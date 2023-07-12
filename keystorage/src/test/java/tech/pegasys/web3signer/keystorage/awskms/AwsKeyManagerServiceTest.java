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
package tech.pegasys.web3signer.keystorage.awskms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import tech.pegasys.web3signer.common.config.AwsAuthenticationMode;
import tech.pegasys.web3signer.common.config.AwsCredentials;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.CreateKeyRequest;
import software.amazon.awssdk.services.kms.model.CreateKeyResponse;
import software.amazon.awssdk.services.kms.model.KeySpec;
import software.amazon.awssdk.services.kms.model.KeyUsageType;
import software.amazon.awssdk.services.kms.model.ScheduleKeyDeletionRequest;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
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
class AwsKeyManagerServiceTest {
  private static final String AWS_ACCESS_KEY_ID = System.getenv("AWS_ACCESS_KEY_ID");
  private static final String AWS_SECRET_ACCESS_KEY = System.getenv("AWS_SECRET_ACCESS_KEY");

  private static final String RW_AWS_ACCESS_KEY_ID = System.getenv("RW_AWS_ACCESS_KEY_ID");
  private static final String RW_AWS_SECRET_ACCESS_KEY = System.getenv("RW_AWS_SECRET_ACCESS_KEY");

  // optional.
  private static final String AWS_REGION =
      Optional.ofNullable(System.getenv("AWS_REGION")).orElse("us-east-2");
  private static final String AWS_SESSION_TOKEN = System.getenv("AWS_SESSION_TOKEN");
  private static final Optional<URI> ENDPOINT_OVERRIDE =
      Optional.ofNullable(System.getenv("AWS_ENDPOINT_OVERRIDE")).map(URI::create);

  private static final AwsCredentials AWS_RW_CREDENTIALS =
      AwsCredentials.builder()
          .withAccessKeyId(RW_AWS_ACCESS_KEY_ID)
          .withSecretAccessKey(RW_AWS_SECRET_ACCESS_KEY)
          .withSessionToken(AWS_SESSION_TOKEN)
          .build();

  private static final AwsCredentials AWS_CREDENTIALS =
      AwsCredentials.builder()
          .withAccessKeyId(AWS_ACCESS_KEY_ID)
          .withSecretAccessKey(AWS_SECRET_ACCESS_KEY)
          .withSessionToken(AWS_SESSION_TOKEN)
          .build();

  private static AwsKeyManagerService awsKeyManagerService;
  private static String testKeyId;

  @BeforeAll
  static void init() {
    awsKeyManagerService =
        new AwsKeyManagerService(
            AwsAuthenticationMode.SPECIFIED, AWS_RW_CREDENTIALS, AWS_REGION, ENDPOINT_OVERRIDE);

    // create a test key
    final KmsClient kmsClient = awsKeyManagerService.getKmsClient();
    final CreateKeyRequest web3SignerTestingKey =
        CreateKeyRequest.builder()
            .keySpec(KeySpec.ECC_SECG_P256_K1)
            .description("Web3Signer Testing Key")
            .keyUsage(KeyUsageType.SIGN_VERIFY)
            .build();
    CreateKeyResponse createKeyResponse = kmsClient.createKey(web3SignerTestingKey);
    testKeyId = createKeyResponse.keyMetadata().keyId();
    assertThat(testKeyId).isNotEmpty();
  }

  @AfterAll
  static void cleanup() {
    if (awsKeyManagerService == null) {
      return;
    }
    // delete key
    ScheduleKeyDeletionRequest deletionRequest =
        ScheduleKeyDeletionRequest.builder().keyId(testKeyId).pendingWindowInDays(7).build();
    awsKeyManagerService.getKmsClient().scheduleKeyDeletion(deletionRequest);

    // close
    awsKeyManagerService.close();
  }

  @Test
  void testGetECPublicKey() {
    // read-only user with GetPublicKey/sign rights
    try (AwsKeyManagerService awsKeyManagerService =
        new AwsKeyManagerService(
            AwsAuthenticationMode.SPECIFIED, AWS_CREDENTIALS, AWS_REGION, ENDPOINT_OVERRIDE)) {

      assertThatCode(() -> awsKeyManagerService.getECPublicKey(testKeyId))
          .doesNotThrowAnyException();
    }
  }

  @Test
  void testSign() {
    // read-only user with GetPublicKey/sign rights
    try (AwsKeyManagerService awsKeyManagerService =
        new AwsKeyManagerService(
            AwsAuthenticationMode.SPECIFIED, AWS_CREDENTIALS, AWS_REGION, ENDPOINT_OVERRIDE)) {

      // call sign twice.
      assertThatCode(
              () ->
                  awsKeyManagerService.sign(
                      testKeyId, "Hello World".getBytes(StandardCharsets.UTF_8)))
          .doesNotThrowAnyException();
      assertThatCode(
              () ->
                  awsKeyManagerService.sign(
                      testKeyId, "Hello World".getBytes(StandardCharsets.UTF_8)))
          .doesNotThrowAnyException();
    }
  }
}
