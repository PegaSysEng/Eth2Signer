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
package tech.pegasys.web3signer.signing.bulkloading;

import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSecretKey;
import tech.pegasys.web3signer.keystorage.aws.AwsSecretsManager;
import tech.pegasys.web3signer.keystorage.aws.AwsSecretsManagerProvider;
import tech.pegasys.web3signer.keystorage.common.MappedResults;
import tech.pegasys.web3signer.signing.ArtifactSigner;
import tech.pegasys.web3signer.signing.BlsArtifactSigner;
import tech.pegasys.web3signer.signing.config.AwsSecretsManagerFactory;
import tech.pegasys.web3signer.signing.config.AwsVaultParameters;
import tech.pegasys.web3signer.signing.config.metadata.SignerOrigin;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class BlsAwsBulkLoader {

  public MappedResults<ArtifactSigner> load(final AwsVaultParameters parameters) {
    try (final AwsSecretsManagerProvider awsSecretsManagerProvider =
        new AwsSecretsManagerProvider(parameters.getCacheMaximumSize())) {
      final AwsSecretsManager awsSecretsManager =
          AwsSecretsManagerFactory.createAwsSecretsManager(awsSecretsManagerProvider, parameters);
      return awsSecretsManager.mapSecrets(
          parameters.getPrefixesFilter(),
          parameters.getTags(),
          (key, value) -> {
            final Bytes privateKeyBytes = Bytes.fromHexString(value);
            final BLSKeyPair keyPair =
                new BLSKeyPair(BLSSecretKey.fromBytes(Bytes32.wrap(privateKeyBytes)));
            return new BlsArtifactSigner(keyPair, SignerOrigin.AWS);
          });
    }
  }
}
