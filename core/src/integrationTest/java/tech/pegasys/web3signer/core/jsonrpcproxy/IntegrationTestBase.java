/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.web3signer.core.jsonrpcproxy;

import static io.restassured.RestAssured.given;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.matchers.Times.exactly;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.web3j.utils.Async.defaultExecutorService;

import tech.pegasys.web3signer.core.Eth1AddressSignerIdentifier;
import tech.pegasys.web3signer.core.Eth1AddressSignerProvider;
import tech.pegasys.web3signer.core.Eth1Runner;
import tech.pegasys.web3signer.core.config.BaseConfig;
import tech.pegasys.web3signer.core.config.Eth1Config;
import tech.pegasys.web3signer.core.jsonrpcproxy.model.HttpMethod;
import tech.pegasys.web3signer.core.jsonrpcproxy.model.request.EthNodeRequest;
import tech.pegasys.web3signer.core.jsonrpcproxy.model.request.EthRequestFactory;
import tech.pegasys.web3signer.core.jsonrpcproxy.model.request.Web3SignerRequest;
import tech.pegasys.web3signer.core.jsonrpcproxy.model.response.EthNodeResponse;
import tech.pegasys.web3signer.core.jsonrpcproxy.model.response.EthResponseFactory;
import tech.pegasys.web3signer.core.jsonrpcproxy.model.response.Web3SignerResponse;
import tech.pegasys.web3signer.core.jsonrpcproxy.support.MetadataFileHelper;
import tech.pegasys.web3signer.core.jsonrpcproxy.support.MockServer;
import tech.pegasys.web3signer.core.jsonrpcproxy.support.RestAssuredConverter;
import tech.pegasys.web3signer.core.jsonrpcproxy.support.TestBaseConfig;
import tech.pegasys.web3signer.core.jsonrpcproxy.support.TestEth1Config;
import tech.pegasys.web3signer.core.service.jsonrpc.handlers.signing.ConfigurationChainId;
import tech.pegasys.web3signer.signing.KeyType;
import tech.pegasys.web3signer.signing.secp256k1.Signer;
import tech.pegasys.web3signer.signing.secp256k1.SingleSignerProvider;
import tech.pegasys.web3signer.signing.secp256k1.filebased.FileBasedSignerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.google.common.io.Resources;
import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.Vertx;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.JsonBody;
import org.mockserver.model.RegexBody;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.WalletUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.JsonRpc2_0Web3j;
import org.web3j.protocol.eea.Eea;
import org.web3j.protocol.eea.JsonRpc2_0Eea;

public class IntegrationTestBase {

  private static final Logger LOG = LogManager.getLogger();
  private static final String PORTS_FILENAME = "web3signer.ports";
  private static final String HTTP_PORT_KEY = "http-port";
  private static final String LOCALHOST = "127.0.0.1";

  private static Vertx vertx;
  private static Eth1Runner runner;
  static ClientAndServer clientAndServer;
  static Credentials credentials;
  private JsonRpc2_0Web3j jsonRpc;
  private JsonRpc2_0Eea eeaJsonRpc;

  protected final EthRequestFactory request = new EthRequestFactory();
  protected final EthResponseFactory response = new EthResponseFactory();

  static String unlockedAccount;
  private static final Duration downstreamTimeout = Duration.ofSeconds(1);

  @TempDir static Path dataPath;
  @TempDir static Path keyConfigPath;

  public static final long DEFAULT_CHAIN_ID = 9;
  public static final int DEFAULT_ID = 77;
  static final String MALFORMED_JSON = "{Bad Json: {{{}";

  static void setupWeb3Signer(final long chainId) throws Exception {
    setupWeb3Signer(chainId, "");
  }

  static void setupWeb3Signer(final long chainId, final String downstreamHttpRequestPath)
      throws Exception {
    setupWeb3Signer(chainId, downstreamHttpRequestPath, List.of("sample.com"));
  }

  static void setupWeb3Signer(
      final long chainId,
      final String downstreamHttpRequestPath,
      final List<String> allowedCorsOrigin)
      throws Exception {
    clientAndServer = startClientAndServer();

    final File keyFile = createKeyFile();
    final File passwordFile = createFile("password");
    credentials = WalletUtils.loadCredentials("password", keyFile);

    final Eth1AddressSignerProvider transactionSignerProvider =
        new Eth1AddressSignerProvider(new SingleSignerProvider(signer(keyFile, passwordFile)));

    createKeyStoreYamlFile(transactionSignerProvider);

    final BaseConfig baseConfig = new TestBaseConfig(dataPath, keyConfigPath, allowedCorsOrigin);
    final Eth1Config eth1Config =
        new TestEth1Config(
            downstreamHttpRequestPath,
            LOCALHOST,
            clientAndServer.getLocalPort(),
            downstreamTimeout,
            new ConfigurationChainId(chainId));
    vertx = Vertx.vertx();
    runner = new Eth1Runner(baseConfig, eth1Config);
    runner.run();

    final Path portsFile = dataPath.resolve(PORTS_FILENAME);
    waitForNonEmptyFileToExist(portsFile);
    final int web3signerPort = httpJsonRpcPort(portsFile);
    RestAssured.port = web3signerPort;

    LOG.info(
        "Started web3signer on port {}, eth stub node on port {}",
        web3signerPort,
        clientAndServer.getLocalPort());

    unlockedAccount =
        transactionSignerProvider.availablePublicKeys().stream()
            .map(Eth1AddressSignerIdentifier::fromPublicKey)
            .map(signerIdentifier -> "0x" + signerIdentifier.toStringIdentifier())
            .findAny()
            .orElseThrow();
  }

