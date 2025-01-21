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
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Default implementation of {@link ArtifactSignerProvider} that loads signers and proxy signers.
 * This class is designed to provide concurrent access to signers and proxy signers, ensuring thread
 * safety and efficient read operations. The {@code load()} method is called infrequently, typically
 * at startup or via a reload API call, while the getter methods are called frequently.
 *
 * <p>The class uses a {@link ConcurrentHashMap} for storing signers and proxy signers to allow
 * efficient concurrent read access. It also uses a single-threaded executor to ensure that the
 * {@code load()} method and other write operations are executed sequentially.
 */
public class DefaultArtifactSignerProvider implements ArtifactSignerProvider {

  private static final Logger LOG = LogManager.getLogger();

  private final Supplier<Collection<ArtifactSigner>> artifactSignerCollectionSupplier;
  private final Optional<Consumer<Set<String>>> postLoadingCallback;
  private final Optional<KeystoresParameters> commitBoostKeystoresParameters;

  private final ConcurrentMap<String, ArtifactSigner> signers = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Set<ArtifactSigner>> proxySigners = new ConcurrentHashMap<>();
  private final ExecutorService executorService = Executors.newSingleThreadExecutor();

  public DefaultArtifactSignerProvider(
      final Supplier<Collection<ArtifactSigner>> artifactSignerCollectionSupplier,
      final Optional<Consumer<Set<String>>> postLoadingCallback,
      final Optional<KeystoresParameters> commitBoostKeystoresParameters) {
    this.artifactSignerCollectionSupplier = artifactSignerCollectionSupplier;
    this.postLoadingCallback = postLoadingCallback;
    this.commitBoostKeystoresParameters = commitBoostKeystoresParameters;
  }

  @Override
  public Future<Void> load() {
    return executorService.submit(
        () -> {
          LOG.debug("Signer keys pre-loaded in memory {}", signers.size());

          // Load new signers into a temporary map - this is time-consuming logic
          final Map<String, ArtifactSigner> newSigners =
              artifactSignerCollectionSupplier.get().stream()
                  .collect(
                      Collectors.toMap(
                          ArtifactSigner::getIdentifier,
                          Function.identity(),
                          (signer1, signer2) -> {
                            LOG.warn(
                                "Duplicate keys were found while loading. {}", Function.identity());
                            return signer1;
                          }));

          // Collect all stale keys that are no longer valid
          final Set<String> staleKeys = new HashSet<>(signers.keySet());
          staleKeys.removeAll(newSigners.keySet());

          // Update the signers map with new signers
          signers.putAll(newSigners);

          // remove stale keys from signers map
          staleKeys.forEach(signers::remove);

          // Callback to perform further actions specific to eth1/eth2 mode (if any)
          postLoadingCallback.ifPresent(callback -> callback.accept(signers.keySet()));

          // For each loaded signer, load commit boost proxy signers (if any)
          commitBoostKeystoresParameters
              .filter(KeystoresParameters::isEnabled)
              .ifPresent(
                  keystoreParameter -> {
                    signers
                        .keySet()
                        .forEach(
                            consensusPubKey -> {
                              LOG.trace(
                                  "Loading proxy signers for signer '{}' ...", consensusPubKey);
                              loadProxySigners(
                                  keystoreParameter,
                                  consensusPubKey,
                                  SECP256K1.name(),
                                  SecpV3KeystoresBulkLoader::loadECDSAProxyKeystores);

                              loadProxySigners(
                                  keystoreParameter,
                                  consensusPubKey,
                                  BLS.name(),
                                  BlsKeystoreBulkLoader::loadKeystoresUsingPasswordFile);
                            });
                    // Remove stale proxy signers
                    staleKeys.forEach(proxySigners::remove);
                  });

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
  public Optional<ArtifactSigner> getProxySigner(final String proxyPubKey) {
    return proxySigners.values().stream()
        .flatMap(Set::stream)
        .filter(signer -> signer.getIdentifier().equals(proxyPubKey))
        .findFirst();
  }

  @Override
  public Set<String> availableIdentifiers() {
    return Set.copyOf(signers.keySet());
  }

  @Override
  public Map<KeyType, Set<String>> getProxyIdentifiers(final String consensusPubKey) {
    final Set<ArtifactSigner> artifactSigners =
        proxySigners.computeIfAbsent(consensusPubKey, k -> ConcurrentHashMap.newKeySet());
    return artifactSigners.stream()
        .collect(
            Collectors.groupingBy(
                ArtifactSigner::getKeyType,
                Collectors.mapping(ArtifactSigner::getIdentifier, Collectors.toSet())));
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
  public Future<Void> addProxySigner(
      final ArtifactSigner proxySigner, final String consensusPubKey) {
    return executorService.submit(
        () -> {
          proxySigners
              .computeIfAbsent(consensusPubKey, k -> ConcurrentHashMap.newKeySet())
              .add(proxySigner);
          LOG.info(
              "Loaded new proxy signer {} for consensus public key '{}'",
              proxySigner.getIdentifier(),
              consensusPubKey);
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

  /**
   * Load proxy signers for given consensus public key and add it to internal proxy signers map.
   *
   * @param keystoreParameter location of proxy keystores and password file
   * @param consensusPubKey Consensus public key
   * @param keyType BLS or SECP256K1
   * @param loaderFunction Bulkloading method reference
   */
  private void loadProxySigners(
      final KeystoresParameters keystoreParameter,
      final String consensusPubKey,
      final String keyType,
      final BiFunction<Path, Path, MappedResults<ArtifactSigner>> loaderFunction) {

    // Calculate identifierPath from keystoreParameter
    final Path identifierPath = keystoreParameter.getKeystoresPath().resolve(consensusPubKey);
    final Path proxyDir = identifierPath.resolve(keyType);

    if (canReadFromDirectory(proxyDir)) {
      final MappedResults<ArtifactSigner> signersResult =
          loaderFunction.apply(proxyDir, keystoreParameter.getKeystoresPasswordFile());
      final Collection<ArtifactSigner> signers = signersResult.getValues();
      proxySigners
          .computeIfAbsent(consensusPubKey, k -> ConcurrentHashMap.newKeySet())
          .addAll(signers);
    }
  }
}
