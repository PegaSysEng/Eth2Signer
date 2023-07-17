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
import static tech.pegasys.web3signer.core.jsonrpcproxy.model.jsonrpc.SendTransaction.FIELD_DATA;
import static tech.pegasys.web3signer.core.jsonrpcproxy.model.jsonrpc.SendTransaction.FIELD_FROM;
import static tech.pegasys.web3signer.core.jsonrpcproxy.model.jsonrpc.SendTransaction.FIELD_GAS;
import static tech.pegasys.web3signer.core.jsonrpcproxy.model.jsonrpc.SendTransaction.FIELD_GAS_PRICE;
import static tech.pegasys.web3signer.core.jsonrpcproxy.model.jsonrpc.SendTransaction.FIELD_NONCE;
import static tech.pegasys.web3signer.core.jsonrpcproxy.model.jsonrpc.SendTransaction.FIELD_TO;
import static tech.pegasys.web3signer.core.jsonrpcproxy.model.jsonrpc.SendTransaction.FIELD_VALUE;
import static tech.pegasys.web3signer.core.jsonrpcproxy.model.jsonrpc.TransactionJsonUtil.putValue;

import io.vertx.core.json.JsonObject;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthSendTransaction;

public class EeaSendTransaction {
  public static final String FIELD_PRIVATE_FROM = "privateFrom";
  public static final String FIELD_PRIVATE_FOR = "privateFor";
  public static final String FIELD_RESTRICTION = "restriction";
  public static final String FIELD_PRIVACY_GROUP_ID = "privacyGroupId";
  public static final String UNLOCKED_ACCOUNT = "0x7577919ae5df4941180eac211965f275cdce314d";
  public static final String PRIVATE_FROM = "ZlapEsl9qDLPy/e88+/6yvCUEVIvH83y0N4A6wHuKXI=";
  public static final String PRIVATE_FOR = "GV8m0VZAccYGAAYMBuYQtKEj0XtpXeaw2APcoBmtA2w=";
  public static final String PRIVACY_GROUP_ID = "/xzRjCLioUBkm5LYuzll61GXyrD5x7bvXzQk/ovJA/4=";

  public static final String DEFAULT_VALUE = "0x0";

  /**
   * Due to the underlying server mocking, When only a single request is used, the contents does not
   * actually matter, only their equivalence does.
   */
  public Request<Object, EthSendTransaction> request(final PrivateTransaction privateTransaction) {
    final JsonObject jsonObject = new JsonObject();
    putValue(jsonObject, FIELD_FROM, privateTransaction.getFrom());
    putValue(jsonObject, FIELD_NONCE, privateTransaction.getNonce());
    putValue(jsonObject, FIELD_GAS_PRICE, privateTransaction.getGasPrice());
    putValue(jsonObject, FIELD_GAS, privateTransaction.getGas());
    putValue(jsonObject, FIELD_TO, privateTransaction.getTo());
    putValue(jsonObject, FIELD_VALUE, privateTransaction.getValue());
    putValue(jsonObject, FIELD_DATA, privateTransaction.getData());
    putValue(jsonObject, FIELD_PRIVATE_FROM, privateTransaction.getPrivateFrom());
    putValue(jsonObject, FIELD_PRIVATE_FOR, privateTransaction.getPrivateFor());
    putValue(jsonObject, FIELD_PRIVACY_GROUP_ID, privateTransaction.getPrivacyGroupId());
    putValue(jsonObject, FIELD_RESTRICTION, privateTransaction.getRestriction());
    return createRequest(jsonObject);
  }

  public Request<Object, EthSendTransaction> request(
      final PrivateTransaction.Builder privateTransactionBuilder) {
    return request(privateTransactionBuilder.build());
  }

  private Request<Object, EthSendTransaction> createRequest(final JsonObject transaction) {
    final Request<Object, EthSendTransaction> eea_sendTransaction =
        new Request<>(
            "eea_sendTransaction", singletonList(transaction), null, EthSendTransaction.class);
    eea_sendTransaction.setId(DEFAULT_ID);
    return eea_sendTransaction;
  }
}
