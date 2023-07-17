/*
 * Copyright 2023 ConsenSys AG.
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
package tech.pegasys.web3signer.core.service.jsonrpc.handlers.internalresponse;

import static tech.pegasys.web3signer.core.service.jsonrpc.response.JsonRpcError.INVALID_PARAMS;

import tech.pegasys.web3signer.core.service.jsonrpc.EthSendTransactionJsonParameters;
import tech.pegasys.web3signer.core.service.jsonrpc.JsonDecoder;
import tech.pegasys.web3signer.core.service.jsonrpc.JsonRpcRequest;
import tech.pegasys.web3signer.core.service.jsonrpc.exceptions.JsonRpcException;
import tech.pegasys.web3signer.core.service.jsonrpc.handlers.ResultProvider;
import tech.pegasys.web3signer.core.service.jsonrpc.handlers.sendtransaction.transaction.EthTransaction;
import tech.pegasys.web3signer.core.service.jsonrpc.handlers.sendtransaction.transaction.Transaction;
import tech.pegasys.web3signer.core.service.jsonrpc.handlers.signing.TransactionSerializer;
import tech.pegasys.web3signer.signing.ArtifactSignerProvider;

import java.util.List;

import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EthSignTransactionResultProvider implements ResultProvider<String> {

  private static final Logger LOG = LogManager.getLogger();

  private final long chainId;
  private final ArtifactSignerProvider signerProvider;
  private final JsonDecoder decoder;

  public EthSignTransactionResultProvider(
      final long chainId, final ArtifactSignerProvider signerProvider, final JsonDecoder decoder) {
    this.chainId = chainId;
    this.signerProvider = signerProvider;
    this.decoder = decoder;
  }

  @Override
  public String createResponseResult(final JsonRpcRequest request) {
    LOG.debug("Transforming request {}, {}", request.getId(), request.getMethod());
    final EthSendTransactionJsonParameters ethSendTransactionJsonParameters;
    final Transaction transaction;
    try {
      ethSendTransactionJsonParameters =
          fromRpcRequestToJsonParam(EthSendTransactionJsonParameters.class, request);
      transaction =
          new EthTransaction(chainId, ethSendTransactionJsonParameters, null, request.getId());

    } catch (final NumberFormatException e) {
      LOG.debug("Parsing values failed for request: {}", request.getParams(), e);
      throw new JsonRpcException(INVALID_PARAMS);
    } catch (final IllegalArgumentException | DecodeException e) {
      LOG.debug("JSON Deserialization failed for request: {}", request.getParams(), e);
      throw new JsonRpcException(INVALID_PARAMS);
    }

    if (!transaction.isNonceUserSpecified()) {
      LOG.debug("Nonce not present in request {}", request.getId());
      throw new JsonRpcException(INVALID_PARAMS);
    }

    LOG.debug("Obtaining signer for {}", transaction.sender());

    final TransactionSerializer transactionSerializer =
        new TransactionSerializer(signerProvider, chainId);
    return transactionSerializer.serialize(transaction);
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
                + " json Rpc requires one parameter, request contained "
                + paramList.size());
      }
      object = paramList.get(0);
    } else {
      object = params;
    }
    if (object == null) {
      throw new IllegalArgumentException(
          type.getSimpleName()
              + " json Rpc requires a valid parameter, request contained a null object");
    }
    final JsonObject receivedParams = JsonObject.mapFrom(object);
    return decoder.decodeValue(receivedParams.toBuffer(), type);
  }
}
