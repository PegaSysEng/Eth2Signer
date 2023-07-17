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
import java.util.stream.Collectors;

import com.google.common.io.BaseEncoding;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.eea.Eea;
import org.web3j.protocol.eea.crypto.PrivateTransactionEncoder;
import org.web3j.protocol.eea.crypto.RawPrivateTransaction;
import org.web3j.utils.Base64String;
import org.web3j.utils.Restriction;

public class EeaSendRawTransaction {

  private final Eea eeaJsonRpc;
  private final Credentials credentials;

  public EeaSendRawTransaction(final Eea eeaJsonRpc, final Credentials credentials) {
    this.eeaJsonRpc = eeaJsonRpc;
    this.credentials = credentials;
  }

  public String request() {
    final Request<?, ? extends Response<?>> sendRawTransactionRequest =
        eeaJsonRpc.eeaSendRawTransaction(
            "0xf90110a0e04d296d2460cfb8472af2c5fd05b5a214109c25688d3704aed5484f9a7792f28609184e72a0008276c094d46e8dd67c5d32be8058bb8eb970870f0724456780a9d46e8dd67c5d32be8d46e8dd67c5d32be8058bb8eb970870f072445675058bb8eb970870f07244567536a00b528cefb87342b2097318cd493d13b4c9bd55bf35bf1b3cf2ef96ee14cee563a06423107befab5530c42a2d7d2590b96c04ee361c868c138b054d7886966121a6aa307837353737393139616535646634393431313830656163323131393635663237356364636533313464ebaa3078643436653864643637633564333262653830353862623865623937303837306630373234343536378a72657374726963746564");
    sendRawTransactionRequest.setId(77);
    return Json.encode(sendRawTransactionRequest);
  }

  @SuppressWarnings("unchecked")
  public String request(final Request<?, EthSendTransaction> request, final long chainId) {
    final List<JsonObject> params = (List<JsonObject>) request.getParams();
    if (params.size() != 1) {
      throw new IllegalStateException("eeaSendTransaction request must have only 1 parameter");
    }
    final JsonObject transaction = params.get(0);
    final String privacyGroupId = transaction.getString("privacyGroupId");
    final RawPrivateTransaction rawTransaction =
        privacyGroupId == null
            ? createEeaRawPrivateTransaction(transaction)
            : createBesuRawPrivateTransaction(transaction, privacyGroupId);

    final byte[] signedTransaction =
        PrivateTransactionEncoder.signMessage(rawTransaction, chainId, credentials);
    final String value = "0x" + BaseEncoding.base16().encode(signedTransaction).toLowerCase();
    return request(value);
  }

  private List<Base64String> privateFor(final JsonArray transaction) {
    return transaction.stream()
        .map(String.class::cast)
        .map(this::valueToBase64String)
        .collect(Collectors.toList());
  }

  public String request(final Request<?, EthSendTransaction> request) {
    return request(request, DEFAULT_CHAIN_ID);
  }

  private BigInteger valueToBigDecimal(final String value) {
    return value == null ? null : decodeQuantity(value);
  }

  private Base64String valueToBase64String(final String value) {
    return value == null ? null : Base64String.wrap(value);
  }

  private RawPrivateTransaction createBesuRawPrivateTransaction(
      final JsonObject transaction, final String privacyGroupId) {
    return RawPrivateTransaction.createTransaction(
        valueToBigDecimal(transaction.getString("nonce")),
        valueToBigDecimal(transaction.getString("gasPrice")),
        valueToBigDecimal(transaction.getString("gas")),
        transaction.getString("to"),
        transaction.getString("data"),
        valueToBase64String(transaction.getString("privateFrom")),
        valueToBase64String(privacyGroupId),
        Restriction.fromString(transaction.getString("restriction")));
  }

  private RawPrivateTransaction createEeaRawPrivateTransaction(final JsonObject transaction) {
    return RawPrivateTransaction.createTransaction(
        valueToBigDecimal(transaction.getString("nonce")),
        valueToBigDecimal(transaction.getString("gasPrice")),
        valueToBigDecimal(transaction.getString("gas")),
        transaction.getString("to"),
        transaction.getString("data"),
        valueToBase64String(transaction.getString("privateFrom")),
        privateFor(transaction.getJsonArray("privateFor")),
        Restriction.fromString(transaction.getString("restriction")));
  }

  public String request(final String value) {
    final Request<?, ? extends Response<?>> sendRawTransactionRequest =
        eeaJsonRpc.eeaSendRawTransaction(value);
    sendRawTransactionRequest.setId(77);

    return Json.encode(sendRawTransactionRequest);
  }

  public String response(final String value) {
    final Response<String> sendRawTransactionResponse = new EthSendTransaction();
    sendRawTransactionResponse.setResult(value);
    return Json.encode(sendRawTransactionResponse);
  }
}
