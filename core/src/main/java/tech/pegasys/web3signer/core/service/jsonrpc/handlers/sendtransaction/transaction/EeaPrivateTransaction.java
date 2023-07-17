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
import tech.pegasys.web3signer.core.service.jsonrpc.JsonRpcRequestId;
import tech.pegasys.web3signer.core.service.jsonrpc.handlers.sendtransaction.NonceProvider;

import java.util.List;

import com.google.common.base.MoreObjects;
import org.web3j.protocol.eea.crypto.RawPrivateTransaction;
import org.web3j.utils.Base64String;
import org.web3j.utils.Restriction;

public class EeaPrivateTransaction extends PrivateTransaction {

  private final List<Base64String> privateFor;

  public static EeaPrivateTransaction from(
      final long chainId,
      final EeaSendTransactionJsonParameters transactionJsonParameters,
      final NonceProvider nonceProvider,
      final JsonRpcRequestId id) {
    if (transactionJsonParameters.privateFor().isEmpty()) {
      throw new IllegalArgumentException("Transaction does not contain a valid privateFor list.");
    }

    return new EeaPrivateTransaction(
        chainId,
        transactionJsonParameters,
        nonceProvider,
        id,
        transactionJsonParameters.privateFor().get());
  }

  private EeaPrivateTransaction(
      final long chainId,
      final EeaSendTransactionJsonParameters transactionJsonParameters,
      final NonceProvider nonceProvider,
      final JsonRpcRequestId id,
      final List<Base64String> privateFor) {
    super(chainId, transactionJsonParameters, nonceProvider, id);
    this.privateFor = privateFor;
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

  @Override
  protected RawPrivateTransaction createTransaction() {
    if (isEip1559()) {
      return RawPrivateTransaction.createTransaction(
          chainId,
          nonce,
          transactionJsonParameters.maxPriorityFeePerGas().orElseThrow(),
          transactionJsonParameters.maxFeePerGas().orElseThrow(),
          transactionJsonParameters.gas().orElse(DEFAULT_GAS),
          transactionJsonParameters.receiver().orElse(DEFAULT_TO),
          transactionJsonParameters.data().orElse(DEFAULT_DATA),
          transactionJsonParameters.privateFrom(),
          privateFor,
          Restriction.fromString(transactionJsonParameters.restriction()));
    } else {
      return RawPrivateTransaction.createTransaction(
          nonce,
          transactionJsonParameters.gasPrice().orElse(DEFAULT_GAS_PRICE),
          transactionJsonParameters.gas().orElse(DEFAULT_GAS),
          transactionJsonParameters.receiver().orElse(DEFAULT_TO),
          transactionJsonParameters.data().orElse(DEFAULT_DATA),
          transactionJsonParameters.privateFrom(),
          privateFor,
          Restriction.fromString(transactionJsonParameters.restriction()));
    }
  }
}
