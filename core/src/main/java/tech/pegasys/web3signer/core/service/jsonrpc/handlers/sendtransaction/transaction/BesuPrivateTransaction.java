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

import org.web3j.protocol.eea.crypto.RawPrivateTransaction;
import org.web3j.utils.Base64String;
import org.web3j.utils.Restriction;

public class BesuPrivateTransaction extends PrivateTransaction {

  public static BesuPrivateTransaction from(
      final long chainId,
      final EeaSendTransactionJsonParameters transactionJsonParameters,
      final NonceProvider nonceProvider,
      final JsonRpcRequestId id) {

    if (transactionJsonParameters.privacyGroupId().isEmpty()) {
      throw new IllegalArgumentException("Transaction does not contain a valid privacyGroup.");
    }

    final Base64String privacyId = transactionJsonParameters.privacyGroupId().get();
    return new BesuPrivateTransaction(
        chainId, transactionJsonParameters, nonceProvider, id, privacyId);
  }

  private final Base64String privacyGroupId;

  private BesuPrivateTransaction(
      final long chainId,
      final EeaSendTransactionJsonParameters transactionJsonParameters,
      final NonceProvider nonceProvider,
      final JsonRpcRequestId id,
      final Base64String privacyGroupId) {
    super(chainId, transactionJsonParameters, nonceProvider, id);
    this.privacyGroupId = privacyGroupId;
  }

  @Override
  protected RawPrivateTransaction createTransaction() {
    if (transactionJsonParameters.maxPriorityFeePerGas().isPresent()
        && transactionJsonParameters.maxFeePerGas().isPresent()) {
      return RawPrivateTransaction.createTransaction(
          chainId,
          nonce,
          transactionJsonParameters.maxPriorityFeePerGas().orElseThrow(),
          transactionJsonParameters.maxFeePerGas().orElseThrow(),
          transactionJsonParameters.gas().orElse(DEFAULT_GAS),
          transactionJsonParameters.receiver().orElse(DEFAULT_TO),
          transactionJsonParameters.data().orElse(DEFAULT_DATA),
          transactionJsonParameters.privateFrom(),
          privacyGroupId,
          Restriction.fromString(transactionJsonParameters.restriction()));
    } else {
      return RawPrivateTransaction.createTransaction(
          nonce,
          transactionJsonParameters.gasPrice().orElse(DEFAULT_GAS_PRICE),
          transactionJsonParameters.gas().orElse(DEFAULT_GAS),
          transactionJsonParameters.receiver().orElse(DEFAULT_TO),
          transactionJsonParameters.data().orElse(DEFAULT_DATA),
          transactionJsonParameters.privateFrom(),
          privacyGroupId,
          Restriction.fromString(transactionJsonParameters.restriction()));
    }
  }
}