  Web3j jsonRpc() {
    return jsonRpc;
  }

  Eea eeaJsonRpc() {
    return eeaJsonRpc;
  }

  @BeforeEach
  public void setup() {
    jsonRpc = new JsonRpc2_0Web3j(null, 2000, defaultExecutorService());
    eeaJsonRpc = new JsonRpc2_0Eea(null);
    if (clientAndServer.isRunning()) {
      clientAndServer.reset();
    }
  }

  @AfterAll
  public static void teardown() {
    clientAndServer.stop();
    vertx.close();
    clientAndServer = null;
    runner = null;
  }

  void setUpEthNodeResponse(final EthNodeRequest request, final EthNodeResponse response) {
    clientAndServer
        .when(request().withBody(json(request.getBody())), exactly(1))
        .respond(
            response()
                .withBody(response.getBody())
                .withHeaders(MockServer.headers(response.getHeaders()))
                .withStatusCode(response.getStatusCode()));
  }

  void setupEthNodeResponse(
      final String bodyRegex, final EthNodeResponse response, final int count) {
    clientAndServer
        .when(request().withBody(new RegexBody(bodyRegex)), exactly(count))
        .respond(
            response()
                .withBody(response.getBody())
                .withHeaders(MockServer.headers(response.getHeaders()))
                .withStatusCode(response.getStatusCode()));
  }

  void timeoutRequest(final String bodyRegex) {
    final int ENSURE_TIMEOUT = 5;
    clientAndServer
        .when(request().withBody(new RegexBody(bodyRegex)))
        .respond(
            response()
                .withDelay(TimeUnit.MILLISECONDS, downstreamTimeout.toMillis() + ENSURE_TIMEOUT));
  }

  void timeoutRequest(final EthNodeRequest request) {
    final int ENSURE_TIMEOUT = 5;
    clientAndServer
        .when(request().withBody(json(request.getBody())), exactly(1))
        .respond(
            response()
                .withDelay(TimeUnit.MILLISECONDS, downstreamTimeout.toMillis() + ENSURE_TIMEOUT));
  }

  void sendPostRequestAndVerifyResponse(
      final Web3SignerRequest request, final Web3SignerResponse expectResponse) {
    sendPostRequestAndVerifyResponse(request, expectResponse, "/");
  }

  void sendPostRequestAndVerifyResponse(
      final Web3SignerRequest request, final Web3SignerResponse expectResponse, final String path) {
    sendPostRequestAndVerifyResponse(request, expectResponse, path, Optional.empty());
  }

  void sendPostRequestAndVerifyResponse(
      final Web3SignerRequest request,
      final Web3SignerResponse expectResponse,
      final String path,
      final Optional<Integer> maybeTimeoutInMilliSec) {

    final RequestSpecification requestSpec =
        given()
            .when()
            .body(request.getBody())
            .headers(RestAssuredConverter.headers(request.getHeaders()));

    maybeTimeoutInMilliSec.ifPresent(
        (timeoutInMilliSec) ->
            requestSpec.config(
                RestAssured.config()
                    .httpClient(
                        HttpClientConfig.httpClientConfig()
                            .setParam(CoreConnectionPNames.CONNECTION_TIMEOUT, timeoutInMilliSec)
                            .setParam(CoreConnectionPNames.SO_TIMEOUT, timeoutInMilliSec))));

    final Response response = requestSpec.post(path);

    verifyResponseMatchesExpected(response, expectResponse);
  }

  void sendPutRequestAndVerifyResponse(
      final Web3SignerRequest request, final Web3SignerResponse expectResponse, final String path) {
    final Response response =
        given()
            .when()
            .body(request.getBody())
            .headers(RestAssuredConverter.headers(request.getHeaders()))
            .put(path);

    verifyResponseMatchesExpected(response, expectResponse);
  }

  void sendGetRequestAndVerifyResponse(
      final Web3SignerRequest request, final Web3SignerResponse expectResponse, final String path) {
    final Response response =
        given()
            .when()
            .body(request.getBody())
            .headers(RestAssuredConverter.headers(request.getHeaders()))
            .get(path);

    verifyResponseMatchesExpected(response, expectResponse);
  }

