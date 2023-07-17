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
package tech.pegasys.web3signer.core.util;

import static org.web3j.crypto.Keys.getAddress;
import static tech.pegasys.web3signer.signing.util.IdentifierUtils.normaliseIdentifier;

import tech.pegasys.web3signer.signing.ArtifactSignerProvider;

import java.util.Optional;

public class Eth1AddressUtil {

  public static Optional<String> signerPublicKeyFromAddress(
      final ArtifactSignerProvider signerProvider, final String address) {
    return signerProvider.availableIdentifiers().stream()
        .filter(
            publicKey ->
                normaliseIdentifier(getAddress(publicKey)).equals(normaliseIdentifier(address)))
        .findFirst();
  }
}
