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
package tech.pegasys.web3signer.dsl.signer;

import tech.pegasys.web3signer.core.service.jsonrpc.response.JsonRpcError;
import tech.pegasys.web3signer.core.service.jsonrpc.response.JsonRpcErrorResponse;
import tech.pegasys.web3signer.core.service.jsonrpc.response.JsonRpcResponse;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.json.Json;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.exceptions.ClientConnectionException;

public class SignerResponse<T extends JsonRpcResponse> {

  private final T rpcResponse;
  private final HttpResponseStatus status;

  public SignerResponse(final T rpcResponse, final HttpResponseStatus status) {
    this.rpcResponse = rpcResponse;
    this.status = status;
  }

  public T jsonRpc() {
    return rpcResponse;
  }

  public HttpResponseStatus status() {
    return status;
  }

  public static SignerResponse<JsonRpcErrorResponse> fromError(final ClientConnectionException e) {
    final String message = e.getMessage();
    final String errorBody = message.substring(message.indexOf(":") + 1).trim();
    final String[] errorParts = errorBody.split(";", 2);
    if (errorParts.length == 2) {
      final String statusCode = errorParts[0];
      final HttpResponseStatus status = HttpResponseStatus.valueOf(Integer.parseInt(statusCode));
      final String jsonBody = errorParts[1];
      JsonRpcErrorResponse jsonRpcResponse = null;
      if (!jsonBody.isEmpty()) {
        jsonRpcResponse = Json.decodeValue(jsonBody, JsonRpcErrorResponse.class);
      }
      return new SignerResponse<>(jsonRpcResponse, status);
    } else {
      throw new RuntimeException("Unable to parse web3j exception message", e);
    }
  }

  public static SignerResponse<JsonRpcErrorResponse> fromWeb3jErrorResponse(
      final Response<?> response) {
    if (response != null && response.hasError()) {
      final Response.Error error = response.getError();
      final JsonRpcError jsonRpcError = JsonRpcError.fromJson(error.getCode(), error.getMessage());
      final JsonRpcErrorResponse jsonRpcErrorResponse =
          new JsonRpcErrorResponse(response.getId(), jsonRpcError);
      return new SignerResponse<>(jsonRpcErrorResponse, HttpResponseStatus.OK);
    }

    return null;
  }
}
