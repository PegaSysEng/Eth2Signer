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
package tech.pegasys.web3signer.slashingprotection;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt64;

public interface SlashingProtection {

  boolean maySignAttestation(
      Bytes publicKey,
      Bytes signingRoot,
      UInt64 sourceEpoch,
      UInt64 targetEpoch,
      Bytes32 genesisValidatorsRoot);

  boolean maySignBlock(
      Bytes publicKey, Bytes signingRoot, UInt64 blockSlot, Bytes32 genesisValidatorsRoot);

  void registerValidators(List<Bytes> validators);

  void export(OutputStream output);

  void importData(InputStream output);

  void prune(final long epochsToPrune, final long slotsPerEpoch);
}
