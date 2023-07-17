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
import static tech.pegasys.web3signer.dsl.utils.WaitUtils.waitFor;

import tech.pegasys.web3signer.core.service.jsonrpc.response.JsonRpcErrorResponse;
import tech.pegasys.web3signer.dsl.signer.SignerResponse;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.awaitility.core.ConditionTimeoutException;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

public abstract class Contracts<T> {

  private static final Logger LOG = LogManager.getLogger();

  public static final BigInteger GAS_PRICE = BigInteger.valueOf(1000);
  public static final BigInteger GAS_LIMIT = BigInteger.valueOf(3000000);

  public abstract String sendTransaction(T smartContract) throws IOException;

  public abstract SignerResponse<JsonRpcErrorResponse> sendTransactionExpectsError(T smartContract)
      throws IOException;

  public abstract Optional<? extends TransactionReceipt> getTransactionReceipt(final String hash)
      throws IOException;

  public String submit(final T smartContract) {
    return failOnIOException(() -> sendTransaction(smartContract));
  }

  public void awaitBlockContaining(final String hash) {
    try {
      waitFor(() -> assertThat(getTransactionReceipt(hash).isPresent()).isTrue());
    } catch (final ConditionTimeoutException e) {
      LOG.error("Timed out waiting for a block containing the transaction receipt hash: " + hash);
    }
  }

  public String address(final String hash) {
    return failOnIOException(
        () -> {
          final TransactionReceipt receipt =
              getTransactionReceipt(hash)
                  .orElseThrow(() -> new RuntimeException("No receipt found for hash: " + hash));
          assertThat(receipt.getContractAddress()).isNotEmpty();
          return receipt.getContractAddress();
        });
  }
}
