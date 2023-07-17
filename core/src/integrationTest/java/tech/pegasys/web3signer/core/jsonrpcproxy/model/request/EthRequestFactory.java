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
package tech.pegasys.web3signer.core.jsonrpcproxy.model.request;

import static java.util.Collections.emptyList;

import java.util.Map.Entry;

import io.vertx.core.json.Json;
import org.web3j.protocol.core.Request;

public class EthRequestFactory {

  private static final Iterable<Entry<String, String>> NO_HEADERS = emptyList();

  public Web3SignerRequest web3Signer(
      final Iterable<Entry<String, String>> headers, final String body) {
    return new Web3SignerRequest(headers, body);
  }

  public Web3SignerRequest web3Signer(final String body) {
    return new Web3SignerRequest(NO_HEADERS, body);
  }

  public Web3SignerRequest web3Signer(final Request<?, ?> request) {
    return new Web3SignerRequest(NO_HEADERS, Json.encode(request));
  }

  public EthNodeRequest ethNode(final String body) {
    return new EthNodeRequest(NO_HEADERS, body);
  }
}
