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
package tech.pegasys.web3signer.core.service.jsonrpc.handlers.sendtransaction.transaction;

import tech.pegasys.web3signer.core.service.VertxRequestTransmitterFactory;
import tech.pegasys.web3signer.core.service.jsonrpc.EeaSendTransactionJsonParameters;
import tech.pegasys.web3signer.core.service.jsonrpc.EthSendTransactionJsonParameters;
import tech.pegasys.web3signer.core.service.jsonrpc.JsonDecoder;
import tech.pegasys.web3signer.core.service.jsonrpc.JsonRpcRequest;
import tech.pegasys.web3signer.core.service.jsonrpc.handlers.sendtransaction.NonceProvider;

import java.util.List;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TransactionFactory {

  private static final Logger LOG = LogManager.getLogger();

  private final VertxRequestTransmitterFactory transmitterFactory;
  private final JsonDecoder decoder;
  private final long chainId;

  public TransactionFactory(
      final long chainId,
      final JsonDecoder decoder,
      final VertxRequestTransmitterFactory transmitterFactory) {
    this.chainId = chainId;
    this.transmitterFactory = transmitterFactory;
    this.decoder = decoder;
  }

  public Transaction createTransaction(final RoutingContext context, final JsonRpcRequest request) {
    final String method = request.getMethod().toLowerCase();
    final VertxNonceRequestTransmitter nonceRequestTransmitter =
        new VertxNonceRequestTransmitter(context.request().headers(), decoder, transmitterFactory);

    switch (method) {
      case "eth_sendtransaction":
        return createEthTransaction(chainId, request, nonceRequestTransmitter);
      case "eea_sendtransaction":
        return createEeaTransaction(chainId, request, nonceRequestTransmitter);
      default:
        throw new IllegalStateException("Unknown send transaction method " + method);
    }
  }

  private Transaction createEthTransaction(
      final long chainId,
      final JsonRpcRequest request,
      final VertxNonceRequestTransmitter nonceRequestTransmitter) {
    final EthSendTransactionJsonParameters params =
        fromRpcRequestToJsonParam(EthSendTransactionJsonParameters.class, request);

    final NonceProvider ethNonceProvider =
        new EthNonceProvider(params.sender(), nonceRequestTransmitter);

    return new EthTransaction(chainId, params, ethNonceProvider, request.getId());
  }

  private Transaction createEeaTransaction(
      final long chainId,
      final JsonRpcRequest request,
      final VertxNonceRequestTransmitter requestTransmitter) {

    final EeaSendTransactionJsonParameters params =
        fromRpcRequestToJsonParam(EeaSendTransactionJsonParameters.class, request);

    if (params.privacyGroupId().isPresent() == params.privateFor().isPresent()) {
      LOG.warn(
          "Illegal private transaction received; privacyGroup (present = {}) and privateFor (present = {}) are mutually exclusive.",
          params.privacyGroupId().isPresent(),
          params.privateFor().isPresent());
      throw new IllegalArgumentException("PrivacyGroup and PrivateFor are mutually exclusive.");
    }

    if (params.privacyGroupId().isPresent()) {
      final NonceProvider nonceProvider =
          new BesuPrivateNonceProvider(
              params.sender(), params.privacyGroupId().get(), requestTransmitter);
      return BesuPrivateTransaction.from(chainId, params, nonceProvider, request.getId());
    }

    final NonceProvider nonceProvider =
        new EeaPrivateNonceProvider(
            params.sender(), params.privateFrom(), params.privateFor().get(), requestTransmitter);
    return EeaPrivateTransaction.from(chainId, params, nonceProvider, request.getId());
  }

  public <T> T fromRpcRequestToJsonParam(final Class<T> type, final JsonRpcRequest request) {

    final Object object;
    final Object params = request.getParams();
    if (params instanceof List) {
      @SuppressWarnings("unchecked")
      final List<Object> paramList = (List<Object>) params;
      if (paramList.size() != 1) {
        throw new IllegalArgumentException(
            type.getSimpleName()
                + " json Rpc requires a single parameter, request contained "
                + paramList.size());
      }
      object = paramList.get(0);
    } else {
      object = params;
    }

    final JsonObject receivedParams = JsonObject.mapFrom(object);

    return decoder.decodeValue(receivedParams.toBuffer(), type);
  }
}
