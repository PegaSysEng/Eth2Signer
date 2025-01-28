/*
 * Copyright 2024 ConsenSys AG.
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
package tech.pegasys.web3signer.core.service.http.handlers.commitboost;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSecretKey;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.web3signer.core.service.http.handlers.commitboost.json.ProxyDelegation;
import tech.pegasys.web3signer.core.service.http.handlers.commitboost.json.ProxyKeySignatureScheme;
import tech.pegasys.web3signer.signing.BlsArtifactSignature;
import tech.pegasys.web3signer.signing.BlsArtifactSigner;
import tech.pegasys.web3signer.signing.secp256k1.EthPublicKeyUtils;

import java.security.interfaces.ECPublicKey;
import java.util.HashMap;
import java.util.Map;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.web3j.crypto.ECKeyPair;
import org.web3j.utils.Numeric;

class SigningRootGeneratorTest {
  private static final Map<Eth2Network, Bytes32> DOMAIN_MAP = new HashMap<>();
  private static final Map<Eth2Network, Bytes32> BLS_PROXY_ROOT_MAP = new HashMap<>();
  private static final Map<Eth2Network, Bytes32> SECP_PROXY_ROOT_MAP = new HashMap<>();

  private static final Map<Eth2Network, String> BLS_PROXY_MESSAGE_SIGNATURE_MAP = new HashMap<>();
  private static final Map<Eth2Network, String> SECP_PROXY_MESSAGE_SIGNATURE_MAP = new HashMap<>();

  private static final String BLS_DELEGATOR_PRIVATE_KEY =
      "3ee2224386c82ffea477e2adf28a2929f5c349165a4196158c7f3a2ecca40f35";
  private static final String BLS_PROXY_PRIVATE_KEY =
      "32ae313afff2daa2ef7005a7f834bdf291855608fe82c24d30be6ac2017093a8";
  private static final String SECP_PROXY_PRIVATE_KEY =
      "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63";

  private static final BLSKeyPair DELEGATOR_KEY_PAIR =
      new BLSKeyPair(BLSSecretKey.fromBytes(Bytes32.fromHexString(BLS_DELEGATOR_PRIVATE_KEY)));
  private static final BLSPublicKey DELEGATOR_PUB_KEY = DELEGATOR_KEY_PAIR.getPublicKey();
  private static final BLSPublicKey BLS_PROXY_PUB_KEY =
      new BLSKeyPair(BLSSecretKey.fromBytes(Bytes32.fromHexString(BLS_PROXY_PRIVATE_KEY)))
          .getPublicKey();
  private static final ECKeyPair SECP_PROXY_KEY_PAIR =
      ECKeyPair.create(Numeric.toBigInt(Bytes.fromHexString(SECP_PROXY_PRIVATE_KEY).toArray()));
  private static final ECPublicKey SECP_PROXY_EC_PUB_KEY =
      EthPublicKeyUtils.web3JPublicKeyToECPublicKey(SECP_PROXY_KEY_PAIR.getPublicKey());
  private static final Bytes SECP_PROXY_PUB_KEY_ENC =
      Bytes.fromHexString(EthPublicKeyUtils.toHexStringCompressed(SECP_PROXY_EC_PUB_KEY));

  @BeforeAll
  static void initExpectedSigningRoots() {
    // precalculated values from Commit Boost client implementation
    DOMAIN_MAP.put(
        Eth2Network.MAINNET,
        Bytes32.fromHexString(
            "0x6d6d6f43f5a5fd42d16a20302798ef6ed309979b43003d2320d9f0e8ea9831a9"));
    DOMAIN_MAP.put(
        Eth2Network.HOLESKY,
        Bytes32.fromHexString(
            "0x6d6d6f435b83a23759c560b2d0c64576e1dcfc34ea94c4988f3e0d9f77f05387"));
    DOMAIN_MAP.put(
        Eth2Network.SEPOLIA,
        Bytes32.fromHexString(
            "0x6d6d6f43d3010778cd08ee514b08fe67b6c503b510987a4ce43f42306d97c67c"));

    BLS_PROXY_ROOT_MAP.put(
        Eth2Network.MAINNET,
        Bytes32.fromHexString(
            "0x36700803956402c24e232e5da8d7dda12796ba96e49177f37daab87dd852f0cd"));
    BLS_PROXY_ROOT_MAP.put(
        Eth2Network.HOLESKY,
        Bytes32.fromHexString(
            "0xdb1b20106a8955ddb47eb2c8c2fe602af8801e61f682f068fc968c65644e45b6"));
    BLS_PROXY_ROOT_MAP.put(
        Eth2Network.SEPOLIA,
        Bytes32.fromHexString(
            "0x99615a149344fc1beffc2085ae98b676bff384b92b45dd28bc1f62127c41505e"));

    SECP_PROXY_ROOT_MAP.put(
        Eth2Network.MAINNET,
        Bytes32.fromHexString(
            "0x419a4f6b748659b3ac4fc3534f3767fffe78127d210af0b2e1c1c8e7b345cf64"));
    SECP_PROXY_ROOT_MAP.put(
        Eth2Network.HOLESKY,
        Bytes32.fromHexString(
            "0xcc0cd2144f8b1c775eda156524e0a26ab794fdf39121ec902e51a4aff477fb74"));
    SECP_PROXY_ROOT_MAP.put(
        Eth2Network.SEPOLIA,
        Bytes32.fromHexString(
            "0xcc773c9f0ca058178f5b65b9c5fe9857c39e667f64f4b09a4e75731ac56fee41"));

    // precalculated Proxy Message Signature values from Commit Boost client implementation
    BLS_PROXY_MESSAGE_SIGNATURE_MAP.put(
        Eth2Network.MAINNET,
        "0x99c739103b950777727a2e4da588de9017adbcae2ccb50ec1887ba0148a70055e55765441974e0a63f635267600b7bc00b2f616a8427f7d27ec7c1b6fd8520049294a9bbc07bea46aaeff59a254bf793fe57e0e67c41b457816839ff3da13f2e");
    BLS_PROXY_MESSAGE_SIGNATURE_MAP.put(
        Eth2Network.HOLESKY,
        "0xa13f26d6b77b17e385ce3411e7c0fba4b2bed81da5e14a50b419d3a2c2ea3c54a5b70c7c93b99bffa99781041b39335609c096639b7f742a0246204697ba67497ace0853a7f9d982356d78fdb99e98134302444596faec857570d9e5d578999c");
    BLS_PROXY_MESSAGE_SIGNATURE_MAP.put(
        Eth2Network.SEPOLIA,
        "0x8fd1736684a3eee3a4deea5785d41ddc7a96faf1dd4ff9778fb58db465a206571fff0ca9e55cd3654a28cfc0b0e065411633deb9cb8b9263f7189b73c013a61d6518a5aa2b40066a230a5cb1705bd9a80894badc7bfc65e3e2dd459e9fa9d7fc");

    SECP_PROXY_MESSAGE_SIGNATURE_MAP.put(
        Eth2Network.MAINNET,
        "0x8cd715641bb61bca8ba50a5f7e5faf06da1aedb074d59b9fce0ab69e8840501975f5c0008de6625b7d343b5bd362e3220ef5be03c1b32842cddcd5073c3d25e22e9746144b8ff2361391af1b681520c111b5ea69f11097991cccb43b9b6fb0e9");
    SECP_PROXY_MESSAGE_SIGNATURE_MAP.put(
        Eth2Network.HOLESKY,
        "0x8f3c546da13ad082e2818b75b4662e4f28d38e193a5c4e24231233df454f26f15c8ab1fd4cc4772321ab7a4ac16acaf300a78c5fc1ac96c4009413ad7f9c6c5cd99cb9d7c92120177d828bd8f6e77b9ffb93c37f6f6b3cb264969fa4fea179d5");
    SECP_PROXY_MESSAGE_SIGNATURE_MAP.put(
        Eth2Network.SEPOLIA,
        "0x90000272c0a751852d28b953c9d30df31fd9eeb846fb3b575c8fdeee0325ee5dcc6f91bdf3d5d0f0814b707d088ab3af047977464cbe3b9eded66202c2ae70fbe478860cbcf4fc31d10a81aac7c682a6e422686a7cfa7cab272903f9cabf73bb");
  }

  @ParameterizedTest
  @EnumSource(names = {"MAINNET", "HOLESKY", "SEPOLIA"})
  void validComputedDomain(final Eth2Network network) {
    final Spec spec = getSpec(network);
    final SigningRootGenerator signingRootGenerator = new SigningRootGenerator(spec);
    assertThat(signingRootGenerator.getDomain()).isEqualTo(DOMAIN_MAP.get(network));
  }

  @ParameterizedTest
  @EnumSource(names = {"MAINNET", "HOLESKY", "SEPOLIA"})
  void computeSigningRootForBLSProxyKey(final Eth2Network network) {
    final Spec spec = getSpec(network);
    final SigningRootGenerator signingRootGenerator = new SigningRootGenerator(spec);

    final ProxyDelegation proxyDelegation =
        new ProxyDelegation(DELEGATOR_PUB_KEY.toHexString(), BLS_PROXY_PUB_KEY.toHexString());
    final Bytes signingRoot =
        signingRootGenerator.computeSigningRoot(
            proxyDelegation.toMerkleizable(ProxyKeySignatureScheme.BLS).hashTreeRoot());

    assertThat(signingRoot).isEqualTo(BLS_PROXY_ROOT_MAP.get(network));

    // verify BLS Signature matching Commit Boost client implementation as well
    final BlsArtifactSigner artifactSigner = new BlsArtifactSigner(DELEGATOR_KEY_PAIR, null);
    final String signature = artifactSigner.sign(signingRoot).asHex();

    assertThat(signature).isEqualTo(BLS_PROXY_MESSAGE_SIGNATURE_MAP.get(network));
  }

  @ParameterizedTest
  @EnumSource(names = {"MAINNET", "HOLESKY", "SEPOLIA"})
  void computeSigningRootforSECPProxyKey(final Eth2Network network) {
    final Spec spec = getSpec(network);
    final SigningRootGenerator signingRootGenerator = new SigningRootGenerator(spec);

    final ProxyDelegation proxyDelegation =
        new ProxyDelegation(DELEGATOR_PUB_KEY.toHexString(), SECP_PROXY_PUB_KEY_ENC.toHexString());

    final Bytes signingRoot =
        signingRootGenerator.computeSigningRoot(
            proxyDelegation.toMerkleizable(ProxyKeySignatureScheme.ECDSA).hashTreeRoot());

    assertThat(signingRoot).isEqualTo(SECP_PROXY_ROOT_MAP.get(network));

    // verify BLS Signature matching Commit Boost client implementation as well
    final BlsArtifactSigner artifactSigner = new BlsArtifactSigner(DELEGATOR_KEY_PAIR, null);
    BlsArtifactSignature blsArtifactSignature = artifactSigner.sign(signingRoot);
    String signature = blsArtifactSignature.asHex();

    assertThat(signature).isEqualTo(SECP_PROXY_MESSAGE_SIGNATURE_MAP.get(network));
  }

  private static Spec getSpec(final Eth2Network network) {
    final Eth2NetworkConfiguration.Builder builder = Eth2NetworkConfiguration.builder();
    return builder.applyNetworkDefaults(network).build().getSpec();
  }
}
