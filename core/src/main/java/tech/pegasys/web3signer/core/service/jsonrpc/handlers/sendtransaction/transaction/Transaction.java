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
package tech.pegasys.web3signer.core.service.jsonrpc.handlers.sendtransaction.transaction;

import static java.util.Collections.singletonList;
import static tech.pegasys.web3signer.core.service.jsonrpc.RpcUtil.JSON_RPC_VERSION;

import tech.pegasys.web3signer.core.service.jsonrpc.JsonRpcRequest;
import tech.pegasys.web3signer.core.service.jsonrpc.JsonRpcRequestId;

import java.math.BigInteger;
import java.nio.ByteBuffer;

import org.web3j.crypto.Sign.SignatureData;

public interface Transaction {
  BigInteger DEFAULT_GAS_PRICE = BigInteger.ZERO;
  BigInteger DEFAULT_GAS = BigInteger.valueOf(90000);
  BigInteger DEFAULT_VALUE = BigInteger.ZERO;
  String DEFAULT_DATA = "";
  String DEFAULT_TO = "";

  void updateFieldsIfRequired();

  byte[] rlpEncode(SignatureData signatureData);

  boolean isNonceUserSpecified();

  String sender();

  JsonRpcRequest jsonRpcRequest(String signedTransactionHexString, JsonRpcRequestId id);

  // NOTE: This was taken from Web3j TransactionEncoder as the function is private
  static byte[] longToBytes(final long x) {
    final ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
    buffer.putLong(x);
    return buffer.array();
  }

  String getJsonRpcMethodName();

  static JsonRpcRequest jsonRpcRequest(
      final String signedTransactionHexString, final JsonRpcRequestId id, final String rpcMethod) {
    final JsonRpcRequest transaction = new JsonRpcRequest(JSON_RPC_VERSION, rpcMethod);
    transaction.setParams(singletonList(signedTransactionHexString));
    transaction.setId(id);
    return transaction;
  }

  JsonRpcRequestId getId();

  boolean isEip1559();
}
