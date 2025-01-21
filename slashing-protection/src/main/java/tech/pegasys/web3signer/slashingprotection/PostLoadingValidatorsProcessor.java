/*
 * Copyright 2025 ConsenSys AG.
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

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.tuweni.bytes.Bytes;

/** Process new validators by registering them with slashing database. */
public record PostLoadingValidatorsProcessor(SlashingProtectionContext slashingProtectionContext)
    implements Consumer<Set<String>> {
  @Override
  public void accept(final Set<String> newValidators) {
    registerNewValidators(newValidators);
  }

  private void registerNewValidators(final Set<String> newValidators) {
    if (newValidators == null || newValidators.isEmpty()) {
      return;
    }

    final List<Bytes> validatorsList = newValidators.stream().map(Bytes::fromHexString).toList();
    slashingProtectionContext.getRegisteredValidators().registerValidators(validatorsList);
  }
}
