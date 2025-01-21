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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.web3signer.BLSTestUtil;
import tech.pegasys.web3signer.FileHiddenUtil;
import tech.pegasys.web3signer.common.Web3SignerMetricCategory;
import tech.pegasys.web3signer.keystorage.aws.AwsSecretsManagerProvider;
import tech.pegasys.web3signer.keystorage.common.MappedResults;
import tech.pegasys.web3signer.keystorage.hashicorp.HashicorpConnectionFactory;
import tech.pegasys.web3signer.signing.ArtifactSigner;
import tech.pegasys.web3signer.signing.BlsArtifactSigner;
import tech.pegasys.web3signer.signing.config.metadata.BlsArtifactSignerFactory;
import tech.pegasys.web3signer.signing.config.metadata.interlock.InterlockKeyProvider;
import tech.pegasys.web3signer.signing.config.metadata.parser.SignerParser;
import tech.pegasys.web3signer.signing.config.metadata.parser.YamlMapperFactory;
import tech.pegasys.web3signer.signing.config.metadata.parser.YamlSignerParser;
import tech.pegasys.web3signer.signing.config.metadata.yubihsm.YubiHsmOpaqueDataProvider;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SignerLoaderTest {
  private static final YAMLMapper YAML_MAPPER = YamlMapperFactory.createYamlMapper();
  @TempDir Path configsDirectory;
  @Mock private MetricsSystem metricsSystem;
  @Mock private HashicorpConnectionFactory hashicorpConnectionFactory;
  @Mock private InterlockKeyProvider interlockKeyProvider;
  @Mock private YubiHsmOpaqueDataProvider yubiHsmOpaqueDataProvider;
  @Mock private AwsSecretsManagerProvider awsSecretsManagerProvider;
  @Mock private AzureKeyVaultFactory azureKeyVaultFactory;
  @Mock private LabelledMetric<OperationTimer> privateKeyRetrievalTimer;
  @Mock private OperationTimer operationTimer;

  private static final BLSKeyPair blsKeyPair1 = BLSTestUtil.randomKeyPair(1);
  private static final BLSKeyPair blsKeyPair2 = BLSTestUtil.randomKeyPair(2);
  private static final BLSKeyPair blsKeyPair3 = BLSTestUtil.randomKeyPair(3);

  private SignerParser signerParser;

  @BeforeEach
  public void setup() {
    // setup metrics system stubbing
    lenient()
        .when(
            metricsSystem.createLabelledTimer(
                Web3SignerMetricCategory.SIGNING,
                "private_key_retrieval_time",
                "Time taken to retrieve private key",
                "signer"))
        .thenReturn(privateKeyRetrievalTimer);

    lenient().when(privateKeyRetrievalTimer.labels(any())).thenReturn(operationTimer);

    final BlsArtifactSignerFactory blsArtifactSignerFactory =
        new BlsArtifactSignerFactory(
            configsDirectory,
            metricsSystem,
            hashicorpConnectionFactory,
            interlockKeyProvider,
            yubiHsmOpaqueDataProvider,
            awsSecretsManagerProvider,
            (args) -> new BlsArtifactSigner(args.getKeyPair(), args.getOrigin(), args.getPath()),
            azureKeyVaultFactory);

    signerParser =
        new YamlSignerParser(
            List.of(blsArtifactSignerFactory), YamlMapperFactory.createYamlMapper());

    SignerLoader.clearCache();
  }

  @ParameterizedTest(name = "{index} - Signer created for file name {0}")
  @MethodSource("validMetadataFileNameProvider")
  void signerReturnedForValidMetadataFile(final String fileName) throws IOException {
    final String privateKeyHex = blsKeyPair1.getSecretKey().toBytes().toHexString();
    createFileInConfigsDirectory(fileName, privateKeyHex);

    SignerLoader.load(configsDirectory, signerParser, true);
    final Collection<ArtifactSigner> signerList =
        SignerLoader.load(configsDirectory, signerParser, true).getValues();

    assertThat(signerList.size()).isOne();
    assertThat(signerList.stream().findFirst().orElseThrow().getIdentifier())
        .isEqualTo(blsKeyPair1.getPublicKey().toHexString());
  }

  static Stream<String> validMetadataFileNameProvider() {
    return Stream.of(
        configFileName(blsKeyPair1),
        "prefix_" + configFileName(blsKeyPair1),
        "test.yaml",
        "test.YAML",
        "test.yml",
        "test.YML");
  }

  @Test
  void wrongFileExtensionReturnsEmptySigner() throws IOException {
    final String filename = blsKeyPair1.getPublicKey().toHexString() + ".nothing";
    final String privateKeyHex = blsKeyPair1.getSecretKey().toBytes().toHexString();
    createFileInConfigsDirectory(filename, privateKeyHex);

    final MappedResults<ArtifactSigner> result =
        SignerLoader.load(configsDirectory, signerParser, true);

    assertThat(result.getValues()).isEmpty();
    assertThat(result.getErrorCount()).isZero();
  }

  @Test
  void failedParserReturnsEmptySigner() throws IOException {
    createFileInConfigsDirectory(configFileName(blsKeyPair1), "NOT_A_VALID_KEY");

    final MappedResults<ArtifactSigner> result =
        SignerLoader.load(configsDirectory, signerParser, true);

    assertThat(result.getValues()).isEmpty();
    assertThat(result.getErrorCount()).isOne();
  }

  @Test
  void failedWithDirectoryErrorReturnEmptySigner() throws IOException {
    final Path missingConfigDir = configsDirectory.resolve("idontexist");
    final MappedResults<ArtifactSigner> result =
        SignerLoader.load(missingConfigDir, signerParser, true);

    assertThat(result.getValues()).isEmpty();
    assertThat(result.getErrorCount()).isOne();
  }

  @Test
  void multipleMatchesForSameIdentifierReturnsSameSigners() throws IOException {
    final String filename1 = "1_" + configFileName(blsKeyPair1);
    final String filename2 = "2_" + configFileName(blsKeyPair1);
    final String privateKeyHex = blsKeyPair1.getSecretKey().toBytes().toHexString();
    createFileInConfigsDirectory(filename1, privateKeyHex);
    createFileInConfigsDirectory(filename2, privateKeyHex);

    final MappedResults<ArtifactSigner> result =
        SignerLoader.load(configsDirectory, signerParser, true);

    assertThat(result.getValues()).hasSize(1);
    assertThat(result.getErrorCount()).isZero();
  }

  @Test
  void signerIdentifiersNotReturnedInvalidMetadataFile() throws IOException {
    createEmptyFileInConfigsDirectory(configFileName(blsKeyPair1));
    createEmptyFileInConfigsDirectory(configFileName(blsKeyPair2));
    final MappedResults<ArtifactSigner> result =
        SignerLoader.load(configsDirectory, signerParser, true);

    assertThat(result.getValues()).isEmpty();
    assertThat(result.getErrorCount()).isEqualTo(2);
  }

  @Test
  void signerIdentifiersNotReturnedForHiddenFiles() throws IOException {
    final String privateKeyHex1 = blsKeyPair1.getSecretKey().toBytes().toHexString();
    final String privateKeyHex2 = blsKeyPair2.getSecretKey().toBytes().toHexString();
    final Path hiddenFile =
        createFileInConfigsDirectory(configFileName(blsKeyPair1), privateKeyHex1);
    FileHiddenUtil.makeFileHidden(hiddenFile);
    createFileInConfigsDirectory(configFileName(blsKeyPair2), privateKeyHex2);

    final MappedResults<ArtifactSigner> result =
        SignerLoader.load(configsDirectory, signerParser, true);

    assertThat(result.getValues()).hasSize(1);
    assertThat(result.getValues().stream().findFirst().orElseThrow().getIdentifier())
        .isEqualTo(blsKeyPair2.getPublicKey().toHexString());
    assertThat(result.getErrorCount()).isZero();
  }

  @Test
  void signerIdentifiersReturnedForAllValidMetadataFilesInDirectory() throws IOException {
    final String privateKeyHex1 = blsKeyPair1.getSecretKey().toBytes().toHexString();
    final String privateKeyHex2 = blsKeyPair2.getSecretKey().toBytes().toHexString();
    final String privateKeyHex3 = blsKeyPair3.getSecretKey().toBytes().toHexString();

    createFileInConfigsDirectory(configFileName(blsKeyPair1), privateKeyHex1);
    createFileInConfigsDirectory(configFileName(blsKeyPair2), privateKeyHex2);
    createFileInConfigsDirectory(configFileName(blsKeyPair3), privateKeyHex3);

    MappedResults<ArtifactSigner> result = SignerLoader.load(configsDirectory, signerParser, true);

    assertThat(result.getValues()).hasSize(3);
    assertThat(
            result.getValues().stream()
                .map(ArtifactSigner::getIdentifier)
                .collect(Collectors.toList()))
        .containsOnly(
            blsKeyPair1.getPublicKey().toHexString(),
            blsKeyPair2.getPublicKey().toHexString(),
            blsKeyPair3.getPublicKey().toHexString());
  }

  @Test
  void callingLoadTwiceReturnedSameNumberOfArtifacts() throws IOException {
    final String privateKeyHex1 = blsKeyPair1.getSecretKey().toBytes().toHexString();
    final String privateKeyHex2 = blsKeyPair2.getSecretKey().toBytes().toHexString();
    final String privateKeyHex3 = blsKeyPair3.getSecretKey().toBytes().toHexString();

    createFileInConfigsDirectory(configFileName(blsKeyPair1), privateKeyHex1);
    createFileInConfigsDirectory(configFileName(blsKeyPair2), privateKeyHex2);
    createFileInConfigsDirectory(configFileName(blsKeyPair3), privateKeyHex3);

    final MappedResults<ArtifactSigner> result =
        SignerLoader.load(configsDirectory, signerParser, true);

    assertThat(result.getValues()).hasSize(3);
    assertThat(
            result.getValues().stream()
                .map(ArtifactSigner::getIdentifier)
                .collect(Collectors.toList()))
        .containsOnly(
            blsKeyPair1.getPublicKey().toHexString(),
            blsKeyPair2.getPublicKey().toHexString(),
            blsKeyPair3.getPublicKey().toHexString());

    final MappedResults<ArtifactSigner> reloadedResult =
        SignerLoader.load(configsDirectory, signerParser, true);
    assertThat(reloadedResult.getValues()).hasSize(3);
  }

  @Test
  void callingLoadTwiceWithRemovedFiles() throws IOException {
    final String privateKeyHex1 = blsKeyPair1.getSecretKey().toBytes().toHexString();
    final String privateKeyHex2 = blsKeyPair2.getSecretKey().toBytes().toHexString();
    final String privateKeyHex3 = blsKeyPair3.getSecretKey().toBytes().toHexString();

    createFileInConfigsDirectory(configFileName(blsKeyPair1), privateKeyHex1);
    createFileInConfigsDirectory(configFileName(blsKeyPair2), privateKeyHex2);
    createFileInConfigsDirectory(configFileName(blsKeyPair3), privateKeyHex3);

    final MappedResults<ArtifactSigner> result =
        SignerLoader.load(configsDirectory, signerParser, true);

    assertThat(result.getValues()).hasSize(3);
    assertThat(
            result.getValues().stream()
                .map(ArtifactSigner::getIdentifier)
                .collect(Collectors.toList()))
        .containsOnly(
            blsKeyPair1.getPublicKey().toHexString(),
            blsKeyPair2.getPublicKey().toHexString(),
            blsKeyPair3.getPublicKey().toHexString());

    // remove file
    Files.delete(configsDirectory.resolve(configFileName(blsKeyPair3)));

    final Collection<ArtifactSigner> reloadedArtifactSigner =
        SignerLoader.load(configsDirectory, signerParser, true).getValues();
    assertThat(reloadedArtifactSigner).hasSize(2);
    assertThat(
            reloadedArtifactSigner.stream()
                .map(ArtifactSigner::getIdentifier)
                .collect(Collectors.toList()))
        .containsOnly(
            blsKeyPair1.getPublicKey().toHexString(), blsKeyPair2.getPublicKey().toHexString());
  }

  @Test
  void calculateTimeTakenWithFutureTimeShouldReturnEmpty() {
    final Optional<String> timeTaken =
        SignerLoader.calculateTimeTaken(Instant.now().plus(5, ChronoUnit.MINUTES));
    assertThat(timeTaken).isEmpty();
  }

  @Test
  void calculateTimeTakenWithPastTimeShouldReturnValue() {
    final Optional<String> timeTaken =
        SignerLoader.calculateTimeTaken(Instant.now().minus(5, ChronoUnit.MINUTES));
    assertThat(timeTaken).isNotEmpty();
  }

  private Path createFileInConfigsDirectory(final String fileName, final String privateKeyHex)
      throws IOException {
    final Path file = configsDirectory.resolve(fileName);

    final Map<String, String> unencryptedKeyMetadataFile = new HashMap<>();
    unencryptedKeyMetadataFile.put("type", "file-raw");
    unencryptedKeyMetadataFile.put("privateKey", privateKeyHex);
    final String yamlContent = YAML_MAPPER.writeValueAsString(unencryptedKeyMetadataFile);

    Files.writeString(file, yamlContent);
    assertThat(file).exists();
    return file;
  }

  private static String configFileName(final BLSKeyPair blsKeyPair) {
    return blsKeyPair.getPublicKey().toHexString() + ".yaml";
  }

  private void createEmptyFileInConfigsDirectory(final String filename) throws IOException {
    final File file = configsDirectory.resolve(filename).toFile();
    assertThat(file.createNewFile()).isTrue();
  }
}
