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
package tech.pegasys.web3signer.signing.config;

import static tech.pegasys.web3signer.signing.KeyType.BLS;
import static tech.pegasys.web3signer.signing.KeyType.SECP256K1;

import tech.pegasys.web3signer.keystorage.common.MappedResults;
import tech.pegasys.web3signer.signing.ArtifactSigner;
import tech.pegasys.web3signer.signing.ArtifactSignerProvider;
import tech.pegasys.web3signer.signing.KeyType;
import tech.pegasys.web3signer.signing.bulkloading.BlsKeystoreBulkLoader;
import tech.pegasys.web3signer.signing.bulkloading.SecpV3KeystoresBulkLoader;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DefaultArtifactSignerProvider implements ArtifactSignerProvider {

  private static final Logger LOG = LogManager.getLogger();
  private final Supplier<Collection<ArtifactSigner>> artifactSignerCollectionSupplier;
  private final Map<String, ArtifactSigner> signers = new HashMap<>();
  private final Map<String, List<ArtifactSigner>> proxySigners = new HashMap<>();
  private final ExecutorService executorService = Executors.newSingleThreadExecutor();
  private final Optional<CommitBoostParameters> commitBoostParameters;

  public DefaultArtifactSignerProvider(
      final Supplier<Collection<ArtifactSigner>> artifactSignerCollectionSupplier,
      final Optional<CommitBoostParameters> commitBoostParameters) {
    this.artifactSignerCollectionSupplier = artifactSignerCollectionSupplier;
    this.commitBoostParameters = commitBoostParameters;
  }

  @Override
  public Future<Void> load() {
    return executorService.submit(
        () -> {
          LOG.debug("Signer keys pre-loaded in memory {}", signers.size());

          artifactSignerCollectionSupplier.get().stream()
              .collect(
                  Collectors.toMap(
                      ArtifactSigner::getIdentifier,
                      Function.identity(),
                      (signer1, signer2) -> {
                        LOG.warn(
                            "Duplicate keys were found while loading. {}", Function.identity());
                        return signer1;
                      }))
              .forEach(signers::putIfAbsent);

          // for each loaded signer, load commit boost proxy signers (if any)
          commitBoostParameters
              .filter(CommitBoostParameters::isEnabled)
              .ifPresent(
                  keystoreParameter ->
                      signers
                          .keySet()
                          .forEach(
                              signerIdentifier -> {
                                LOG.trace(
                                    "Loading proxy signers for signer '{}' ...", signerIdentifier);
                                loadProxySigners(
                                    keystoreParameter,
                                    signerIdentifier,
                                    SECP256K1.name(),
                                    SecpV3KeystoresBulkLoader
                                        ::loadKeystoresWithCompressedIdentifier);

                                loadProxySigners(
                                    keystoreParameter,
                                    signerIdentifier,
                                    BLS.name(),
                                    BlsKeystoreBulkLoader::loadKeystoresUsingPasswordFile);
                              }));

          LOG.info("Total signers (keys) currently loaded in memory: {}", signers.size());
          return null;
        });
  }

  @Override
  public Optional<ArtifactSigner> getSigner(final String identifier) {
    final Optional<ArtifactSigner> result = Optional.ofNullable(signers.get(identifier));

    if (result.isEmpty()) {
      LOG.error("No signer was loaded matching identifier '{}'", identifier);
    }
    return result;
  }

  @Override
  public Set<String> availableIdentifiers() {
    return Set.copyOf(signers.keySet());
  }

  @Override
  public Map<KeyType, List<String>> getProxyIdentifiers(final String identifier) {
    final List<ArtifactSigner> artifactSigners =
        proxySigners.computeIfAbsent(identifier, k -> List.of());
    return artifactSigners.stream()
        .collect(
            Collectors.groupingBy(
                ArtifactSigner::getKeyType,
                Collectors.mapping(ArtifactSigner::getIdentifier, Collectors.toList())));
  }

  @Override
  public Future<Void> addSigner(final ArtifactSigner signer) {
    return executorService.submit(
        () -> {
          signers.put(signer.getIdentifier(), signer);
          LOG.info("Loaded new signer for identifier '{}'", signer.getIdentifier());
          return null;
        });
  }

  @Override
  public Future<Void> removeSigner(final String identifier) {
    return executorService.submit(
        () -> {
          signers.remove(identifier);
          proxySigners.remove(identifier);
          LOG.info("Removed signer with identifier '{}'", identifier);
          return null;
        });
  }

  @Override
  public Future<Void> addProxySigner(final ArtifactSigner signer, final String identifier) {
    return executorService.submit(
        () -> {
          proxySigners.computeIfAbsent(identifier, k -> new ArrayList<>()).add(signer);
          LOG.info(
              "Loaded new proxy signer {} for identifier '{}'", signer.getIdentifier(), identifier);
          return null;
        });
  }

  @Override
  public void close() {
    executorService.shutdownNow();
  }

  private static boolean canReadFromDirectory(final Path path) {
    final File file = path.toFile();
    return file.canRead() && file.isDirectory();
  }

  private void loadProxySigners(
      final CommitBoostParameters keystoreParameter,
      final String identifier,
      final String keyType,
      final BiFunction<Path, Path, MappedResults<ArtifactSigner>> loaderFunction) {

    // Calculate identifierPath from keystoreParameter
    final Path identifierPath = keystoreParameter.getProxyKeystoresPath().resolve(identifier);
    final Path proxyDir = identifierPath.resolve(keyType);

    if (canReadFromDirectory(proxyDir)) {
      final MappedResults<ArtifactSigner> signersResult =
          loaderFunction.apply(proxyDir, keystoreParameter.getProxyKeystoresPasswordFile());
      final Collection<ArtifactSigner> signers = signersResult.getValues();
      proxySigners.computeIfAbsent(identifier, k -> new ArrayList<>()).addAll(signers);
    }
  }
}
