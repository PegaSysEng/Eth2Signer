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
package tech.pegasys.web3signer.tests.eth1rpc.signing;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static tech.pegasys.web3signer.core.service.jsonrpc.response.JsonRpcError.REPLAY_PROTECTED_SIGNATURES_NOT_SUPPORTED;
import static tech.pegasys.web3signer.core.service.jsonrpc.response.JsonRpcError.WRONG_CHAIN_ID;

import tech.pegasys.web3signer.core.service.jsonrpc.handlers.signing.ConfigurationChainId;
import tech.pegasys.web3signer.core.service.jsonrpc.response.JsonRpcErrorResponse;
import tech.pegasys.web3signer.dsl.besu.BesuNodeConfig;
import tech.pegasys.web3signer.dsl.besu.BesuNodeConfigBuilder;
import tech.pegasys.web3signer.dsl.besu.BesuNodeFactory;
import tech.pegasys.web3signer.dsl.signer.SignerConfiguration;
import tech.pegasys.web3signer.dsl.signer.SignerConfigurationBuilder;
import tech.pegasys.web3signer.dsl.signer.SignerResponse;
import tech.pegasys.web3signer.tests.eth1rpc.Eth1RpcAcceptanceTestBase;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.utils.Convert;
import org.web3j.utils.Convert.Unit;

public class ReplayProtectionAcceptanceTest extends Eth1RpcAcceptanceTestBase {

  private static final String RECIPIENT = "0x1b00ba00ca00bb00aa00bc00be00ac00ca00da00";
  private static final BigInteger TRANSFER_AMOUNT_WEI =
      Convert.toWei("1.75", Unit.ETHER).toBigIntegerExact();

  private void setUp(final String genesis) {
    final BesuNodeConfig besuNodeConfig =
        BesuNodeConfigBuilder.aBesuNodeConfig().withGenesisFile(genesis).build();

    besu = BesuNodeFactory.create(besuNodeConfig);
    besu.start();
    besu.awaitStartupCompletion();

    final SignerConfiguration web3SignerConfiguration =
        new SignerConfigurationBuilder()
            .withKeyStoreDirectory(keyFileTempDir)
            .withMode("eth1")
            .withDownstreamHttpPort(besu.ports().getHttpRpc())
            .withChainIdProvider(new ConfigurationChainId(2018))
            .build();

    startSigner(web3SignerConfiguration);
  }

  @Test
  public void wrongChainId() {
    setUp("besu/eth_hash_4404.json");

    final SignerResponse<JsonRpcErrorResponse> signerResponse =
        signer
            .transactions()
            .submitExceptional(
                Transaction.createEtherTransaction(
                    richBenefactor().address(),
                    richBenefactor().nextNonceAndIncrement(),
                    GAS_PRICE,
                    INTRINSIC_GAS,
                    RECIPIENT,
                    TRANSFER_AMOUNT_WEI));

    assertThat(signerResponse.status()).isEqualTo(OK);
    assertThat(signerResponse.jsonRpc().getError()).isEqualTo(WRONG_CHAIN_ID);
  }

  @Test
  public void unnecessaryChainId() {
    setUp("besu/eth_hash_2018_no_replay_protection.json");

    final SignerResponse<JsonRpcErrorResponse> signerResponse =
        signer
            .transactions()
            .submitExceptional(
                Transaction.createEtherTransaction(
                    richBenefactor().address(),
                    richBenefactor().nextNonceAndIncrement(),
                    GAS_PRICE,
                    INTRINSIC_GAS,
                    RECIPIENT,
                    TRANSFER_AMOUNT_WEI));

    assertThat(signerResponse.status()).isEqualTo(OK);
    assertThat(signerResponse.jsonRpc().getError())
        .isEqualTo(REPLAY_PROTECTED_SIGNATURES_NOT_SUPPORTED);
  }
}
