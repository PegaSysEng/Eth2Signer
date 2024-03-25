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
package tech.pegasys.web3signer.tests.signing;

import static io.restassured.http.ContentType.JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.spec.SpecMilestone.DENEB;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSecretKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.web3signer.core.service.http.SigningObjectMapperFactory;
import tech.pegasys.web3signer.core.service.http.handlers.signing.SigningExtensionBody;
import tech.pegasys.web3signer.core.service.http.handlers.signing.SigningExtensionType;
import tech.pegasys.web3signer.dsl.signer.SignerConfigurationBuilder;
import tech.pegasys.web3signer.dsl.utils.MetadataFileHelpers;
import tech.pegasys.web3signer.signing.KeyType;

import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class Eth2GenericSigningAcceptanceTest extends SigningAcceptanceTestBase {

  private static final String PRIVATE_KEY =
      "3ee2224386c82ffea477e2adf28a2929f5c349165a4196158c7f3a2ecca40f35";
  private static final MetadataFileHelpers METADATA_FILE_HELPERS = new MetadataFileHelpers();
  private static final BLSSecretKey KEY =
      BLSSecretKey.fromBytes(Bytes32.fromHexString(PRIVATE_KEY));
  private static final BLSKeyPair KEY_PAIR = new BLSKeyPair(KEY);
  private static final BLSPublicKey PUBLIC_KEY = KEY_PAIR.getPublicKey();
  private static final ObjectMapper JSON_MAPPER = SigningObjectMapperFactory.createObjectMapper();

  @BeforeEach
  void setup() {
    final String configFilename = PUBLIC_KEY.toString().substring(2);
    final Path keyConfigFile = testDirectory.resolve(configFilename + ".yaml");
    METADATA_FILE_HELPERS.createUnencryptedYamlFileAt(keyConfigFile, PRIVATE_KEY, KeyType.BLS);

    setForkEpochsAndStartSigner(
        new SignerConfigurationBuilder()
            .withKeyStoreDirectory(testDirectory)
            .withMode("eth2")
            .withNetwork(Eth2Network.MINIMAL.configName())
            .withGenericSigningExtEnabled(true),
        DENEB);
  }

  @Test
  void extensionSigningData() throws Exception {
    SigningExtensionBody signingExtensionBody =
        new SigningExtensionBody(
            SigningExtensionType.PROOF_OF_VALIDATION,
            "AT",
            String.valueOf(System.currentTimeMillis()),
            PUBLIC_KEY.toString());
    String data = JSON_MAPPER.writeValueAsString(signingExtensionBody);

    final Response response = signer.signGenericData(PUBLIC_KEY.toString(), data, JSON);

    final JsonObject jsonBody = verifyStatusAndGetBody(response);

    final Bytes signature = Bytes.fromHexString(jsonBody.getString("signature"));
    assertThat(
            BLS.verify(
                PUBLIC_KEY,
                signingExtensionBody.signingData(),
                BLSSignature.fromBytesCompressed(signature)))
        .isTrue();

    final SigningExtensionBody message =
        jsonBody.getJsonObject("message").mapTo(SigningExtensionBody.class);
    assertThat(message).isEqualTo(signingExtensionBody);
  }

  @Test
  void invalidIdentifierCausesNotFound() throws Exception {
    SigningExtensionBody signingExtensionBody =
            new SigningExtensionBody(
                    SigningExtensionType.PROOF_OF_VALIDATION,
                    "AT",
                    String.valueOf(System.currentTimeMillis()),
                    PUBLIC_KEY.toString());
    String data = JSON_MAPPER.writeValueAsString(signingExtensionBody);
    final Response response = signer.signGenericData("0x1234", data, JSON);
    response.then().statusCode(404);
  }

  @ParameterizedTest(name="{index} - Testing Invalid Body: {0}")
  @ValueSource(strings = {"", "invalid", "{}", "{\"data\": \"invalid\"}"})
  void invalidBodyCausesBadRequestStatusCode(final String data) {
    final Response response = signer.signGenericData(PUBLIC_KEY.toString(), data, JSON);
    response.then().statusCode(400);
  }

  private JsonObject verifyStatusAndGetBody(final Response response) {
    response.then().statusCode(200).contentType(JSON);
    return new JsonObject(response.body().print());
  }
}
