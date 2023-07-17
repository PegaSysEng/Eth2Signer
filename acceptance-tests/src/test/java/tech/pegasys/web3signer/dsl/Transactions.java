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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static tech.pegasys.web3signer.dsl.utils.ExceptionUtils.failOnIOException;
import static tech.pegasys.web3signer.dsl.utils.WaitUtils.waitFor;

import tech.pegasys.web3signer.core.service.jsonrpc.response.JsonRpcErrorResponse;
import tech.pegasys.web3signer.dsl.signer.SignerResponse;

import java.io.IOException;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.awaitility.core.ConditionTimeoutException;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.exceptions.ClientConnectionException;

public class Transactions {

  private static final Logger LOG = LogManager.getLogger();

  private final Eth eth;

  public Transactions(final Eth eth) {
    this.eth = eth;
  }

  public String submit(final Transaction transaction) {
    return failOnIOException(() -> eth.sendTransaction(transaction));
  }

  public SignerResponse<JsonRpcErrorResponse> submitExceptional(final Transaction transaction) {
    try {
      return failOnIOException(() -> eth.sendTransactionExpectsError(transaction));
    } catch (final ClientConnectionException e) {
      LOG.info("ClientConnectionException with message: " + e.getMessage());
      return SignerResponse.fromError(e);
    }
  }

  public void awaitBlockContaining(final String hash) {
    try {
      waitFor(() -> assertThat(eth.getTransactionReceipt(hash).isPresent()).isTrue());
    } catch (final ConditionTimeoutException e) {
      LOG.error("Timed out waiting for a block containing the transaction receipt hash: " + hash);
      throw new RuntimeException("No receipt found for hash: " + hash);
    }
  }

  public Optional<TransactionReceipt> getTransactionReceipt(final String hash) {
    try {
      return eth.getTransactionReceipt(hash);
    } catch (IOException e) {
      LOG.error("IOException with message: " + e.getMessage());
      throw new RuntimeException("No tx receipt found for hash: " + hash);
    }
  }
}
