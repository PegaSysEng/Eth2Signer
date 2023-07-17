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
package tech.pegasys.web3signer.core.service.jsonrpc.handlers.sendtransaction;

import static tech.pegasys.web3signer.core.service.jsonrpc.response.JsonRpcError.ETH_SEND_TX_ALREADY_KNOWN;
import static tech.pegasys.web3signer.core.service.jsonrpc.response.JsonRpcError.ETH_SEND_TX_REPLACEMENT_UNDERPRICED;
import static tech.pegasys.web3signer.core.service.jsonrpc.response.JsonRpcError.NONCE_TOO_LOW;

import tech.pegasys.web3signer.core.service.jsonrpc.response.JsonRpcErrorResponse;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NonceTooLowRetryMechanism extends RetryMechanism {

  private static final Logger LOG = LogManager.getLogger();

  public NonceTooLowRetryMechanism(final int maxRetries) {
    super(maxRetries);
  }

  @Override
  public boolean responseRequiresRetry(final int httpStatusCode, final String body) {
    if ((httpStatusCode == HttpResponseStatus.OK.code())) {
      final JsonRpcErrorResponse errorResponse;
      try {
        errorResponse = specialiseResponse(body);
      } catch (final IllegalArgumentException | DecodeException e) {
        return false;
      }
      if (NONCE_TOO_LOW.equals(errorResponse.getError())) {
        LOG.info("Nonce too low, resend required for {}.", errorResponse.getId());
        return true;
      } else if (ETH_SEND_TX_ALREADY_KNOWN.equals(errorResponse.getError())
          || ETH_SEND_TX_REPLACEMENT_UNDERPRICED.equals(errorResponse.getError())) {
        LOG.info(
            "Besu returned \"{}\", which means that a Tx with the same nonce is in the transaction pool, resend required for {}.",
            errorResponse.getError(),
            errorResponse.getId());
        return true;
      }
    }
    return false;
  }

  private JsonRpcErrorResponse specialiseResponse(final String errorResposneBody) {
    final JsonObject jsonBody = new JsonObject(errorResposneBody);
    return jsonBody.mapTo(JsonRpcErrorResponse.class);
  }
}
