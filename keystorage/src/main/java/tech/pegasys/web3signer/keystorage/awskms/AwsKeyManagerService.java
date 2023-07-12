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

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.web3signer.common.config.AwsAuthenticationMode.ENVIRONMENT;

import tech.pegasys.web3signer.common.config.AwsAuthenticationMode;
import tech.pegasys.web3signer.common.config.AwsCredentials;

import java.net.URI;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.KmsClientBuilder;
import software.amazon.awssdk.services.kms.model.GetPublicKeyRequest;
import software.amazon.awssdk.services.kms.model.GetPublicKeyResponse;
import software.amazon.awssdk.services.kms.model.KeySpec;
import software.amazon.awssdk.services.kms.model.MessageType;
import software.amazon.awssdk.services.kms.model.SignRequest;
import software.amazon.awssdk.services.kms.model.SignResponse;
import software.amazon.awssdk.services.kms.model.SigningAlgorithmSpec;

public class AwsKeyManagerService implements AutoCloseable {
  private final AwsAuthenticationMode authMode;
  private final AwsCredentials awsCredentials;
  private final String region;

  private KmsClient kmsClient;

  private Optional<URI> endpointOverride;

  public AwsKeyManagerService(
      final AwsAuthenticationMode authMode,
      final AwsCredentials awsCredentials,
      final String region,
      final Optional<URI> endpointOverride) {
    this.authMode = authMode;
    this.awsCredentials = awsCredentials;
    this.region = region;
    this.endpointOverride = endpointOverride;

    initKmsClient();
  }

  void initKmsClient() {
    if (kmsClient != null) {
      return;
    }

    final KmsClientBuilder kmsClientBuilder = KmsClient.builder();
    endpointOverride.ifPresent(kmsClientBuilder::endpointOverride);
    kmsClient =
        kmsClientBuilder
            .credentialsProvider(getAwsCredentialsProvider())
            .region(Region.of(region))
            .build();
  }

  private AwsCredentialsProvider getAwsCredentialsProvider() {
    final AwsCredentialsProvider awsCredentialsProvider;
    if (authMode == ENVIRONMENT) {
      awsCredentialsProvider = DefaultCredentialsProvider.create();
    } else {
      final software.amazon.awssdk.auth.credentials.AwsCredentials awsSdkCredentials;
      if (awsCredentials.getSessionToken().isPresent()) {
        awsSdkCredentials =
            AwsSessionCredentials.create(
                awsCredentials.getAccessKeyId(),
                awsCredentials.getSecretAccessKey(),
                awsCredentials.getSessionToken().get());
      } else {
        awsSdkCredentials =
            AwsBasicCredentials.create(
                awsCredentials.getAccessKeyId(), awsCredentials.getSecretAccessKey());
      }

      awsCredentialsProvider = StaticCredentialsProvider.create(awsSdkCredentials);
    }
    return awsCredentialsProvider;
  }

  public ECPublicKey getECPublicKey(final String kmsKeyId) {
    checkArgument(kmsClient != null, "KmsClient is not initialized");

    // Question ... do we need to set grantTokens?
    final GetPublicKeyRequest getPublicKeyRequest =
        GetPublicKeyRequest.builder().keyId(kmsKeyId).build();
    final GetPublicKeyResponse publicKeyResponse = kmsClient.getPublicKey(getPublicKeyRequest);
    KeySpec keySpec = publicKeyResponse.keySpec();
    if (keySpec != KeySpec.ECC_SECG_P256_K1) {
      throw new RuntimeException("Unsupported key spec from AWS KMS: " + keySpec.toString());
    }

    final X509EncodedKeySpec encodedKeySpec =
        new X509EncodedKeySpec(publicKeyResponse.publicKey().asByteArray());
    try {
      KeyFactory keyFactory = KeyFactory.getInstance("EC");
      return (ECPublicKey) keyFactory.generatePublic(encodedKeySpec);
    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
      throw new RuntimeException(e);
    }
  }

  public byte[] sign(final String kmsKeyId, final byte[] data) {
    final SignRequest signRequest =
        SignRequest.builder()
            .keyId(kmsKeyId)
            .signingAlgorithm(SigningAlgorithmSpec.ECDSA_SHA_256)
            .messageType(MessageType.DIGEST)
            .message(SdkBytes.fromByteArray(data))
            .build();
    final SignResponse signResponse = kmsClient.sign(signRequest);
    return signResponse.signature().asByteArray();
  }

  @VisibleForTesting
  public KmsClient getKmsClient() {
    return kmsClient;
  }

  @Override
  public void close() {
    if (kmsClient != null) {
      kmsClient.close();
    }
  }
}
