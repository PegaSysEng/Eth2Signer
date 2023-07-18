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
package tech.pegasys.web3signer.tests.bulkloading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;

import tech.pegasys.web3signer.dsl.signer.SignerConfigurationBuilder;
import tech.pegasys.web3signer.dsl.utils.DefaultAzureKeyVaultParameters;
import tech.pegasys.web3signer.signing.KeyType;
import tech.pegasys.web3signer.signing.config.AzureKeyVaultParameters;
import tech.pegasys.web3signer.tests.AcceptanceTestBase;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

public class AzureKeyVaultAcceptanceTest extends AcceptanceTestBase {

  private static final String CLIENT_ID = System.getenv("AZURE_CLIENT_ID");
  private static final String CLIENT_SECRET = System.getenv("AZURE_CLIENT_SECRET");
  private static final String TENANT_ID = System.getenv("AZURE_TENANT_ID");
  private static final String VAULT_NAME = System.getenv("AZURE_KEY_VAULT_NAME");
  private static final String[] BLS_EXPECTED_KEYS =
      List.of(
              "0x989d34725a2bfc3f15105f3f5fc8741f436c25ee1ee4f948e425d6bcb8c56bce6e06c269635b7e985a7ffa639e2409bf")
          .toArray(new String[0]);

  private static final String[] SECP_EXPECTED_KEYS =
      List.of(
              "0xa95663509e608da3c2af5a48eb4315321f8430cbed5518a44590cc9d367f01dc72ebbc583fc7d94f9fdc20eb6e162c9f8cb35be8a91a3b1d32a63ecc10be4e08")
          .toArray(new String[0]);

  @BeforeAll
  public static void setup() {
    Assumptions.assumeTrue(CLIENT_ID != null, "Set AZURE_CLIENT_ID environment variable");
    Assumptions.assumeTrue(CLIENT_SECRET != null, "Set AZURE_CLIENT_SECRET environment variable");
    Assumptions.assumeTrue(TENANT_ID != null, "Set AZURE_TENANT_ID environment variable");
    Assumptions.assumeTrue(VAULT_NAME != null, "Set AZURE_KEY_VAULT_NAME environment variable");
  }

  @ParameterizedTest
  @EnumSource(KeyType.class)
  void ensureSecretsInKeyVaultAreLoadedAndReportedViaPublicKeysApi(final KeyType keyType) {
    final AzureKeyVaultParameters azureParams =
        new DefaultAzureKeyVaultParameters(VAULT_NAME, CLIENT_ID, TENANT_ID, CLIENT_SECRET);

    final SignerConfigurationBuilder configBuilder =
        new SignerConfigurationBuilder()
            .withMode(calculateMode(keyType))
            .withAzureKeyVaultParameters(azureParams);

    startSigner(configBuilder.build());

    final Response response = signer.callApiPublicKeys(keyType);
    response
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("", hasItems(expectedKeys(keyType)));

    // Since our Azure vault contains some invalid keys, the healthcheck would return 503.
    final Response healthcheckResponse = signer.healthcheck();
    healthcheckResponse
        .then()
        .statusCode(503)
        .contentType(ContentType.JSON)
        .body("status", equalTo("DOWN"));

    // keys loaded would still be >= 1 though
    final String jsonBody = healthcheckResponse.body().asString();
    int keysLoaded = getAzureBulkLoadingData(jsonBody, "keys-loaded");
    assertThat(keysLoaded).isGreaterThanOrEqualTo(1);
  }

