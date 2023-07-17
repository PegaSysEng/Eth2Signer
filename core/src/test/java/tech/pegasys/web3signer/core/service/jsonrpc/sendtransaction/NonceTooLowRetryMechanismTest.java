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
package tech.pegasys.web3signer.core.service.jsonrpc.sendtransaction;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import tech.pegasys.web3signer.core.service.jsonrpc.handlers.sendtransaction.NonceTooLowRetryMechanism;
import tech.pegasys.web3signer.core.service.jsonrpc.handlers.sendtransaction.RetryMechanism;
import tech.pegasys.web3signer.core.service.jsonrpc.response.JsonRpcError;
import tech.pegasys.web3signer.core.service.jsonrpc.response.JsonRpcErrorResponse;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class NonceTooLowRetryMechanismTest {

  private final RetryMechanism retryMechanism = new NonceTooLowRetryMechanism(2);

  @Test
  public void retryIsRequiredIfErrorIsNonceTooLow() {

    final JsonRpcErrorResponse errorResponse = new JsonRpcErrorResponse(JsonRpcError.NONCE_TOO_LOW);

    assertThat(
            retryMechanism.responseRequiresRetry(
                HttpResponseStatus.OK.code(), Json.encode(errorResponse)))
        .isTrue();
  }

  @Test
  public void retryIsRequiredIfErrorIsKnownTransaction() {

    final JsonRpcErrorResponse errorResponse =
        new JsonRpcErrorResponse(JsonRpcError.ETH_SEND_TX_ALREADY_KNOWN);

    assertThat(
            retryMechanism.responseRequiresRetry(
                HttpResponseStatus.OK.code(), Json.encode(errorResponse)))
        .isTrue();
  }

  @Test
  public void retryIsRequiredIfErrorIsReplacementTransactionUnderpriced() {
    final JsonRpcErrorResponse errorResponse =
        new JsonRpcErrorResponse(JsonRpcError.ETH_SEND_TX_REPLACEMENT_UNDERPRICED);

    assertThat(
            retryMechanism.responseRequiresRetry(
                HttpResponseStatus.OK.code(), Json.encode(errorResponse)))
        .isTrue();
  }

  @Test
  public void retryIsNotRequiredIfErrorIsNotNonceTooLow() {
    final JsonRpcErrorResponse errorResponse =
        new JsonRpcErrorResponse(JsonRpcError.INVALID_PARAMS);

    assertThat(
            retryMechanism.responseRequiresRetry(
                HttpResponseStatus.BAD_REQUEST.code(), Json.encode(errorResponse)))
        .isFalse();
  }

  @Test
  public void retryIsNotRequiredForUnknownErrorType() {
    final JsonObject errorResponse = new JsonObject();
    final JsonObject error = new JsonObject();
    error.put("code", -9000);
    error.put("message", "Unknown error");
    errorResponse.put("jsonrpc", "2.0");
    errorResponse.put("id", 1);
    errorResponse.put("error", new JsonObject());

    assertThat(
            retryMechanism.responseRequiresRetry(
                HttpResponseStatus.BAD_REQUEST.code(), Json.encode(errorResponse)))
        .isFalse();
  }

  @Test
  public void testRetryReportsFalseOnceMatchingMaxValue() {
    assertThat(retryMechanism.retriesAvailable()).isTrue();
    retryMechanism.incrementRetries(); // retried once
    assertThat(retryMechanism.retriesAvailable()).isTrue();
    retryMechanism.incrementRetries(); // retried twice
    assertThat(retryMechanism.retriesAvailable()).isFalse();
    retryMechanism.incrementRetries();
    assertThat(retryMechanism.retriesAvailable()).isFalse();
  }
}
