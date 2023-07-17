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

import static java.util.Collections.singletonList;
import static tech.pegasys.web3signer.core.jsonrpcproxy.IntegrationTestBase.DEFAULT_ID;
import static tech.pegasys.web3signer.core.jsonrpcproxy.model.jsonrpc.TransactionJsonUtil.putValue;

import io.vertx.core.json.JsonObject;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthSendTransaction;

public class SendTransaction {
  public static final String FIELD_VALUE_DEFAULT = "0x0";
  public static final String FIELD_GAS_DEFAULT = "0x15F90";
  public static final String FIELD_GAS_PRICE_DEFAULT = "0x0";
  public static final String FIELD_DATA_DEFAULT = "";
  public static final String FIELD_FROM = "from";
  public static final String FIELD_NONCE = "nonce";
  public static final String FIELD_TO = "to";
  public static final String FIELD_VALUE = "value";
  public static final String FIELD_GAS = "gas";
  public static final String FIELD_GAS_PRICE = "gasPrice";
  public static final String FIELD_DATA = "data";

  /**
   * Due to the underlying server mocking, When only a single request is used, the contents does not
   * actually matter, only their equivalence does.
   */
  public Request<?, EthSendTransaction> request(final Transaction transaction) {
    final JsonObject jsonObject = new JsonObject();
    putValue(jsonObject, FIELD_FROM, transaction.getFrom());
    putValue(jsonObject, FIELD_NONCE, transaction.getNonce());
    putValue(jsonObject, FIELD_GAS_PRICE, transaction.getGasPrice());
    putValue(jsonObject, FIELD_GAS, transaction.getGas());
    putValue(jsonObject, FIELD_TO, transaction.getTo());
    putValue(jsonObject, FIELD_VALUE, transaction.getValue());
    putValue(jsonObject, FIELD_DATA, transaction.getData());
    return createRequest(jsonObject);
  }

  public Request<?, EthSendTransaction> request(final Transaction.Builder transactionBuilder) {
    return request(transactionBuilder.build());
  }

  private Request<?, EthSendTransaction> createRequest(final JsonObject transaction) {
    final Request<Object, EthSendTransaction> eea_sendTransaction =
        new Request<>(
            "eth_sendTransaction", singletonList(transaction), null, EthSendTransaction.class);
    eea_sendTransaction.setId(DEFAULT_ID);
    return eea_sendTransaction;
  }
}