  @ParameterizedTest(name = "{index} - KeyType: {0}, using config file: {1}")
  @MethodSource("azureSecretsViaTag")
  void azureSecretsViaTag(final KeyType keyType, boolean useConfigFile) {
    final AzureKeyVaultParameters azureParams =
        new DefaultAzureKeyVaultParameters(
            VAULT_NAME, CLIENT_ID, TENANT_ID, CLIENT_SECRET, Map.of("ENV", "TEST"));

    final SignerConfigurationBuilder configBuilder =
        new SignerConfigurationBuilder()
            .withMode(calculateMode(keyType))
            .withUseConfigFile(useConfigFile)
            .withAzureKeyVaultParameters(azureParams);

    startSigner(configBuilder.build());

    final Response response = signer.callApiPublicKeys(keyType);
    response
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("", hasItems(expectedKeys(keyType)));

    // the tag filter will return only valid keys. The healthcheck should be UP
    final Response healthcheckResponse = signer.healthcheck();
    healthcheckResponse
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("status", equalTo("UP"));

    // keys loaded should be 1 as well.
    final String jsonBody = healthcheckResponse.body().asString();
    int keysLoaded = getAzureBulkLoadingData(jsonBody, "keys-loaded");
    int errorCount = getAzureBulkLoadingData(jsonBody, "error-count");
    assertThat(keysLoaded).isOne();
    assertThat(errorCount).isZero();
  }

  private static Stream<Arguments> azureSecretsViaTag() {
    return Stream.of(
        Arguments.arguments(KeyType.BLS, false),
        Arguments.arguments(KeyType.BLS, true),
        Arguments.arguments(KeyType.SECP256K1, false),
        Arguments.arguments(KeyType.SECP256K1, true));
  }

  private static int getAzureBulkLoadingData(String healthCheckJsonBody, String dataKey) {
    final JsonObject jsonObject = new JsonObject(healthCheckJsonBody);
    return jsonObject.getJsonArray("checks").stream()
        .filter(o -> "keys-check".equals(((JsonObject) o).getString("id")))
        .flatMap(o -> ((JsonObject) o).getJsonArray("checks").stream())
        .filter(o -> "azure-bulk-loading".equals(((JsonObject) o).getString("id")))
        .mapToInt(o -> ((JsonObject) ((JsonObject) o).getValue("data")).getInteger(dataKey))
        .findFirst()
        .orElse(-1);
  }

  @ParameterizedTest
  @EnumSource(KeyType.class)
  void invalidVaultParametersFailsToLoadKeys(final KeyType keyType) {
    final AzureKeyVaultParameters azureParams =
        new DefaultAzureKeyVaultParameters("nonExistentVault", CLIENT_ID, TENANT_ID, CLIENT_SECRET);

    final SignerConfigurationBuilder configBuilder =
        new SignerConfigurationBuilder()
            .withMode(calculateMode(keyType))
            .withAzureKeyVaultParameters(azureParams);

    startSigner(configBuilder.build());

    final Response response = signer.callApiPublicKeys(keyType);
    response.then().statusCode(200).contentType(ContentType.JSON).body("", hasSize(0));

    signer
        .healthcheck()
        .then()
        .statusCode(503)
        .contentType(ContentType.JSON)
        .body("status", equalTo("DOWN"));
  }

  @ParameterizedTest
  @EnumSource(KeyType.class)
  void envVarsAreUsedToDefaultAzureParams(final KeyType keyType) {
    // This ensures env vars correspond to the WEB3SIGNER_<subcommand>_<option> syntax
    final String envPrefix = keyType == KeyType.BLS ? "WEB3SIGNER_ETH2_" : "WEB3SIGNER_ETH1_";
    final Map<String, String> env =
        Map.of(
            envPrefix + "AZURE_VAULT_ENABLED", "true",
            envPrefix + "AZURE_VAULT_NAME", VAULT_NAME,
            envPrefix + "AZURE_CLIENT_ID", CLIENT_ID,
            envPrefix + "AZURE_CLIENT_SECRET", CLIENT_SECRET,
            envPrefix + "AZURE_TENANT_ID", TENANT_ID);

    final SignerConfigurationBuilder configBuilder =
        new SignerConfigurationBuilder().withMode(calculateMode(keyType)).withEnvironment(env);

    startSigner(configBuilder.build());

    final Response response = signer.callApiPublicKeys(keyType);
    response
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("", hasItems(expectedKeys(keyType)));
  }

  private String[] expectedKeys(final KeyType keyType) {
    return keyType == KeyType.BLS ? BLS_EXPECTED_KEYS : SECP_EXPECTED_KEYS;
  }
}
