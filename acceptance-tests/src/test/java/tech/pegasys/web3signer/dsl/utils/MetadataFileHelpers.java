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
package tech.pegasys.web3signer.dsl.utils;

import static org.assertj.core.api.AssertionsForClassTypes.fail;
import static tech.pegasys.signers.bls.keystore.model.Pbkdf2PseudoRandomFunction.HMAC_SHA256;

import tech.pegasys.signers.bls.keystore.KeyStore;
import tech.pegasys.signers.bls.keystore.KeyStoreLoader;
import tech.pegasys.signers.bls.keystore.model.Cipher;
import tech.pegasys.signers.bls.keystore.model.CipherFunction;
import tech.pegasys.signers.bls.keystore.model.KdfFunction;
import tech.pegasys.signers.bls.keystore.model.KdfParam;
import tech.pegasys.signers.bls.keystore.model.KeyStoreData;
import tech.pegasys.signers.bls.keystore.model.Pbkdf2Param;
import tech.pegasys.signers.bls.keystore.model.SCryptParam;
import tech.pegasys.signers.hashicorp.dsl.certificates.CertificateHelpers;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.web3signer.core.signing.KeyType;
import tech.pegasys.web3signer.dsl.HashicorpSigningParams;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class MetadataFileHelpers {
  private static final ObjectMapper YAML_OBJECT_MAPPER = new ObjectMapper(new YAMLFactory());
  private static final Bytes SALT =
      Bytes.fromHexString("0x9ac471d9d421bc06d9aefe2b46cf96d11829c51e36ed0b116132be57a9f8c22b");
  private static final Bytes IV = Bytes.fromHexString("0xcca2c67ec95a1dd13edd986fea372789");

  public void createUnencryptedYamlFileAt(
      final Path metadataFilePath, final String privateKey, final KeyType keyType) {
    final Map<String, String> signingMetadata = new HashMap<>();
    signingMetadata.put("type", "file-raw");
    signingMetadata.put("privateKey", privateKey);
    signingMetadata.put("keyType", keyType.name());

    createYamlFile(metadataFilePath, signingMetadata);
  }

  public void createRandomUnencryptedBlsKeys(final Path directory, final int numberOfKeys) {
    final SecureRandom secureRandom = new SecureRandom();
    final MetadataFileHelpers metadataFileHelpers = new MetadataFileHelpers();
    for (int i = 0; i < numberOfKeys; i++) {
      final BLSKeyPair keyPair = BLSKeyPair.random(secureRandom);
      final String privateKey = keyPair.getSecretKey().toBytes().toHexString();
      final Path filename = directory.resolve(keyPair.getPublicKey().toString() + ".yaml");
      metadataFileHelpers.createUnencryptedYamlFileAt(filename, privateKey, KeyType.BLS);
    }
  }

  public void createKeyStoreYamlFileAt(
      final Path metadataFilePath, final BLSKeyPair keyPair, final KdfFunction kdfFunctionType) {
    final Bytes32 privateKeyBytes = keyPair.getSecretKey().toBytes();

    final String password = "password";

    final Path keystoreFile =
        metadataFilePath.getParent().resolve(keyPair.getPublicKey().toString() + ".json");
    createKeyStoreFile(
        keystoreFile,
        password,
        privateKeyBytes,
        keyPair.getPublicKey().toBytesCompressed(),
        kdfFunctionType);

    createKeyStoreYamlFileAt(metadataFilePath, keystoreFile, password, KeyType.BLS);
  }

  public void createKeyStoreYamlFileAt(
      final Path metadataFilePath,
      final Path keystoreFile,
      final String password,
      final KeyType keyType) {
    final String filename = metadataFilePath.getFileName().toString();
    final String passwordFilename = filename + ".password";
    final Path passwordFile = metadataFilePath.getParent().resolve(passwordFilename);
    createPasswordFile(passwordFile, password);

    final Map<String, String> signingMetadata = new HashMap<>();
    signingMetadata.put("type", "file-keystore");
    signingMetadata.put("keystoreFile", keystoreFile.toString());
    signingMetadata.put("keystorePasswordFile", passwordFile.toString());
    signingMetadata.put("keyType", keyType.name());
    createYamlFile(metadataFilePath, signingMetadata);
  }

  public void createHashicorpYamlFileAt(
      final Path metadataFilePath, final HashicorpSigningParams node) {
    try {
      final Map<String, String> signingMetadata = new HashMap<>();

      final boolean tlsEnabled = node.getServerCertificate().isPresent();

      signingMetadata.put("type", "hashicorp");
      signingMetadata.put("serverHost", node.getHost());
      signingMetadata.put("serverPort", Integer.toString(node.getPort()));
      signingMetadata.put("timeout", "10000");
      signingMetadata.put("tlsEnabled", Boolean.toString(tlsEnabled));
      if (tlsEnabled) {
        final Path parentDir = metadataFilePath.getParent();
        final Path knownServerPath =
            CertificateHelpers.createFingerprintFile(
                parentDir, node.getServerCertificate().get(), Optional.of(node.getPort()));

        signingMetadata.put("tlsKnownServersPath", knownServerPath.toString());
      }
      signingMetadata.put("keyPath", node.getSecretHttpPath());
      signingMetadata.put("keyName", node.getSecretName());
      signingMetadata.put("token", node.getVaultToken());
      signingMetadata.put("keyType", node.getKeyType().toString());

      createYamlFile(metadataFilePath, signingMetadata);
    } catch (final Exception e) {
      throw new RuntimeException("Unable to construct hashicorp yaml file", e);
    }
  }

  public void createAzureYamlFileAt(
      final Path metadataFilePath,
      final String clientId,
      final String clientSecret,
      final String tenantId,
      final String keyVaultName,
      final String secretName) {
    try {
      final Map<String, String> signingMetadata = new HashMap<>();

      signingMetadata.put("type", "azure-secret");
      signingMetadata.put("clientId", clientId);
      signingMetadata.put("clientSecret", clientSecret);
      signingMetadata.put("tenantId", tenantId);
      signingMetadata.put("vaultName", keyVaultName);
      signingMetadata.put("secretName", secretName);

      createYamlFile(metadataFilePath, signingMetadata);
    } catch (final Exception e) {
      throw new RuntimeException("Unable to construct azure yaml file", e);
    }
  }

  public void createAzureKeyYamlFileAt(
      final Path metadataFilePath,
      final String clientId,
      final String clientSecret,
      final String keyVaultName,
      final String tenantId) {
    try {
      final Map<String, String> signingMetadata = new HashMap<>();
      signingMetadata.put("type", "azure-key");
      signingMetadata.put("vaultName", keyVaultName);
      signingMetadata.put("keyName", "TestKey2");
      signingMetadata.put("clientId", clientId);
      signingMetadata.put("clientSecret", clientSecret);
      signingMetadata.put("tenantId", tenantId);
      createYamlFile(metadataFilePath, signingMetadata);
    } catch (final Exception e) {
      throw new RuntimeException("Unable to construct hashicorp yaml file", e);
    }
  }

  public void createInterlockYamlFileAt(
      final Path metadataFilePath,
      final Path knownServersFile,
      final String keyPath,
      final KeyType keyType) {
    // these are default credentials of Interlock on USB Armory
    final Map<String, String> yaml = new HashMap<>();
    yaml.put("type", "interlock");
    yaml.put("interlockUrl", "https://10.0.0.1");
    yaml.put("knownServersFile", knownServersFile.toString());
    yaml.put("volume", "armory");
    yaml.put("password", "usbarmory");
    yaml.put("keyPath", keyPath);
    yaml.put("keyType", keyType.name());

    createYamlFile(metadataFilePath, yaml);
  }

  public void createYubihsmYamlFileAt(
      final Path metadataFilePath,
      final String pkcs11ModulePath,
      final String connectorUrl,
      final String additionalInitConfig,
      final short authId,
      final String password,
      final int opaqueDataId,
      final KeyType keyType) {
    final Map<String, Serializable> yaml = new HashMap<>();
    yaml.put("type", "yubihsm");
    yaml.put("pkcs11ModulePath", pkcs11ModulePath);
    yaml.put("connectorUrl", connectorUrl);
    yaml.put("additionalInitConfig", additionalInitConfig);
    yaml.put("authId", authId);
    yaml.put("password", password);
    yaml.put("opaqueDataId", opaqueDataId);
    yaml.put("keyType", keyType.name());

    createYamlFile(metadataFilePath, yaml);
  }

  private void createPasswordFile(final Path passwordFilePath, final String password) {
    try {
      Files.writeString(passwordFilePath, password);
    } catch (IOException e) {
      fail("Unable to create password file", e);
    }
  }

  private void createKeyStoreFile(
      final Path keyStoreFilePath,
      final String password,
      final Bytes privateKey,
      final Bytes publicKey,
      final KdfFunction kdfFunctionType) {
    final KdfParam kdfParam =
        kdfFunctionType == KdfFunction.SCRYPT
            ? new SCryptParam(32, SALT)
            : new Pbkdf2Param(32, 262144, HMAC_SHA256, SALT);
    final Cipher cipher = new Cipher(CipherFunction.AES_128_CTR, IV);
    final KeyStoreData keyStoreData =
        KeyStore.encrypt(privateKey, publicKey, password, "", kdfParam, cipher);
    try {
      KeyStoreLoader.saveToFile(keyStoreFilePath, keyStoreData);
    } catch (IOException e) {
      fail("Unable to create keystore file", e);
    }
  }

  private void createYamlFile(
      final Path filePath, final Map<String, ? extends Serializable> signingMetadata) {
    try {
      YAML_OBJECT_MAPPER.writeValue(filePath.toFile(), signingMetadata);
    } catch (final IOException e) {
      fail("Unable to create metadata file.", e);
    }
  }
}
