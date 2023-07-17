/*
 * Copyright 2020 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.web3signer.core;

import static tech.pegasys.web3signer.signing.KeyType.SECP256K1;

import tech.pegasys.web3signer.core.config.BaseConfig;
import tech.pegasys.web3signer.core.config.Eth1Config;
import tech.pegasys.web3signer.core.service.DownstreamPathCalculator;
import tech.pegasys.web3signer.core.service.VertxRequestTransmitter;
import tech.pegasys.web3signer.core.service.VertxRequestTransmitterFactory;
import tech.pegasys.web3signer.core.service.http.handlers.LogErrorHandler;
import tech.pegasys.web3signer.core.service.http.handlers.signing.Eth1SignForIdentifierHandler;
import tech.pegasys.web3signer.core.service.http.handlers.signing.SignerForIdentifier;
import tech.pegasys.web3signer.core.service.http.metrics.HttpApiMetrics;
import tech.pegasys.web3signer.core.service.jsonrpc.JsonDecoder;
import tech.pegasys.web3signer.core.service.jsonrpc.handlers.Eth1AccountsHandler;
import tech.pegasys.web3signer.core.service.jsonrpc.handlers.HttpResponseFactory;
import tech.pegasys.web3signer.core.service.jsonrpc.handlers.JsonRpcErrorHandler;
import tech.pegasys.web3signer.core.service.jsonrpc.handlers.JsonRpcHandler;
import tech.pegasys.web3signer.core.service.jsonrpc.handlers.PassThroughHandler;
import tech.pegasys.web3signer.core.service.jsonrpc.handlers.RequestMapper;
import tech.pegasys.web3signer.core.service.jsonrpc.handlers.internalresponse.EthSignResultProvider;
import tech.pegasys.web3signer.core.service.jsonrpc.handlers.internalresponse.EthSignTransactionResultProvider;
import tech.pegasys.web3signer.core.service.jsonrpc.handlers.internalresponse.InternalResponseHandler;
import tech.pegasys.web3signer.core.service.jsonrpc.handlers.sendtransaction.SendTransactionHandler;
import tech.pegasys.web3signer.core.service.jsonrpc.handlers.sendtransaction.transaction.TransactionFactory;
import tech.pegasys.web3signer.keystorage.hashicorp.HashicorpConnectionFactory;
import tech.pegasys.web3signer.signing.ArtifactSignerProvider;
import tech.pegasys.web3signer.signing.EthSecpArtifactSigner;
import tech.pegasys.web3signer.signing.SecpArtifactSignature;
import tech.pegasys.web3signer.signing.config.DefaultArtifactSignerProvider;
import tech.pegasys.web3signer.signing.config.SignerLoader;
import tech.pegasys.web3signer.signing.config.metadata.Secp256k1ArtifactSignerFactory;
import tech.pegasys.web3signer.signing.config.metadata.interlock.InterlockKeyProvider;
import tech.pegasys.web3signer.signing.config.metadata.parser.YamlMapperFactory;
import tech.pegasys.web3signer.signing.config.metadata.parser.YamlSignerParser;
import tech.pegasys.web3signer.signing.config.metadata.yubihsm.YubiHsmOpaqueDataProvider;
import tech.pegasys.web3signer.signing.secp256k1.azure.AzureKeyVaultSignerFactory;

import java.util.List;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.ResponseContentTypeHandler;
import io.vertx.ext.web.impl.BlockingHandlerDecorator;
import org.hyperledger.besu.plugin.services.MetricsSystem;

public class Eth1Runner extends Runner {
  public static final String PUBLIC_KEYS_PATH = "/api/v1/eth1/publicKeys";
  public static final String ROOT_PATH = "/";
  public static final String SIGN_PATH = "/api/v1/eth1/sign/:identifier";
  private final Eth1Config eth1Config;
  private final long chainId;

  private final HttpResponseFactory responseFactory = new HttpResponseFactory();

  public Eth1Runner(final BaseConfig baseConfig, final Eth1Config eth1Config) {
    super(baseConfig);
    this.eth1Config = eth1Config;
    this.chainId = eth1Config.getChainId().id();
  }

  @Override
  protected void populateRouter(final Context context) {
    final Router router = context.getRouter();
    final LogErrorHandler errorHandler = context.getErrorHandler();
    final ArtifactSignerProvider signerProvider = context.getArtifactSignerProvider();

    addPublicKeysListHandler(router, signerProvider, PUBLIC_KEYS_PATH, context.getErrorHandler());

    final SignerForIdentifier<SecpArtifactSignature> secpSigner =
        new SignerForIdentifier<>(signerProvider, this::formatSecpSignature, SECP256K1);
    router
        .route(HttpMethod.POST, SIGN_PATH)
        .handler(
            new BlockingHandlerDecorator(
                new Eth1SignForIdentifierHandler(
                    secpSigner,
                    new HttpApiMetrics(context.getMetricsSystem(), SECP256K1, signerProvider)),
                false))
        .failureHandler(errorHandler);

    addReloadHandler(router, signerProvider, context.getErrorHandler());

    final DownstreamPathCalculator downstreamPathCalculator =
        new DownstreamPathCalculator(eth1Config.getDownstreamHttpPath());

    final WebClientOptions webClientOptions =
        new WebClientOptionsFactory().createWebClientOptions(eth1Config);
    final HttpClient downStreamConnection = context.getVertx().createHttpClient(webClientOptions);

    final VertxRequestTransmitterFactory transmitterFactory =
        responseBodyHandler ->
            new VertxRequestTransmitter(
                context.getVertx(),
                downStreamConnection,
                eth1Config.getDownstreamHttpRequestTimeout(),
                downstreamPathCalculator,
                responseBodyHandler);

    final JsonDecoder jsonDecoder = createJsonDecoder();
    final PassThroughHandler passThroughHandler =
        new PassThroughHandler(transmitterFactory, jsonDecoder);

    final RequestMapper requestMapper =
        createRequestMapper(transmitterFactory, signerProvider, jsonDecoder, secpSigner);

    router
        .route(HttpMethod.POST, ROOT_PATH)
        .produces(Runner.JSON)
        .handler(ResponseContentTypeHandler.create())
        .handler(BodyHandler.create())
        .failureHandler(new JsonRpcErrorHandler(new HttpResponseFactory()))
        .blockingHandler(new JsonRpcHandler(responseFactory, requestMapper, jsonDecoder), false);

    router.route().handler(BodyHandler.create()).handler(passThroughHandler);
  }

  @Override
  protected ArtifactSignerProvider createArtifactSignerProvider(
      final Vertx vertx, final MetricsSystem metricsSystem) {
    return new DefaultArtifactSignerProvider(
        () -> {
          final AzureKeyVaultSignerFactory azureFactory = new AzureKeyVaultSignerFactory();
          final HashicorpConnectionFactory hashicorpConnectionFactory =
              new HashicorpConnectionFactory();
          try (final InterlockKeyProvider interlockKeyProvider = new InterlockKeyProvider(vertx);
              final YubiHsmOpaqueDataProvider yubiHsmOpaqueDataProvider =
                  new YubiHsmOpaqueDataProvider()) {
            final Secp256k1ArtifactSignerFactory ethSecpArtifactSignerFactory =
                new Secp256k1ArtifactSignerFactory(
                    hashicorpConnectionFactory,
                    baseConfig.getKeyConfigPath(),
                    azureFactory,
                    interlockKeyProvider,
                    yubiHsmOpaqueDataProvider,
                    EthSecpArtifactSigner::new,
                    true);

            return new SignerLoader(baseConfig.keystoreParallelProcessingEnabled())
                .load(
                    baseConfig.getKeyConfigPath(),
                    "yaml",
                    new YamlSignerParser(
                        List.of(ethSecpArtifactSignerFactory),
                        YamlMapperFactory.createYamlMapper(
                            baseConfig.getKeyStoreConfigFileMaxSize())))
                .getValues();
          }
        });
  }

  private String formatSecpSignature(final SecpArtifactSignature signature) {
    return SecpArtifactSignature.toBytes(signature).toHexString();
  }

  public static JsonDecoder createJsonDecoder() {
    // Force Transaction Deserialization to fail if missing expected properties
    final ObjectMapper jsonObjectMapper = new ObjectMapper();
    jsonObjectMapper.configure(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES, true);
    jsonObjectMapper.configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, true);

    return new JsonDecoder(jsonObjectMapper);
  }

  private RequestMapper createRequestMapper(
      final VertxRequestTransmitterFactory transmitterFactory,
      final ArtifactSignerProvider signerProvider,
      final JsonDecoder jsonDecoder,
      final SignerForIdentifier<SecpArtifactSignature> secpSigner) {
    final PassThroughHandler defaultHandler =
        new PassThroughHandler(transmitterFactory, jsonDecoder);

    final TransactionFactory transactionFactory =
        new TransactionFactory(chainId, jsonDecoder, transmitterFactory);
    final SendTransactionHandler sendTransactionHandler =
        new SendTransactionHandler(chainId, signerProvider, transactionFactory, transmitterFactory);

    final RequestMapper requestMapper = new RequestMapper(defaultHandler);
    requestMapper.addHandler(
        "eth_accounts",
        new InternalResponseHandler<>(
            responseFactory, new Eth1AccountsHandler(signerProvider::availableIdentifiers)));
    requestMapper.addHandler(
        "eth_sign",
        new InternalResponseHandler<>(responseFactory, new EthSignResultProvider(secpSigner)));
    requestMapper.addHandler(
        "eth_signTransaction",
        new InternalResponseHandler<>(
            responseFactory,
            new EthSignTransactionResultProvider(chainId, signerProvider, jsonDecoder)));
    requestMapper.addHandler("eth_sendTransaction", sendTransactionHandler);
    requestMapper.addHandler("eea_sendTransaction", sendTransactionHandler);

    return requestMapper;
  }
}
