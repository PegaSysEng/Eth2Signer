/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.eth2signer.tests.signing;

import static io.restassured.RestAssured.given;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.web3j.crypto.Sign.publicKeyFromPrivate;
import static org.web3j.crypto.Sign.signedMessageToKey;
import static tech.pegasys.signers.secp256k1.MultiKeyTomlFileUtil.createAzureTomlFileAt;

import tech.pegasys.eth2signer.dsl.signer.SignerConfigurationBuilder;
import tech.pegasys.eth2signer.tests.AcceptanceTestBase;

import java.math.BigInteger;
import java.nio.file.Path;
import java.security.SignatureException;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;
import org.junit.jupiter.api.io.TempDir;
import org.web3j.crypto.Sign.SignatureData;

public class SecpSigningAcceptanceTest extends AcceptanceTestBase {
  private static final byte[] DATA_TO_SIGN = "42".getBytes(UTF_8);
  private static final String PRIVATE_KEY =
      "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63";

  private static final String SIGN_ENDPOINT = "/signer/sign/{publicKey}";

  static final String FILENAME = "fe3b557e8fb62b89f4916b721be55ceb828dbd73";

  @Test
  @EnabledIfEnvironmentVariables({
    @EnabledIfEnvironmentVariable(named = "AZURE_CLIENT_ID", matches = ".*"),
    @EnabledIfEnvironmentVariable(named = "AZURE_CLIENT_SECRET", matches = ".*"),
    @EnabledIfEnvironmentVariable(named = "AZURE_KEY_VAULT_NAME", matches = ".*")
  })
  @EnabledIfEnvironmentVariable(named = "AZURE_KEY_VAULT_NAME", matches = ".*")
  public void signDataWithKeyInAzure(@TempDir Path tomlDirectory) {
    final String clientId = System.getenv("AZURE_CLIENT_ID");
    final String clientSecret = System.getenv("AZURE_CLIENT_SECRET");
    final String keyVaultName = System.getenv("AZURE_KEY_VAULT_NAME");
    createAzureTomlFileAt(
        tomlDirectory.resolve("arbitrary_prefix" + FILENAME + ".toml"),
        clientId,
        clientSecret,
        keyVaultName);

    final SignerConfigurationBuilder builder = new SignerConfigurationBuilder();
    builder.withKeyStoreDirectory(tomlDirectory);
    startSigner(builder.build());

    final Response response =
        given()
            .baseUri(signer.getUrl())
            .filter(getOpenApiValidationFilter())
            .contentType(ContentType.JSON)
            .pathParam("publicKey", FILENAME)
            .body(new JsonObject().put("data", Bytes.wrap(DATA_TO_SIGN).toHexString()).toString())
            .post(SIGN_ENDPOINT);

    assertThat(response.contentType()).startsWith(ContentType.TEXT.toString());
    assertThat(response.statusCode()).isEqualTo(200);
    verifySignature(Bytes.fromHexString(response.getBody().print()));
  }

  void verifySignature(final Bytes signature) {
    final BigInteger privateKey = new BigInteger(1, Bytes.fromHexString(PRIVATE_KEY).toArray());
    final BigInteger expectedPublicKey = publicKeyFromPrivate(privateKey);

    final byte[] r = signature.slice(0, 32).toArray();
    final byte[] s = signature.slice(32, 32).toArray();
    final byte[] v = signature.slice(64).toArray();
    final BigInteger messagePublicKey = recoverPublicKey(new SignatureData(v, r, s));
    assertThat(messagePublicKey).isEqualTo(expectedPublicKey);
  }

  private BigInteger recoverPublicKey(final SignatureData signature) {
    try {
      return signedMessageToKey(DATA_TO_SIGN, signature);
    } catch (SignatureException e) {
      throw new IllegalStateException("signature cannot be recovered", e);
    }
  }
}
