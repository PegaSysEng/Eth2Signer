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
package tech.pegasys.web3signer.core.service.jsonrpc.handlers.sendtransaction.transaction;

import tech.pegasys.web3signer.core.service.jsonrpc.EeaSendTransactionJsonParameters;
import tech.pegasys.web3signer.core.service.jsonrpc.JsonRpcRequest;
import tech.pegasys.web3signer.core.service.jsonrpc.JsonRpcRequestId;
import tech.pegasys.web3signer.core.service.jsonrpc.handlers.sendtransaction.NonceProvider;

import java.math.BigInteger;
import java.util.List;

import com.google.common.base.MoreObjects;
import org.jetbrains.annotations.NotNull;
import org.web3j.crypto.Sign.SignatureData;
import org.web3j.protocol.eea.crypto.PrivateTransactionEncoder;
import org.web3j.protocol.eea.crypto.RawPrivateTransaction;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpType;

public abstract class PrivateTransaction implements Transaction {

  protected static final String JSON_RPC_METHOD = "eea_sendRawTransaction";
  protected final long chainId;
  protected final JsonRpcRequestId id;
  protected final NonceProvider nonceProvider;
  protected BigInteger nonce;
  protected final EeaSendTransactionJsonParameters transactionJsonParameters;

  PrivateTransaction(
      final long chainId,
      final EeaSendTransactionJsonParameters transactionJsonParameters,
      final NonceProvider nonceProvider,
      final JsonRpcRequestId id) {
    this.chainId = chainId;
    this.transactionJsonParameters = transactionJsonParameters;
    this.nonceProvider = nonceProvider;
    this.id = id;
    this.nonce = transactionJsonParameters.nonce().orElse(null);
  }

  @Override
  public void updateFieldsIfRequired() {
    if (!this.isNonceUserSpecified()) {
      this.nonce = nonceProvider.getNonce();
    }
  }

  @Override
  public byte[] rlpEncode(final SignatureData signatureData) {
    final RawPrivateTransaction rawTransaction = createTransaction();
    final List<RlpType> values =
        PrivateTransactionEncoder.asRlpValues(rawTransaction, signatureData);
    final RlpList rlpList = new RlpList(values);
    return RlpEncoder.encode(rlpList);
  }

  @Override
  public boolean isNonceUserSpecified() {
    return transactionJsonParameters.nonce().isPresent();
  }

  @Override
  public String sender() {
    return transactionJsonParameters.sender();
  }

  @Override
  public JsonRpcRequest jsonRpcRequest(
      final String signedTransactionHexString, final JsonRpcRequestId id) {
    return Transaction.jsonRpcRequest(signedTransactionHexString, id, getJsonRpcMethodName());
  }

  @Override
  @NotNull
  public String getJsonRpcMethodName() {
    return JSON_RPC_METHOD;
  }

  @Override
  public JsonRpcRequestId getId() {
    return id;
  }

  @Override
  public boolean isEip1559() {
    return transactionJsonParameters.maxPriorityFeePerGas().isPresent()
        && transactionJsonParameters.maxFeePerGas().isPresent();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("chainId", chainId)
        .add("transactionJsonParameters", transactionJsonParameters)
        .add("id", id)
        .add("nonceProvider", nonceProvider)
        .add("nonce", nonce)
        .toString();
  }

  protected abstract RawPrivateTransaction createTransaction();
}
