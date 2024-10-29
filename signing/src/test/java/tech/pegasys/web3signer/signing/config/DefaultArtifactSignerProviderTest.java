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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.web3signer.KeystoreUtil;
import tech.pegasys.web3signer.signing.ArtifactSigner;
import tech.pegasys.web3signer.signing.ArtifactSignerProvider;
import tech.pegasys.web3signer.signing.KeyType;
import tech.pegasys.web3signer.signing.secp256k1.EthPublicKeyUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.WalletUtils;
import org.web3j.crypto.exception.CipherException;

class DefaultArtifactSignerProviderTest {

  private static final String PUBLIC_KEY1 =
      "0x989d34725a2bfc3f15105f3f5fc8741f436c25ee1ee4f948e425d6bcb8c56bce6e06c269635b7e985a7ffa639e2409bf";
  private static final String PUBLIC_KEY2 =
      "0xa99a76ed7796f7be22d5b7e85deeb7c5677e88e511e0b337618f8c4eb61349b4bf2d153f649f7b53359fe8b94a38e44c";

  private ArtifactSignerProvider signerProvider;

  @TempDir private Path commitBoostKeystoresPath;

  @TempDir private Path commitBoostPasswordDir;

  @AfterEach
  void cleanup() {
    if (signerProvider != null) {
      signerProvider.close();
    }
  }

  @Test
  void signerReturnedForMatchingIdentifier() {
    final ArtifactSigner mockSigner = mock(ArtifactSigner.class);
    when(mockSigner.getIdentifier()).thenReturn(PUBLIC_KEY1);

    signerProvider = new DefaultArtifactSignerProvider(() -> List.of(mockSigner), Optional.empty());
    assertThatCode(() -> signerProvider.load().get()).doesNotThrowAnyException();

    final Optional<ArtifactSigner> signer = signerProvider.getSigner(PUBLIC_KEY1);
    assertThat(signer).isNotEmpty();
    assertThat(signerProvider.availableIdentifiers()).containsOnly(PUBLIC_KEY1);
  }

  @Test
  void signerProviderOnlyHasSingleEntryIfPassedInListHasMultipleMatchingSigners() {
    final ArtifactSigner mockSigner1 = mock(ArtifactSigner.class);
    when(mockSigner1.getIdentifier()).thenReturn(PUBLIC_KEY1);
    final ArtifactSigner mockSigner2 = mock(ArtifactSigner.class);
    when(mockSigner2.getIdentifier()).thenReturn(PUBLIC_KEY1);

    signerProvider =
        new DefaultArtifactSignerProvider(
            () -> List.of(mockSigner1, mockSigner2), Optional.empty());
    assertThatCode(() -> signerProvider.load().get()).doesNotThrowAnyException();

    assertThat(signerProvider.availableIdentifiers()).hasSize(1);
    assertThat(signerProvider.availableIdentifiers()).containsOnly(PUBLIC_KEY1);
  }

  @Test
  void signerProviderCanMapInTwoSigners() {
    final ArtifactSigner mockSigner1 = mock(ArtifactSigner.class);
    when(mockSigner1.getIdentifier()).thenReturn(PUBLIC_KEY1);
    final ArtifactSigner mockSigner2 = mock(ArtifactSigner.class);
    when(mockSigner2.getIdentifier()).thenReturn(PUBLIC_KEY2);

    signerProvider =
        new DefaultArtifactSignerProvider(
            () -> List.of(mockSigner1, mockSigner2), Optional.empty());
    assertThatCode(() -> signerProvider.load().get()).doesNotThrowAnyException();
    assertThat(signerProvider.availableIdentifiers()).hasSize(2);
    assertThat(signerProvider.availableIdentifiers()).containsOnly(PUBLIC_KEY1, PUBLIC_KEY2);
  }

  @Test
  void proxySignersAreLoadedCorrectly() throws IOException {
    final SecureRandom secureRandom = new SecureRandom();

    // create random proxy signers
    final CommitBoostParameters commitBoostParameters =
        new TestCommitBoostParameters(commitBoostKeystoresPath, commitBoostPasswordDir);

    assertThat(commitBoostParameters.getProxyKeystoresPasswordFile())
        .exists()
        .hasFileName("password.txt");

    // create random BLS key pairs as proxy keys for public key1 and public key2
    final List<BLSKeyPair> key1ProxyKeyPairs = randomBLSV4Keystores(secureRandom, PUBLIC_KEY1);
    final List<BLSKeyPair> key2ProxyKeyPairs = randomBLSV4Keystores(secureRandom, PUBLIC_KEY2);

    // create random secp key pairs as proxy keys for public key1 and public key2
    final List<ECKeyPair> key1SecpKeyPairs = randomSecpV3Keystores(secureRandom, PUBLIC_KEY1);
    final List<ECKeyPair> key2SecpKeyPairs = randomSecpV3Keystores(secureRandom, PUBLIC_KEY2);

    // set up mock signers
    final ArtifactSigner mockSigner1 = mock(ArtifactSigner.class);
    when(mockSigner1.getIdentifier()).thenReturn(PUBLIC_KEY1);
    final ArtifactSigner mockSigner2 = mock(ArtifactSigner.class);
    when(mockSigner2.getIdentifier()).thenReturn(PUBLIC_KEY2);

    signerProvider =
        new DefaultArtifactSignerProvider(
            () -> List.of(mockSigner1, mockSigner2), Optional.of(commitBoostParameters));

    // methods under test
    assertThatCode(() -> signerProvider.load().get()).doesNotThrowAnyException();

    // assert that the proxy keys are loaded correctly
    final Map<KeyType, List<String>> key1ProxyPublicKeys =
        signerProvider.getProxyIdentifiers(PUBLIC_KEY1);

    assertThat(key1ProxyPublicKeys.get(KeyType.BLS))
        .containsExactlyInAnyOrder(getPublicKeysArray(key1ProxyKeyPairs));
    assertThat(key1ProxyPublicKeys.get(KeyType.SECP256K1))
        .containsExactlyInAnyOrder(getCompressedSecpPublicKeysArray(key1SecpKeyPairs));

    final Map<KeyType, List<String>> key2ProxyPublicKeys =
        signerProvider.getProxyIdentifiers(PUBLIC_KEY2);

    assertThat(key2ProxyPublicKeys.get(KeyType.BLS))
        .containsExactlyInAnyOrder(getPublicKeysArray(key2ProxyKeyPairs));
    assertThat(key2ProxyPublicKeys.get(KeyType.SECP256K1))
        .containsExactlyInAnyOrder(getCompressedSecpPublicKeysArray(key2SecpKeyPairs));
  }

