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
package tech.pegasys.eth2signer.core.signing;

import tech.pegasys.eth2signer.core.utils.ByteUtils;
import tech.pegasys.signers.secp256k1.api.Signature;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class SecpArtifactSignature implements ArtifactSignature {
  private final Signature signature;

  public SecpArtifactSignature(final Signature signature) {
    this.signature = signature;
  }

  @Override
  public String toHexString() {
    final Bytes outputSignature =
        Bytes.concatenate(
            Bytes32.leftPad(Bytes.wrap(ByteUtils.bigIntegerToBytes(signature.getR()))),
            Bytes32.leftPad(Bytes.wrap(ByteUtils.bigIntegerToBytes(signature.getS()))),
            Bytes.wrap(ByteUtils.bigIntegerToBytes(signature.getV())));
    return outputSignature.toHexString();
  }
}
