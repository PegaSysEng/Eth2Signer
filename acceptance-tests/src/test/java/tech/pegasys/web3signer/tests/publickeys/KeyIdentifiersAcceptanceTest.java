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
package tech.pegasys.web3signer.tests.publickeys;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.everyItem;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.collection.IsIn.in;
import static tech.pegasys.web3signer.core.config.HealthCheckNames.KEYS_CHECK_CONFIG_FILE_LOADING;
import static tech.pegasys.web3signer.dsl.utils.HealthCheckResultUtil.getHealtcheckKeysLoaded;
import static tech.pegasys.web3signer.dsl.utils.HealthCheckResultUtil.getHealthcheckErrorCount;
import static tech.pegasys.web3signer.dsl.utils.HealthCheckResultUtil.getHealthcheckStatusValue;
import static tech.pegasys.web3signer.signing.KeyType.BLS;
import static tech.pegasys.web3signer.signing.KeyType.SECP256K1;

import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSecretKey;
import tech.pegasys.web3signer.signing.KeyType;

import java.nio.file.Path;
import java.util.List;

import io.restassured.response.Response;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.tuweni.bytes.Bytes32;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class KeyIdentifiersAcceptanceTest extends KeyIdentifiersAcceptanceTestBase {

  @ParameterizedTest
  @EnumSource(value = KeyType.class)
  public void noLoadedKeysReturnsEmptyPublicKeyResponse(final KeyType keyType) {
    initAndStartSigner(calculateMode(keyType));
    validateApiResponse(signer.callApiPublicKeys(keyType), empty());
  }

  @ParameterizedTest
  @EnumSource(value = KeyType.class)
  public void invalidKeysReturnsEmptyPublicKeyResponse(final KeyType keyType) {
    createKeys(keyType, false, privateKeys(keyType));
    initAndStartSigner(calculateMode(keyType));

    validateApiResponse(signer.callApiPublicKeys(keyType), empty());
  }

  @ParameterizedTest
  @EnumSource(value = KeyType.class)
  public void onlyValidKeysAreReturnedInPublicKeyResponse(final KeyType keyType) {
    final String[] prvKeys = privateKeys(keyType);
    final String[] keys = createKeys(keyType, true, prvKeys[0]);
    final String[] invalidKeys = createKeys(keyType, false, prvKeys[1]);

    initAndStartSigner(calculateMode(keyType));

    final Response response = signer.callApiPublicKeys(keyType);
    validateApiResponse(response, contains(keys));
    validateApiResponse(response, everyItem(not(in(invalidKeys))));
  }

  @Test
  public void healthCheckShowsErrorCountForInvalidKeysinEth2Mode() {
    final KeyType keyType = BLS;
    final String[] prvKeys = privateKeys(keyType);
    final String[] keys = createKeys(keyType, true, prvKeys[0]);
    final String[] invalidKeys = createKeys(keyType, false, prvKeys[1]);

    initAndStartSigner(calculateMode(keyType));

    final String jsonBody = signer.healthcheck().body().asString();
    int keysLoaded = getHealtcheckKeysLoaded(jsonBody, KEYS_CHECK_CONFIG_FILE_LOADING);
    int errorCount = getHealthcheckErrorCount(jsonBody, KEYS_CHECK_CONFIG_FILE_LOADING);
    assertThat(getHealthcheckStatusValue(jsonBody)).isEqualTo("DOWN");
    assertThat(keysLoaded).isEqualTo(keys.length);
    assertThat(errorCount).isEqualTo(invalidKeys.length);
  }

  @ParameterizedTest
  @EnumSource(value = KeyType.class)
  public void additionalPublicKeyAreReportedAfterReload(final KeyType keyType) {
    final String[] prvKeys = privateKeys(keyType);
    final String[] keys = createKeys(keyType, true, prvKeys[0]);

    initAndStartSigner(calculateMode(keyType));

    final Response response = signer.callApiPublicKeys(keyType);
    validateApiResponse(response, contains(keys));

    final String[] additionalKeys = createKeys(keyType, true, prvKeys[1]);
    signer.callReload().then().statusCode(200);

    // reload is async ...
    Awaitility.await()
        .atMost(5, SECONDS)
        .until(
            () -> signer.callApiPublicKeys(keyType).jsonPath().<List<String>>get("."),
            containsInAnyOrder(ArrayUtils.addAll(keys, additionalKeys)));
  }

  @Test
  public void healthCheckReportsKeysLoadedAfterReloadInEth2Mode() {
    final KeyType keyType = BLS;
    final String[] prvKeys = privateKeys(keyType);
    final String[] keys = createKeys(keyType, true, prvKeys[0]);

    initAndStartSigner(calculateMode(keyType));

    final String jsonBody = signer.healthcheck().body().asString();
    int keysLoaded = getHealtcheckKeysLoaded(jsonBody, KEYS_CHECK_CONFIG_FILE_LOADING);
    assertThat(keysLoaded).isEqualTo(keys.length);
    assertThat(getHealthcheckStatusValue(jsonBody)).isEqualTo("UP");

    final String[] additionalKeys = createKeys(keyType, true, prvKeys[1]);
    signer.callReload().then().statusCode(200);

    // reload is async ...
    Awaitility.await()
        .atMost(5, SECONDS)
        .until(
            () -> signer.callApiPublicKeys(keyType).jsonPath().<List<String>>get("."),
            containsInAnyOrder(ArrayUtils.addAll(keys, additionalKeys)));

    // only new keys loaded (from config-files-loading) should show up in healthcheck.
    keysLoaded = getHealtcheckKeysLoaded(jsonBody, KEYS_CHECK_CONFIG_FILE_LOADING);
    assertThat(keysLoaded).isEqualTo(1);
    assertThat(getHealthcheckStatusValue(jsonBody)).isEqualTo("UP");
  }

  @ParameterizedTest
  @EnumSource(value = KeyType.class)
  public void publicKeysAreRemovedAfterReloadDefault(final KeyType keyType) {
    final String[] prvKeys = privateKeys(keyType);
    final String[] keys = createKeys(keyType, true, prvKeys);

    initAndStartSigner(calculateMode(keyType));

    validateApiResponse(signer.callApiPublicKeys(keyType), containsInAnyOrder(keys));

    // remove one of the key config file
    assertThat(testDirectory.resolve(keys[1] + ".yaml").toFile().delete()).isTrue();

    // reload API call
    signer.callReload().then().statusCode(200);

    // reload is async ... assert that the key is removed
    Awaitility.await()
        .atMost(5, SECONDS)
        .untilAsserted(
            () -> {
              final List<String> publicKeysList =
                  signer.callApiPublicKeys(keyType).jsonPath().getList(".");
              assertThat(publicKeysList).containsOnly(keys[0]);
            });
  }

  @ParameterizedTest
  @EnumSource(value = KeyType.class)
  public void allLoadedKeysAreReturnedInPublicKeyResponse(final KeyType keyType) {
    final String[] keys = createKeys(keyType, true, privateKeys(keyType));
    initAndStartSigner(calculateMode(keyType));

    validateApiResponse(signer.callApiPublicKeys(keyType), containsInAnyOrder(keys));
  }

  @Test
  public void allLoadedKeysAreReportedInHealthCheckInEth2Mode() {
    final KeyType keyType = BLS;
    final String[] keys = createKeys(keyType, true, privateKeys(keyType));
    initAndStartSigner(calculateMode(keyType));

    final String jsonBody = signer.healthcheck().body().asString();
    int keysLoaded = getHealtcheckKeysLoaded(jsonBody, KEYS_CHECK_CONFIG_FILE_LOADING);
    assertThat(keysLoaded).isEqualTo(keys.length);
    assertThat(getHealthcheckStatusValue(jsonBody)).isEqualTo("UP");
  }

  @ParameterizedTest
  @EnumSource(value = KeyType.class)
  public void allLoadedKeysAreReturnedPublicKeyResponseWithEmptyAccept(final KeyType keyType) {
    final String[] keys = createKeys(keyType, true, privateKeys(keyType));
    initAndStartSigner(calculateMode(keyType));

    final Response response = callApiPublicKeysWithoutOpenApiClientSideFilter(keyType);
    validateApiResponse(response, containsInAnyOrder(keys));
  }

  @Test
  @EnabledIfEnvironmentVariables({
    @EnabledIfEnvironmentVariable(named = "AZURE_CLIENT_ID", matches = ".*"),
    @EnabledIfEnvironmentVariable(named = "AZURE_CLIENT_SECRET", matches = ".*"),
    @EnabledIfEnvironmentVariable(named = "AZURE_KEY_VAULT_NAME", matches = ".*"),
    @EnabledIfEnvironmentVariable(named = "AZURE_TENANT_ID", matches = ".*")
  })
  public void azureKeysReturnAppropriatePublicKey() {
    final String clientId = System.getenv("AZURE_CLIENT_ID");
    final String clientSecret = System.getenv("AZURE_CLIENT_SECRET");
    final String keyVaultName = System.getenv("AZURE_KEY_VAULT_NAME");
    final String tenantId = System.getenv("AZURE_TENANT_ID");
    final String publicKeyHexString =
        "0xa95663509e608da3c2af5a48eb4315321f8430cbed5518a44590cc9d367f01dc72ebbc583fc7d94f9fdc20eb6e162c9f8cb35be8a91a3b1d32a63ecc10be4e08";

    METADATA_FILE_HELPERS.createAzureKeyYamlFileAt(
        testDirectory.resolve(publicKeyHexString + ".yaml"),
        clientId,
        clientSecret,
        keyVaultName,
        tenantId);
    initAndStartSigner("eth1");
    final Response response = callApiPublicKeysWithoutOpenApiClientSideFilter(SECP256K1);
    validateApiResponse(response, containsInAnyOrder(publicKeyHexString));
  }

  @Test
  public void ensureSystemCanLoadAndReportTenThousandKeysWithinExistingTimeLimits() {
    final int keyCount = 10000;
    final String[] publicKeys = new String[keyCount];
    for (int i = 0; i < keyCount; i++) {
      final Bytes32 bytes = Bytes32.fromHexString(String.format("%064X", i + 1));
      final BLSSecretKey key = BLSSecretKey.fromBytes(bytes);
      final BLSKeyPair keyPair = new BLSKeyPair(key);
      final BLSPublicKey publicKey = keyPair.getPublicKey();
      final String configFilename = publicKey.toString().substring(2);
      publicKeys[i] = publicKey.toString();
      final Path keyConfigFile = testDirectory.resolve(configFilename + ".yaml");
      METADATA_FILE_HELPERS.createUnencryptedYamlFileAt(
          keyConfigFile, bytes.toUnprefixedHexString(), BLS);
    }

    initAndStartSigner("eth2");
    validateApiResponse(signer.callApiPublicKeys(BLS), containsInAnyOrder(publicKeys));
  }

  @ParameterizedTest
  @EnumSource(value = KeyType.class)
  public void keysWithArbitraryFilenamesAreLoaded(final KeyType keyType) {
    final String privateKey = privateKeys(keyType)[0];
    final String filename = "foo" + "_" + keyType + ".yaml";
    METADATA_FILE_HELPERS.createUnencryptedYamlFileAt(
        testDirectory.resolve(filename), privateKey, keyType);
    initAndStartSigner(calculateMode(keyType));

    final String publicKey = keyType == BLS ? BLS_PUBLIC_KEY_1 : SECP_PUBLIC_KEY_1;
    validateApiResponse(signer.callApiPublicKeys(keyType), contains(publicKey));
  }
}
