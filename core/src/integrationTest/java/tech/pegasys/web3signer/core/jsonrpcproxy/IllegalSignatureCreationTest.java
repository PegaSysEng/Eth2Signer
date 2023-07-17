/*
 * Copyright 2020 ConsenSys AG.
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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import tech.pegasys.web3signer.core.service.jsonrpc.EthSendTransactionJsonParameters;
import tech.pegasys.web3signer.core.service.jsonrpc.handlers.sendtransaction.transaction.EthTransaction;
import tech.pegasys.web3signer.signing.secp256k1.Signature;
import tech.pegasys.web3signer.signing.secp256k1.filebased.CredentialSigner;

import org.junit.jupiter.api.Test;
import org.web3j.crypto.Credentials;

class IllegalSignatureCreationTest {

  @Test
  void ensureSignaturesCreatedHavePositiveValues() {
    // This problem was identified in Github Issue #247, which identified a specific
    // transaction being signed with a given key, resulted in the transaction being rejected by
    // Besu due to "INVALID SIGNATURE" - ultimately, it was came down to byte[] --> BigInt resulting
    // in negative value.
    final EthSendTransactionJsonParameters txnParams =
        new EthSendTransactionJsonParameters("0xf17f52151ebef6c7334fad080c5704d77216b732");
    txnParams.gasPrice("0x0");
    txnParams.gas("0x7600");
    txnParams.nonce("0x46");
    txnParams.value("0x1");
    txnParams.data("0x0");
    txnParams.receiver("0x627306090abaB3A6e1400e9345bC60c78a8BEf57");

    final EthTransaction txn = new EthTransaction(1337L, txnParams, null, null);
    final byte[] serialisedBytes = txn.rlpEncode(null);

    final CredentialSigner signer =
        new CredentialSigner(
            Credentials.create("ae6ae8e5ccbfb04590405997ee2d52d2b330726137b875053c36d94e974d162f"));

    final Signature signature = signer.sign(serialisedBytes);

    assertThat(signature.getR().signum()).isOne();
    assertThat(signature.getS().signum()).isOne();
  }
}
