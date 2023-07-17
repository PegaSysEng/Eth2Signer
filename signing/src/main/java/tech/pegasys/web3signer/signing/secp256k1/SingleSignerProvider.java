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
package tech.pegasys.web3signer.signing.secp256k1;

import java.security.interfaces.ECPublicKey;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public class SingleSignerProvider implements SignerProvider {
  private final Signer signer;

  public SingleSignerProvider(Signer signer) {
    if (signer == null) {
      throw new IllegalArgumentException("SingleSignerFactory requires a non-null Signer");
    } else {
      this.signer = signer;
    }
  }

  @Override
  public Optional<Signer> getSigner(SignerIdentifier signerIdentifier) {
    if (signerIdentifier == null) {
      return Optional.empty();
    } else {
      return signerIdentifier.validate(this.signer.getPublicKey())
          ? Optional.of(this.signer)
          : Optional.empty();
    }
  }

  @Override
  public Set<ECPublicKey> availablePublicKeys(
      Function<ECPublicKey, SignerIdentifier> identifierFunction) {
    return this.signer.getPublicKey() != null
        ? Set.of(this.signer.getPublicKey())
        : Collections.emptySet();
  }
}
