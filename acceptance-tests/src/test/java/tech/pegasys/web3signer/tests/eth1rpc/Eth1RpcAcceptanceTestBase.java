/*
 * Copyright 2023 ConsenSys AG.
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
package tech.pegasys.web3signer.tests.eth1rpc;

import tech.pegasys.web3signer.dsl.Account;
import tech.pegasys.web3signer.dsl.besu.BesuNode;
import tech.pegasys.web3signer.dsl.besu.BesuNodeConfig;
import tech.pegasys.web3signer.dsl.besu.BesuNodeConfigBuilder;
import tech.pegasys.web3signer.dsl.besu.BesuNodeFactory;
import tech.pegasys.web3signer.dsl.utils.MetadataFileHelpers;
import tech.pegasys.web3signer.signing.KeyType;
import tech.pegasys.web3signer.tests.AcceptanceTestBase;

import java.io.File;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;

import com.google.common.io.Resources;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.web3j.utils.Convert;

public class Eth1RpcAcceptanceTestBase extends AcceptanceTestBase {
  public static final String RICH_BENEFACTOR = "fe3b557e8fb62b89f4916b721be55ceb828dbd73";
  public static final BigInteger INTRINSIC_GAS = BigInteger.valueOf(21000);
  public static final BigInteger GAS_PRICE =
      Convert.toWei("5", Convert.Unit.SZABO).toBigIntegerExact();

  protected BesuNode besu;
  protected final MetadataFileHelpers metadataFileHelpers = new MetadataFileHelpers();
  protected Path keyFileTempDir;

  protected void startBesu() {
    final BesuNodeConfig besuNodeConfig =
        BesuNodeConfigBuilder.aBesuNodeConfig()
            .withAdditionalCommandLineArgs(List.of("--tx-pool-limit-by-account-percentage=1"))
            .build();

    besu = BesuNodeFactory.create(besuNodeConfig);
    besu.start();
    besu.awaitStartupCompletion();
  }

  @BeforeEach
  public synchronized void generateTempFile(@TempDir Path testDirectory) throws URISyntaxException {
    final String keyPath =
        new File(Resources.getResource("secp256k1/wallet.json").toURI()).getAbsolutePath();

    final Path keyConfigFile = testDirectory.resolve("arbitrary_secp.yaml");

    metadataFileHelpers.createKeyStoreYamlFileAt(
        keyConfigFile, Path.of(keyPath), "pass", KeyType.SECP256K1);

    keyFileTempDir = testDirectory;
  }

  @AfterEach
  public synchronized void shutdownBesu() {
    if (besu != null) {
      besu.shutdown();
      besu = null;
    }
  }

  protected Account richBenefactor() {
    return signer.accounts().richBenefactor();
  }
}