  void sendDeleteRequestAndVerifyResponse(
      final Web3SignerRequest request, final Web3SignerResponse expectResponse, final String path) {
    final Response response =
        given()
            .when()
            .body(request.getBody())
            .headers(RestAssuredConverter.headers(request.getHeaders()))
            .delete(path);
    verifyResponseMatchesExpected(response, expectResponse);
  }

  void sendRequestAndVerifyResponse(
      final HttpMethod httpMethod,
      final Web3SignerRequest request,
      final Web3SignerResponse expectResponse,
      final String path) {
    final Response response =
        given()
            .when()
            .body(request.getBody())
            .headers(RestAssuredConverter.headers(request.getHeaders()))
            .request(httpMethod.name(), path);

    verifyResponseMatchesExpected(response, expectResponse);
  }

  private void verifyResponseMatchesExpected(
      final Response response, final Web3SignerResponse expectResponse) {
    assertThat(response.statusCode()).isEqualTo(expectResponse.getStatusCode());
    if (expectResponse.getStatusLine().isPresent()) {
      assertThat(response.getStatusLine()).contains(expectResponse.getStatusLine().get());
    }
    assertThat(response.headers())
        .containsAll(RestAssuredConverter.headers(expectResponse.getHeaders()));
    assertThat(response.body().print()).isEqualTo(expectResponse.getBody());
  }

  void verifyEthNodeReceived(final String proxyBodyRequest) {
    clientAndServer.verify(
        request()
            .withBody(JsonBody.json(proxyBodyRequest))
            .withHeaders(MockServer.headers(emptyList())));
  }

  void verifyEthNodeReceived(
      final Iterable<Entry<String, String>> headers, final String proxyBodyRequest) {
    clientAndServer.verify(
        request().withBody(proxyBodyRequest).withHeaders(MockServer.headers(headers)));
  }

  void verifyEthNodeReceived(
      final Iterable<Entry<String, String>> headers,
      final String proxyBodyRequest,
      final String path) {
    clientAndServer.verify(
        request()
            .withPath(path)
            .withBody(JsonBody.json(proxyBodyRequest))
            .withHeaders(MockServer.headers(headers)));
  }

  private static int httpJsonRpcPort(final Path portsFile) {
    try (final FileInputStream fis = new FileInputStream(portsFile.toString())) {
      final Properties portProperties = new Properties();
      portProperties.load(fis);
      final String value = portProperties.getProperty(HTTP_PORT_KEY);
      return Integer.parseInt(value);
    } catch (final IOException e) {
      throw new RuntimeException("Error reading Web3Provider ports file", e);
    }
  }

  private static void waitForNonEmptyFileToExist(final Path path) {
    final File file = path.toFile();
    Awaitility.waitAtMost(30, TimeUnit.SECONDS)
        .until(
            () -> {
              if (file.exists()) {
                try (final Stream<String> s = Files.lines(file.toPath())) {
                  return s.findAny().isPresent();
                }
              }
              return false;
            });
  }

  private static void createKeyStoreYamlFile(Eth1AddressSignerProvider transactionSignerProvider)
      throws IOException, URISyntaxException {
    final MetadataFileHelper METADATA_FILE_HELPERS = new MetadataFileHelper();
    final String keyPath =
        new File(Resources.getResource("keyfile.json").toURI()).getAbsolutePath();

    String unlockedAccountAddress =
        transactionSignerProvider.availablePublicKeys().stream()
            .map(Eth1AddressSignerIdentifier::fromPublicKey)
            .map(signerIdentifier -> "0x" + signerIdentifier.toStringIdentifier())
            .findAny()
            .get();

    METADATA_FILE_HELPERS.createKeyStoreYamlFileAt(
        keyConfigPath.resolve(unlockedAccountAddress + ".yaml"),
        Path.of(keyPath),
        "password",
        KeyType.SECP256K1);
  }

  private static Signer signer(final File keyFile, final File passwordFile) {
    return FileBasedSignerFactory.createSigner(keyFile.toPath(), passwordFile.toPath());
  }

  @SuppressWarnings("UnstableApiUsage")
  private static File createKeyFile() throws IOException {
    final URL walletResource = Resources.getResource("keyfile.json");
    final Path wallet = Files.createTempFile("ethsigner_intg_keyfile", ".json");
    Files.write(wallet, Resources.toString(walletResource, UTF_8).getBytes(UTF_8));
    final File keyFile = wallet.toFile();
    keyFile.deleteOnExit();
    return keyFile;
  }

  private static File createFile(final String s) throws IOException {
    final Path path = Files.createTempFile("file", ".file");
    Files.write(path, s.getBytes(UTF_8));
    final File file = path.toFile();
    file.deleteOnExit();
    return file;
  }
}
