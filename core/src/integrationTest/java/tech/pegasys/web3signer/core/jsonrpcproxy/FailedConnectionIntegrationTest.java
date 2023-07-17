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

import tech.pegasys.web3signer.core.jsonrpcproxy.model.jsonrpc.EthProtocolVersionRequest;
import tech.pegasys.web3signer.core.service.jsonrpc.response.JsonRpcError;
import tech.pegasys.web3signer.core.service.jsonrpc.response.JsonRpcErrorResponse;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.json.Json;
import org.junit.jupiter.api.Test;

class FailedConnectionIntegrationTest extends DefaultTestBase {

  @Test
  void failsToConnectToDownStreamRaisesTimeout() {
    clientAndServer.stop();
    final EthProtocolVersionRequest request = new EthProtocolVersionRequest(jsonRpc());

    final String expectedResponse =
        Json.encode(
            new JsonRpcErrorResponse(
                request.getId(), JsonRpcError.FAILED_TO_CONNECT_TO_DOWNSTREAM_NODE));

    sendPostRequestAndVerifyResponse(
        this.request.web3Signer(request.getEncodedRequestBody()),
        response.web3Signer(expectedResponse, HttpResponseStatus.GATEWAY_TIMEOUT));
  }
}
