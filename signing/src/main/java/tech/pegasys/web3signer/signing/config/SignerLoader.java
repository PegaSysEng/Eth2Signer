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

import tech.pegasys.web3signer.keystorage.common.MappedResults;
import tech.pegasys.web3signer.signing.ArtifactSigner;
import tech.pegasys.web3signer.signing.config.metadata.SigningMetadata;
import tech.pegasys.web3signer.signing.config.metadata.SigningMetadataException;
import tech.pegasys.web3signer.signing.config.metadata.parser.SignerParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.AbstractMap.SimpleEntry;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The SignerLoaded loads the metadata files and converts them to ArtifactSigners. This class keeps
 * track of the metadata files and ArtifactSigners that have been read and only reads them again if
 * they have been modified. It also removes the cached ArtifactSigners if the metadata file has been
 * removed.
 */
public class SignerLoader {

  private static final Logger LOG = LogManager.getLogger();
  private static final long FILES_PROCESSED_TO_REPORT = 10;
  private static final Set<String> CONFIG_FILE_EXTENSIONS = Set.of("yaml", "yml");

  record CachedArtifactSigners(
      Path metadataFile, FileTime lastModifiedTime, Set<ArtifactSigner> artifactSigners) {}

  private static final Map<Path, CachedArtifactSigners> cachedArtifactSigners =
      new ConcurrentHashMap<>();

  /**
   * Load ArtifactSigners for new or modified metadata files. Return cached ArtifactSigners if
   * metadata files have not been modified. Remove cached ArtifactSigner if metadata file has been
   * removed.
   *
   * @param configsDirectory Location of the metadata files
   * @param signerParser An implementation of SignerParser to parse the metadata files
   * @return A MappedResults of ArtifactSigners and error count
   */
  public static MappedResults<ArtifactSigner> load(
      final Path configsDirectory,
      final SignerParser signerParser,
      final boolean useParallelStreams) {
    LOG.info("Loading signer configuration metadata files from {}", configsDirectory);
    final Instant loadStartTime = Instant.now();
    // get all metadata file paths from the config directory.
    final Map<Path, CachedArtifactSigners> filteredPaths;
    try {
      filteredPaths = getMetadataConfigFiles(configsDirectory);
    } catch (final IOException e) {
      LOG.error("Unable to access the supplied key directory", e);
      return MappedResults.errorResult();
    }

    // remove cached metadata file mappings that have been deleted or not loaded
    var cachedArtifactSignerSize = cachedArtifactSigners.size();
    cachedArtifactSigners.keySet().removeIf(path -> !filteredPaths.containsKey(path));
    if (cachedArtifactSignerSize != cachedArtifactSigners.size()) {
      LOG.info(
          "Removed {} cached metadata files that has not been loaded.",
          cachedArtifactSignerSize - cachedArtifactSigners.size());
    }

    // reload the metadata files that are either new or modified
    LOG.info(
        "Loading and converting SigningMetadata to ArtifactSigner using {} streams ...",
        useParallelStreams ? "parallel" : "sequential");
    final Stream<Map.Entry<Path, CachedArtifactSigners>> pathStream =
        useParallelStreams
            ? filteredPaths.entrySet().parallelStream()
            : filteredPaths.entrySet().stream();

    final AtomicLong configFilesHandled = new AtomicLong(0);
    final AtomicInteger errorCount = new AtomicInteger(0);
    final Map<Path, Set<ArtifactSigner>> loadedArtSigners =
        pathStream
            .filter(SignerLoader::isModifiedOrNew)
            .flatMap(
                entry -> {
                  if (configFilesHandled.incrementAndGet() % FILES_PROCESSED_TO_REPORT == 0) {
                    LOG.info("{} signing metadata processed", configFilesHandled.get());
                  }
                  try {
                    return Stream.of(
                        new SimpleEntry<>(
                            entry.getKey(),
                            Files.readString(entry.getKey(), StandardCharsets.UTF_8)));
                  } catch (IOException e) {
                    LOG.error("Error reading metadata config file: {}", entry.getKey(), e);
                    errorCount.incrementAndGet();
                    return Stream.empty();
                  }
                })
            .flatMap(
                entry -> {
                  try {
                    final List<SigningMetadata> signingMetadata =
                        signerParser.readSigningMetadata(entry.getValue());
                    return Stream.of(new SimpleEntry<>(entry.getKey(), signingMetadata));
                  } catch (final SigningMetadataException e) {
                    LOG.error(
                        "Error parsing metadata file {} to signing metadata: {}",
                        entry.getKey(),
                        ExceptionUtils.getRootCauseMessage(e));
                    errorCount.incrementAndGet();
                    return Stream.empty();
                  }
                })
            .flatMap(
                entry -> {
                  try {
                    final Set<ArtifactSigner> artifactSigners =
                        new HashSet<>(signerParser.parse(entry.getValue()));
                    return Stream.of(new SimpleEntry<>(entry.getKey(), artifactSigners));
                  } catch (final SigningMetadataException e) {
                    LOG.error(
                        "Error converting signing metadata to Artifact Signer: {}",
                        ExceptionUtils.getRootCauseMessage(e));
                    errorCount.incrementAndGet();
                    return Stream.empty();
                  }
                })
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    // merge the new loaded ArtifactSigners with the cached ones
    loadedArtSigners.forEach(
        (path, artifactSigners) -> {
          cachedArtifactSigners.put(
              path,
              new CachedArtifactSigners(
                  path, filteredPaths.get(path).lastModifiedTime, artifactSigners));
        });

    // return all ArtifactSigners from the cache. If same ArtifactSigner is loaded from multiple
    // paths, only one will
    // be returned.
    final Collection<ArtifactSigner> allArtifactSigners =
        cachedArtifactSigners.values().stream()
            .flatMap(cachedArtifactSigners -> cachedArtifactSigners.artifactSigners.stream())
            .collect(Collectors.toSet());

    LOG.info(
        "Total Artifact Signers loaded via configuration files: {}\nTotal Paths cached: {}, Error count: {}\nTime Taken: {}.",
        allArtifactSigners.size(),
        cachedArtifactSigners.size(),
        errorCount.get(),
        calculateTimeTaken(loadStartTime).orElse("unknown duration"));

    return MappedResults.newInstance(allArtifactSigners, errorCount.get());
  }

