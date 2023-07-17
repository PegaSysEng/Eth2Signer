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
package tech.pegasys.web3signer.core.jsonrpcproxy.model.jsonrpc;

import static org.web3j.utils.Numeric.decodeQuantity;
import static tech.pegasys.web3signer.core.jsonrpcproxy.IntegrationTestBase.DEFAULT_CHAIN_ID;

import java.math.BigInteger;
import java.util.List;

import com.google.common.io.BaseEncoding;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.EthSendTransaction;

public class SendRawTransaction {

  private final Web3j jsonRpc;
  private final Credentials credentials;

  public SendRawTransaction(final Web3j jsonRpc, final Credentials credentials) {
    this.jsonRpc = jsonRpc;
    this.credentials = credentials;
  }

  public String request() {
    final Request<?, ? extends Response<?>> sendRawTransactionRequest =
        jsonRpc.ethSendRawTransaction(
            "0xf8b2a0e04d296d2460cfb8472af2c5fd05b5a214109c25688d3704aed5484f9a7792f28609184e72a0008276c094d46e8dd67c5d32be8058bb8eb970870f07244567849184e72aa9d46e8dd67c5d32be8d46e8dd67c5d32be8058bb8eb970870f072445675058bb8eb970870f07244567535a0f04e0e7b41adea417596550611138a3ec9a452abb6648d734107c53476e76a27a05b826d9e9b4e0dd0e7b8939c102a2079d71cfc27cd6b7bebe5a006d5ad17d780");
    sendRawTransactionRequest.setId(77);

    return Json.encode(sendRawTransactionRequest);
  }

  @SuppressWarnings("unchecked")
  public String request(final Request<?, EthSendTransaction> request, final long chainId) {
    final List<JsonObject> params = (List<JsonObject>) request.getParams();
    if (params.size() != 1) {
      throw new IllegalStateException("sendTransaction request must have only 1 parameter");
    }
    final JsonObject transaction = params.get(0);
    final RawTransaction rawTransaction =
        RawTransaction.createTransaction(
            valueToBigDecimal(transaction.getString("nonce")),
            valueToBigDecimal(transaction.getString("gasPrice")),
            valueToBigDecimal(transaction.getString("gas")),
            transaction.getString("to"),
            valueToBigDecimal(transaction.getString("value")),
            transaction.getString("data"));
    final byte[] signedTransaction =
        TransactionEncoder.signMessage(rawTransaction, chainId, credentials);
    final String value = "0x" + BaseEncoding.base16().encode(signedTransaction).toLowerCase();
    return request(value);
  }

  public String request(final Request<?, EthSendTransaction> request) {
    return request(request, DEFAULT_CHAIN_ID);
  }

  private BigInteger valueToBigDecimal(final String value) {
    return value == null ? null : decodeQuantity(value);
  }

  public String request(final String value) {
    final Request<?, ? extends Response<?>> sendRawTransactionRequest =
        jsonRpc.ethSendRawTransaction(value);
    sendRawTransactionRequest.setId(77);

    return Json.encode(sendRawTransactionRequest);
  }

  public String response(final String value) {
    final Response<String> sendRawTransactionResponse = new EthSendTransaction();
    sendRawTransactionResponse.setResult(value);
    return Json.encode(sendRawTransactionResponse);
  }
}
