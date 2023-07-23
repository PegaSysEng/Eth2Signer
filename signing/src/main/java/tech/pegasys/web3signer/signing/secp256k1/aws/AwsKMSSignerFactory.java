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
package tech.pegasys.web3signer.signing.secp256k1.aws;

import static com.google.common.base.Preconditions.checkArgument;

import tech.pegasys.web3signer.signing.config.metadata.AwsKMSMetadata;
import tech.pegasys.web3signer.signing.secp256k1.EthPublicKeyUtils;
import tech.pegasys.web3signer.signing.secp256k1.Signer;

import java.security.interfaces.ECPublicKey;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Create AwsKMSSigner and instantiate AWS' KmsClient library. */
public class AwsKMSSignerFactory {
  private static final Logger LOG = LogManager.getLogger();
  private final boolean applySha3Hash; // Apply Hash.sha3(data) before signing

  public AwsKMSSignerFactory(final boolean applySha3Hash) {
    this.applySha3Hash = applySha3Hash;
  }

  public Signer createSigner(final AwsKMSMetadata awsKMSMetadata) {
    checkArgument(awsKMSMetadata != null, "awsKMSMetadata must not be null");
    // fetch public key as we require it later on to create recovery key
    try (AwsKMS awsKMS =
        new AwsKMS(
            awsKMSMetadata.getAuthenticationMode(),
            awsKMSMetadata.getAwsCredentials().orElse(null),
            awsKMSMetadata.getRegion(),
            awsKMSMetadata.getEndpointOverride())) {
      final ECPublicKey ecPublicKey = awsKMS.getECPublicKey(awsKMSMetadata.getKmsKeyId());
      LOG.trace("AWS KMS Public Key:" + EthPublicKeyUtils.toHexString(ecPublicKey));
      return new AwsKMSSigner(awsKMSMetadata, ecPublicKey, applySha3Hash);
    }
  }
}
