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
package tech.pegasys.web3signer.core.jsonrpcproxy;

import static java.math.BigInteger.ONE;
import static tech.pegasys.web3signer.core.jsonrpcproxy.support.TransactionCountResponder.TRANSACTION_COUNT_METHOD.ETH_GET_TRANSACTION_COUNT;

import tech.pegasys.web3signer.core.jsonrpcproxy.model.jsonrpc.SendRawTransaction;
import tech.pegasys.web3signer.core.jsonrpcproxy.model.jsonrpc.SendTransaction;
import tech.pegasys.web3signer.core.jsonrpcproxy.model.jsonrpc.Transaction;
import tech.pegasys.web3signer.core.jsonrpcproxy.support.TransactionCountResponder;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthSendTransaction;

class SigningEthSendTransactionWithChainIdIntegrationTest extends IntegrationTestBase {

  private SendTransaction sendTransaction;
  private SendRawTransaction sendRawTransaction;

  @SuppressWarnings("unused")
  @BeforeAll
  private static void setupWeb3Signer() throws Exception {
    setupWeb3Signer(4123123123L);
  }

  @BeforeEach
  void setUp() {
    sendTransaction = new SendTransaction();
    sendRawTransaction = new SendRawTransaction(jsonRpc(), credentials);
    final TransactionCountResponder getTransactionResponse =
        new TransactionCountResponder(nonce -> nonce.add(ONE), ETH_GET_TRANSACTION_COUNT);
    clientAndServer.when(getTransactionResponse.request()).respond(getTransactionResponse);
  }

  @Test
  void signSendTransactionWhenContractWithLongChainId() {
    final Request<?, EthSendTransaction> sendTransactionRequest =
        sendTransaction.request(Transaction.smartContract());
    final String sendRawTransactionRequest =
        sendRawTransaction.request(
            sendTransaction.request(Transaction.smartContract()), 4123123123L);
    final String sendRawTransactionResponse =
        sendRawTransaction.response(
            "0xe670ec64341771606e55d6b4ca35a1a6b75ee3d5145a99d0592102688888888");
    setUpEthNodeResponse(
        request.ethNode(sendRawTransactionRequest), response.ethNode(sendRawTransactionResponse));

    sendPostRequestAndVerifyResponse(
        request.web3Signer(sendTransactionRequest),
        response.web3Signer(sendRawTransactionResponse));

    verifyEthNodeReceived(sendRawTransactionRequest);
  }
}