  @Test
  void emptyProxySignersAreLoadedSuccessfully() {
    // enable commit boost without existing proxy keys
    final CommitBoostParameters commitBoostParameters =
        new TestCommitBoostParameters(commitBoostKeystoresPath, commitBoostPasswordDir);

    // set up mock signers
    final ArtifactSigner mockSigner1 = mock(ArtifactSigner.class);
    when(mockSigner1.getIdentifier()).thenReturn(PUBLIC_KEY1);
    final ArtifactSigner mockSigner2 = mock(ArtifactSigner.class);
    when(mockSigner2.getIdentifier()).thenReturn(PUBLIC_KEY2);

    signerProvider =
        new DefaultArtifactSignerProvider(
            () -> List.of(mockSigner1, mockSigner2), Optional.of(commitBoostParameters));

    // methods under test
    assertThatCode(() -> signerProvider.load().get()).doesNotThrowAnyException();

    for (String identifier : List.of(PUBLIC_KEY1, PUBLIC_KEY2)) {
      final Map<KeyType, List<String>> keyProxyPublicKeys =
          signerProvider.getProxyIdentifiers(identifier);
      assertThat(keyProxyPublicKeys).isEmpty();
    }
  }

  private List<BLSKeyPair> randomBLSV4Keystores(SecureRandom secureRandom, String identifier)
      throws IOException {
    final Path v4Dir =
        Files.createDirectories(
            commitBoostKeystoresPath.resolve(identifier).resolve(KeyType.BLS.name()));

    return IntStream.range(0, 4)
        .mapToObj(
            i -> {
              final BLSKeyPair blsKeyPair = BLSKeyPair.random(secureRandom);
              KeystoreUtil.createKeystoreFile(blsKeyPair, v4Dir, "password");
              return blsKeyPair;
            })
        .toList();
  }

  private List<ECKeyPair> randomSecpV3Keystores(
      final SecureRandom secureRandom, final String identifier) throws IOException {
    final Path v3Dir =
        Files.createDirectories(
            commitBoostKeystoresPath.resolve(identifier).resolve(KeyType.SECP256K1.name()));
    return IntStream.range(0, 4)
        .mapToObj(
            i -> {
              try {
                final KeyPair secp256k1KeyPair =
                    EthPublicKeyUtils.createSecp256k1KeyPair(secureRandom);
                final ECKeyPair ecKeyPair = ECKeyPair.create(secp256k1KeyPair);

                WalletUtils.generateWalletFile("password", ecKeyPair, v3Dir.toFile(), false);
                return ecKeyPair;
              } catch (GeneralSecurityException | CipherException | IOException e) {
                throw new RuntimeException(e);
              }
            })
        .toList();
  }

  private static String[] getPublicKeysArray(final List<BLSKeyPair> blsKeyPairs) {
    return blsKeyPairs.stream()
        .map(keyPair -> keyPair.getPublicKey().toString())
        .toList()
        .toArray(String[]::new);
  }

  private static String[] getCompressedSecpPublicKeysArray(final List<ECKeyPair> ecKeyPairs) {
    // compressed public keys
    return ecKeyPairs.stream()
        .map(
            keyPair ->
                EthPublicKeyUtils.getEncoded(
                        EthPublicKeyUtils.bigIntegerToECPublicKey(keyPair.getPublicKey()),
                        true,
                        false)
                    .toHexString())
        .toList()
        .toArray(String[]::new);
  }

  private static class TestCommitBoostParameters implements CommitBoostParameters {
    private final Path keystorePath;
    private final Path passwordFile;

    public TestCommitBoostParameters(final Path keystorePath, final Path passwordDir) {
      this.keystorePath = keystorePath;
      // create password file in passwordDir
      this.passwordFile = passwordDir.resolve("password.txt");
      // write text to password file
      try {
        Files.writeString(passwordFile, "password");
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    @Override
    public boolean isEnabled() {
      return true;
    }

    @Override
    public Path getProxyKeystoresPath() {
      return keystorePath;
    }

    @Override
    public Path getProxyKeystoresPasswordFile() {
      return passwordFile;
    }

    @Override
    public Bytes32 getGenesisValidatorsRoot() {
      return Bytes32.ZERO;
    }
  }
}
