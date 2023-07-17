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
import static tech.pegasys.web3signer.dsl.utils.ExceptionUtils.failOnIOException;

import tech.pegasys.web3signer.core.service.jsonrpc.response.JsonRpcErrorResponse;
import tech.pegasys.web3signer.dsl.signer.SignerResponse;

import java.io.IOException;
import java.util.Optional;

import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

public class PublicContracts extends Contracts<Transaction> {

  private final Eth eth;

  public PublicContracts(final Eth eth) {
    this.eth = eth;
  }

  @Override
  public String sendTransaction(final Transaction smartContract) throws IOException {
    return eth.sendTransaction(smartContract);
  }

  @Override
  public SignerResponse<JsonRpcErrorResponse> sendTransactionExpectsError(
      final Transaction smartContract) throws IOException {
    return eth.sendTransactionExpectsError(smartContract);
  }

  @Override
  public Optional<TransactionReceipt> getTransactionReceipt(final String hash) throws IOException {
    return eth.getTransactionReceipt(hash);
  }

  public String code(final String address) {
    return failOnIOException(
        () -> {
          final String code = eth.getCode(address);
          assertThat(code).isNotEmpty();
          return code;
        });
  }

  public String call(final Transaction contractViewOperation) {
    return failOnIOException(() -> eth.call(contractViewOperation));
  }
}
