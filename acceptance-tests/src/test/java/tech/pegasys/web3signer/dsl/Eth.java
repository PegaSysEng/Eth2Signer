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
package tech.pegasys.web3signer.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.web3signer.core.service.jsonrpc.response.JsonRpcErrorResponse;
import tech.pegasys.web3signer.dsl.signer.SignerResponse;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

public class Eth {

  private final Web3j jsonRpc;

  public Eth(final Web3j jsonRpc) {
    this.jsonRpc = jsonRpc;
  }

  public String sendTransaction(final Transaction transaction) throws IOException {
    final EthSendTransaction response = jsonRpc.ethSendTransaction(transaction).send();

    assertThat(response.getTransactionHash()).isNotEmpty();
    assertThat(response.getError()).isNull();

    return response.getTransactionHash();
  }

  public SignerResponse<JsonRpcErrorResponse> sendTransactionExpectsError(
      final Transaction transaction) throws IOException {
    final EthSendTransaction response = jsonRpc.ethSendTransaction(transaction).send();
    assertThat(response.hasError()).isTrue();
    return SignerResponse.fromWeb3jErrorResponse(response);
  }

  public BigInteger getTransactionCount(final String address) throws IOException {
    return jsonRpc
        .ethGetTransactionCount(address, DefaultBlockParameterName.LATEST)
        .send()
        .getTransactionCount();
  }

  public Optional<TransactionReceipt> getTransactionReceipt(final String hash) throws IOException {
    return jsonRpc.ethGetTransactionReceipt(hash).send().getTransactionReceipt();
  }

  public List<String> getAccounts() throws IOException {
    return jsonRpc.ethAccounts().send().getAccounts();
  }

  public String getCode(final String address) throws IOException {
    return jsonRpc.ethGetCode(address, DefaultBlockParameterName.LATEST).send().getResult();
  }

  public BigInteger getBalance(final String account) throws IOException {
    return jsonRpc.ethGetBalance(account, DefaultBlockParameterName.LATEST).send().getBalance();
  }

  public String call(final Transaction contractViewOperation) throws IOException {
    return jsonRpc
        .ethCall(contractViewOperation, DefaultBlockParameterName.LATEST)
        .send()
        .getValue();
  }
}