  @VisibleForTesting
  static void clearCache() {
    cachedArtifactSigners.clear();
  }

  /**
   * Load Metadata config file paths and their timestamps.
   *
   * @param configsDirectory Path to the directory containing the metadata files
   * @return A map of metadata file paths and their last modified timestamps
   * @throws IOException If there is an error reading the config directory.
   */
  private static Map<Path, CachedArtifactSigners> getMetadataConfigFiles(
      final Path configsDirectory) throws IOException {
    try (final Stream<Path> fileStream = Files.list(configsDirectory)) {
      return fileStream
          .filter(SignerLoader::validFileExtension)
          .map(
              path -> {
                try {
                  return new SimpleEntry<>(
                      path,
                      new CachedArtifactSigners(path, Files.getLastModifiedTime(path), Set.of()));
                } catch (final IOException e) {
                  // very unlikely to happen as Files.list is already successful.
                  LOG.error("Error getting last modified time for config file: {}", path, e);
                  return null;
                }
              })
          .filter(Objects::nonNull)
          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
  }

  private static boolean isModifiedOrNew(final Map.Entry<Path, CachedArtifactSigners> entry) {
    var metadataFilePath = entry.getKey();
    var metadataFile = entry.getValue();
    return !cachedArtifactSigners.containsKey(metadataFilePath)
        || cachedArtifactSigners
                .get(metadataFilePath)
                .lastModifiedTime
                .compareTo(metadataFile.lastModifiedTime)
            != 0;
  }

  @VisibleForTesting
  static Optional<String> calculateTimeTaken(final Instant start) {
    final Instant now = Instant.now();
    final long timeTaken = Duration.between(start, now).toMillis();
    if (timeTaken < 0) {
      LOG.warn("System Clock returned time in past. Start: {}, Now: {}.", start, now);
      return Optional.empty();
    }
    return Optional.of(DurationFormatUtils.formatDurationHMS(timeTaken));
  }

  private static boolean validFileExtension(final Path filename) {
    final boolean isHidden = filename.toFile().isHidden();
    final String extension = FilenameUtils.getExtension(filename.toString());
    return !isHidden && CONFIG_FILE_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT));
  }
}
