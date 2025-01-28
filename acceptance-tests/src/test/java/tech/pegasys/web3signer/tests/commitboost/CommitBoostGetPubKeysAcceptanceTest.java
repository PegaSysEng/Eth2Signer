/*
 * Copyright 2024 ConsenSys AG.
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
package tech.pegasys.web3signer.tests.commitboost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;

import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.web3signer.KeystoreUtil;
import tech.pegasys.web3signer.dsl.signer.SignerConfigurationBuilder;
import tech.pegasys.web3signer.dsl.utils.DefaultKeystoresParameters;
import tech.pegasys.web3signer.signing.config.KeystoresParameters;
import tech.pegasys.web3signer.signing.secp256k1.EthPublicKeyUtils;
import tech.pegasys.web3signer.tests.AcceptanceTestBase;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.WalletUtils;

// See https://commit-boost.github.io/commit-boost-client/api/ for Commit Boost spec
public class CommitBoostGetPubKeysAcceptanceTest extends AcceptanceTestBase {
  static final String KEYSTORE_PASSWORD = "password";

  private final List<BLSKeyPair> consensusBlsKeys = randomBLSKeyPairs(2);
  private final Map<String, List<BLSKeyPair>> proxyBLSKeysMap = new HashMap<>();
  private final Map<String, List<ECKeyPair>> proxySECPKeysMap = new HashMap<>();

  @TempDir private Path keystoreDir;
  @TempDir private Path passwordDir;
  // commit boost directories
  @TempDir private Path commitBoostKeystoresPath;
  @TempDir private Path commitBoostPasswordDir;

  @BeforeEach
  void setup() {
    for (final BLSKeyPair blsKeyPair : consensusBlsKeys) {
      // create consensus bls keystore
      KeystoreUtil.createKeystore(blsKeyPair, keystoreDir, passwordDir, KEYSTORE_PASSWORD);

      // create 2 proxy bls
      final List<BLSKeyPair> proxyBLSKeys =
          createProxyBLSKeys(blsKeyPair, 2, commitBoostKeystoresPath);
      proxyBLSKeysMap.put(blsKeyPair.getPublicKey().toHexString(), proxyBLSKeys);

      // create 2 proxy secp keys
      final List<ECKeyPair> proxyECKeyPairs =
          createProxyECKeys(blsKeyPair, 2, commitBoostKeystoresPath);
      proxySECPKeysMap.put(blsKeyPair.getPublicKey().toHexString(), proxyECKeyPairs);
    }

    // commit boost proxy keys password file
    final Path commitBoostPasswordFile = createCommitBoostPasswordFile(commitBoostPasswordDir);

    // start web3signer with keystores and commit boost parameters
    final KeystoresParameters keystoresParameters =
        new DefaultKeystoresParameters(keystoreDir, passwordDir, null);
    final Pair<Path, Path> commitBoostParameters =
        Pair.of(commitBoostKeystoresPath, commitBoostPasswordFile);

    final SignerConfigurationBuilder configBuilder =
        new SignerConfigurationBuilder()
            .withMode("eth2")
            .withNetwork("mainnet")
            .withKeystoresParameters(keystoresParameters)
            .withCommitBoostParameters(commitBoostParameters);

    startSigner(configBuilder.build());
  }

  @Test
  void listCommitBoostPublicKeys() {
    final Response response = signer.callCommitBoostGetPubKeys();
    response
        .then()
        .log()
        .body()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("keys", hasSize(2));

    // extract consensus public keys from response
    final List<Map<String, Object>> responseKeys = response.jsonPath().getList("keys");
    for (final Map<String, Object> responseKeyMap : responseKeys) {
      final String consensusKeyHex = (String) responseKeyMap.get("consensus");
      // verify if consensus public key is present in the map
      assertThat(proxyBLSKeysMap.keySet()).contains(consensusKeyHex);

      // verify if proxy BLS keys are present in the response
      @SuppressWarnings("unchecked")
      final List<String> responseProxyBlsKeys = (List<String>) responseKeyMap.get("proxy_bls");
      final List<String> expectedProxyBLSKeys = getProxyBLSPubKeys(consensusKeyHex);
      assertThat(responseProxyBlsKeys)
          .containsExactlyInAnyOrder(expectedProxyBLSKeys.toArray(String[]::new));

      // verify if proxy SECP keys are present in the response
      @SuppressWarnings("unchecked")
      final List<String> responseProxySECPKeys = (List<String>) responseKeyMap.get("proxy_ecdsa");
      final List<String> expectedProxySECPKeys = getProxyECPubKeys(consensusKeyHex);
      assertThat(responseProxySECPKeys)
          .containsExactlyInAnyOrder(expectedProxySECPKeys.toArray(String[]::new));
    }
  }

  private List<String> getProxyECPubKeys(final String consensusKeyHex) {
    // return compressed secp256k1 public keys in hex format
    return proxySECPKeysMap.get(consensusKeyHex).stream()
        .map(
            ecKeyPair ->
                EthPublicKeyUtils.toHexStringCompressed(
                    EthPublicKeyUtils.web3JPublicKeyToECPublicKey(ecKeyPair.getPublicKey())))
        .toList();
  }

  private List<String> getProxyBLSPubKeys(final String consensusKeyHex) {
    return proxyBLSKeysMap.get(consensusKeyHex).stream()
        .map(blsKeyPair -> blsKeyPair.getPublicKey().toHexString())
        .toList();
  }

  static Path createCommitBoostPasswordFile(final Path commitBoostPasswordDir) {
    try {
      return Files.writeString(
          commitBoostPasswordDir.resolve("cb_password.txt"), KEYSTORE_PASSWORD);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to write password file");
    }
  }

  /**
   * Generate random proxy EC key pairs and their encrypted keystores for given consensus BLS key
   *
   * @param consensusKeyPair consensus BLS key pair whose public key will be used as directory name
   * @param count number of proxy key pairs to generate
   * @param commitBoostKeystoresPath path to store the generated keystores
   * @return list of ECKeyPairs
   */
  static List<ECKeyPair> createProxyECKeys(
      final BLSKeyPair consensusKeyPair, final int count, final Path commitBoostKeystoresPath) {
    final Path proxySecpKeyStoreDir =
        commitBoostKeystoresPath
            .resolve(consensusKeyPair.getPublicKey().toHexString())
            .resolve("SECP256K1");
    try {
      Files.createDirectories(proxySecpKeyStoreDir);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    // create 2 random proxy secp keys and their keystores
    final List<ECKeyPair> proxyECKeyPairs = randomECKeyPairs(count);
    proxyECKeyPairs.forEach(
        proxyECKey -> {
          try {
            WalletUtils.generateWalletFile(
                KEYSTORE_PASSWORD, proxyECKey, proxySecpKeyStoreDir.toFile(), false);
          } catch (final Exception e) {
            throw new IllegalStateException(e);
          }
        });
    return proxyECKeyPairs;
  }

  /**
   * Generate random proxy BLS key pairs and their encrypted keystores for given BLS consensus key
   *
   * @param consensusKeyPair consensus BLS key pair whose public key will be used as directory name
   * @param count number of proxy key pairs to generate
   * @param commitBoostKeystoresPath path to store the generated keystores
   * @return list of BLSKeyPairs
   */
  static List<BLSKeyPair> createProxyBLSKeys(
      final BLSKeyPair consensusKeyPair, final int count, final Path commitBoostKeystoresPath) {
    final Path proxyBlsKeyStoreDir =
        commitBoostKeystoresPath
            .resolve(consensusKeyPair.getPublicKey().toHexString())
            .resolve("BLS");
    try {
      Files.createDirectories(proxyBlsKeyStoreDir);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    // create 2 proxy bls keys and their keystores
    List<BLSKeyPair> blsKeyPairs = randomBLSKeyPairs(count);
    blsKeyPairs.forEach(
        blsKeyPair ->
            KeystoreUtil.createKeystoreFile(blsKeyPair, proxyBlsKeyStoreDir, KEYSTORE_PASSWORD));
    return blsKeyPairs;
  }

  /**
   * Generate random SECP256K1 KeyPairs using Web3J library
   *
   * @param count number of key pairs to generate
   * @return list of ECKeyPairs
   */
  static List<ECKeyPair> randomECKeyPairs(final int count) {
    return Stream.generate(
            () -> {
              try {
                return Keys.createEcKeyPair();
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            })
        .limit(count)
        .toList();
  }

  /**
   * Generate random BLS KeyPairs using Teku library
   *
   * @param count number of key pairs to generate
   * @return list of BLSKeyPairs
   */
  static List<BLSKeyPair> randomBLSKeyPairs(final int count) {
    return Stream.generate(() -> BLSKeyPair.random(SECURE_RANDOM)).limit(count).toList();
  }
}
