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
package tech.pegasys.web3signer.signing;

import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.web3signer.signing.config.metadata.SignerOrigin;
import tech.pegasys.web3signer.signing.util.IdentifierUtils;

import java.util.Objects;
import java.util.Optional;

import com.google.common.base.MoreObjects;
import org.apache.tuweni.bytes.Bytes;

public class BlsArtifactSigner implements ArtifactSigner {

  private final BLSKeyPair keyPair;
  private final SignerOrigin origin;
  private final Optional<String> path;

  public BlsArtifactSigner(
      final BLSKeyPair keyPair, final SignerOrigin origin, final Optional<String> path) {
    this.keyPair = keyPair;
    this.origin = origin;
    this.path = path;
  }

  public BlsArtifactSigner(final BLSKeyPair keyPair, final SignerOrigin origin) {
    this.keyPair = keyPair;
    this.origin = origin;
    this.path = Optional.empty();
  }

  @Override
  public String getIdentifier() {
    return IdentifierUtils.normaliseIdentifier(keyPair.getPublicKey().toString());
  }

  @Override
  public BlsArtifactSignature sign(final Bytes data) {
    return new BlsArtifactSignature(BLS.sign(keyPair.getSecretKey(), data));
  }

  @Override
  public KeyType getKeyType() {
    return KeyType.BLS;
  }

  public Optional<String> getPath() {
    return path;
  }

  // only signers loaded from key store files are editable, everything else is read only
  public boolean isReadOnlyKey() {
    return origin != SignerOrigin.FILE_KEYSTORE;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BlsArtifactSigner that = (BlsArtifactSigner) o;
    return keyPair.equals(that.keyPair);
  }

  @Override
  public int hashCode() {
    return Objects.hash(keyPair);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("keyPair", keyPair.getPublicKey())
        .add("origin", origin)
        .toString();
  }
}
