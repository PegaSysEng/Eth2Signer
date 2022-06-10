/*
 * Copyright 2022 ConsenSys AG.
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

import static org.hamcrest.Matchers.containsInAnyOrder;

import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSecretKey;
import tech.pegasys.web3signer.AwsSecretsManagerUtil;
import tech.pegasys.web3signer.dsl.signer.SignerConfigurationBuilder;
import tech.pegasys.web3signer.signing.KeyType;
import tech.pegasys.web3signer.signing.config.AwsAuthenticationMode;
import tech.pegasys.web3signer.signing.config.AwsSecretsManagerParameters;
import tech.pegasys.web3signer.signing.config.AwsSecretsManagerParametersBuilder;
import tech.pegasys.web3signer.tests.AcceptanceTestBase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import io.restassured.http.ContentType;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@EnabledIfEnvironmentVariable(
    named = "RW_AWS_ACCESS_KEY_ID",
    matches = ".*",
    disabledReason = "RW_AWS_ACCESS_KEY_ID env variable is required")
@EnabledIfEnvironmentVariable(
    named = "RW_AWS_SECRET_ACCESS_KEY",
    matches = ".*",
    disabledReason = "RW_AWS_SECRET_ACCESS_KEY env variable is required")
@EnabledIfEnvironmentVariable(
    named = "AWS_ACCESS_KEY_ID",
    matches = ".*",
    disabledReason = "AWS_ACCESS_KEY_ID env variable is required")
@EnabledIfEnvironmentVariable(
    named = "AWS_SECRET_ACCESS_KEY",
    matches = ".*",
    disabledReason = "AWS_SECRET_ACCESS_KEY env variable is required")
@EnabledIfEnvironmentVariable(
    named = "AWS_REGION",
    matches = ".*",
    disabledReason = "AWS_REGION env variable is required")
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // same instance is shared across test methods
public class AwsSecretsManagerAcceptanceTest extends AcceptanceTestBase {
  private static final String RW_AWS_ACCESS_KEY_ID = System.getenv("RW_AWS_ACCESS_KEY_ID");
  private static final String RW_AWS_SECRET_ACCESS_KEY = System.getenv("RW_AWS_SECRET_ACCESS_KEY");
  private static final String RO_AWS_ACCESS_KEY_ID = System.getenv("AWS_ACCESS_KEY_ID");
  private static final String RO_AWS_SECRET_ACCESS_KEY = System.getenv("AWS_SECRET_ACCESS_KEY");
  private static final String AWS_REGION =
      Optional.ofNullable(System.getenv("AWS_REGION")).orElse("us-east-2");
  private static final List<String> BLS_PRIVATE_KEYS =
      List.of(
          "3ee2224386c82ffea477e2adf28a2929f5c349165a4196158c7f3a2ecca40f35",
          "32ae313afff2daa2ef7005a7f834bdf291855608fe82c24d30be6ac2017093a8");
  private AwsSecretsManagerUtil awsSecretsManagerUtil;
  private final List<String> awsKeysIdentifiers = new ArrayList<>();

  @BeforeAll
  void setupAwsResources() {
    awsSecretsManagerUtil =
        new AwsSecretsManagerUtil(AWS_REGION, RW_AWS_ACCESS_KEY_ID, RW_AWS_SECRET_ACCESS_KEY);

    BLS_PRIVATE_KEYS.forEach(
        key -> {
          final String pubKey =
              new BLSKeyPair(BLSSecretKey.fromBytes(Bytes32.fromHexString(key)))
                  .getPublicKey()
                  .toString();
          awsSecretsManagerUtil.createSecret(pubKey, key, Collections.emptyMap());
          awsKeysIdentifiers.add(pubKey);
        });
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void secretsAreLoadedFromAWSSecretsManagerAndReportedByPublicApi(final boolean useConfigFile) {
    final AwsSecretsManagerParameters awsSecretsManagerParameters =
        AwsSecretsManagerParametersBuilder.anAwsSecretsManagerParameters()
            .withAuthenticationMode(AwsAuthenticationMode.SPECIFIED)
            .withRegion(AWS_REGION)
            .withAccessKeyId(RO_AWS_ACCESS_KEY_ID)
            .withSecretAccessKey(RO_AWS_SECRET_ACCESS_KEY)
            .withPrefixesFilter(List.of(awsSecretsManagerUtil.getSecretsManagerPrefix()))
            .build();

    final SignerConfigurationBuilder configBuilder =
        new SignerConfigurationBuilder()
            .withUseConfigFile(useConfigFile)
            .withMode("eth2")
            .withAwsSecretsManagerParameters(awsSecretsManagerParameters);

    startSigner(configBuilder.build());

    signer
        .callApiPublicKeys(KeyType.BLS)
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("", containsInAnyOrder(awsKeysIdentifiers.toArray()));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void secretsAreLoadedFromAWSSecretsManagerWithEnvironmentAuthModeAndReportedByPublicApi(
      final boolean useConfigFile) {
    final AwsSecretsManagerParameters awsSecretsManagerParameters =
        AwsSecretsManagerParametersBuilder.anAwsSecretsManagerParameters()
            .withAuthenticationMode(AwsAuthenticationMode.ENVIRONMENT)
            .withPrefixesFilter(List.of(awsSecretsManagerUtil.getSecretsManagerPrefix()))
            .build();

    final SignerConfigurationBuilder configBuilder =
        new SignerConfigurationBuilder()
            .withUseConfigFile(useConfigFile)
            .withMode("eth2")
            .withAwsSecretsManagerParameters(awsSecretsManagerParameters);

    startSigner(configBuilder.build());

    signer
        .callApiPublicKeys(KeyType.BLS)
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("", containsInAnyOrder(awsKeysIdentifiers.toArray()));
  }

  @AfterAll
  void cleanUpAwsResources() {
    if (awsSecretsManagerUtil != null) {
      awsKeysIdentifiers.forEach(
          identifier -> {
            try {
              awsSecretsManagerUtil.deleteSecret(identifier);
            } catch (RuntimeException ignored) {
            }
          });
      awsSecretsManagerUtil.close();
    }
  }
}
