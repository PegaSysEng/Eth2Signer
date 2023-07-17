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
package tech.pegasys.web3signer.dsl.signer;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.web3signer.dsl.tls.TlsClientHelper.createRequestSpecification;
import static tech.pegasys.web3signer.dsl.utils.WaitUtils.waitFor;
import static tech.pegasys.web3signer.signing.KeyType.BLS;
import static tech.pegasys.web3signer.tests.AcceptanceTestBase.JSON_RPC_PATH;

import tech.pegasys.web3signer.core.service.http.SigningObjectMapperFactory;
import tech.pegasys.web3signer.core.service.http.handlers.signing.eth2.Eth2SigningRequestBody;
import tech.pegasys.web3signer.dsl.Accounts;
import tech.pegasys.web3signer.dsl.Eth;
import tech.pegasys.web3signer.dsl.PublicContracts;
import tech.pegasys.web3signer.dsl.Transactions;
import tech.pegasys.web3signer.dsl.lotus.FilecoinJsonRpcEndpoint;
import tech.pegasys.web3signer.dsl.signer.runner.Web3SignerRunner;
import tech.pegasys.web3signer.dsl.tls.ClientTlsConfig;
import tech.pegasys.web3signer.signing.KeyType;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import io.restassured.common.mapper.TypeRef;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Ethereum;
import org.web3j.protocol.core.JsonRpc2_0Web3j;
import org.web3j.protocol.http.HttpService;

public class Signer extends FilecoinJsonRpcEndpoint {

  private static final Logger LOG = LogManager.getLogger();
  public static final String ETH1_SIGN_ENDPOINT =
      "/api/v1/eth1/sign/{identifier}"; // using secp keys
  public static final String ETH2_SIGN_ENDPOINT =
      "/api/v1/eth2/sign/{identifier}"; // using bls keys
  public static final String ETH1_PUBLIC_KEYS = "/api/v1/eth1/publicKeys"; // secp keys
  public static final String ETH2_PUBLIC_KEYS = "/api/v1/eth2/publicKeys"; // bls keys
  public static final String RELOAD_ENDPOINT = "/reload";

  public static final ObjectMapper ETH_2_INTERFACE_OBJECT_MAPPER =
      SigningObjectMapperFactory.createObjectMapper().setSerializationInclusion(Include.NON_NULL);
  private static final String METRICS_ENDPOINT = "/metrics";
  private static final String HEALTHCHECK_ENDPOINT = "/healthcheck";

  private final SignerConfiguration signerConfig;
  private final Web3SignerRunner runner;
  private final String hostname;
  private Accounts accounts;
  private PublicContracts publicContracts;
  private Transactions transactions;
  private final Vertx vertx;
  private final String urlFormatting;
  private final Optional<ClientTlsConfig> clientTlsConfig;
  private Web3j jsonRpc;

  public Signer(final SignerConfiguration signerConfig, final ClientTlsConfig clientTlsConfig) {
    super(JSON_RPC_PATH);
    this.signerConfig = signerConfig;
    this.runner = Web3SignerRunner.createRunner(signerConfig);
    this.hostname = signerConfig.hostname();
    this.urlFormatting =
        signerConfig.getServerTlsOptions().isPresent() ? "https://%s:%s" : "http://%s:%s";
    this.clientTlsConfig = Optional.ofNullable(clientTlsConfig);
    vertx = Vertx.vertx();
  }

  public void start() {
    LOG.info("Starting Web3Signer");
    runner.start();
    final String httpUrl = getUrl();
    jsonRpc = new JsonRpc2_0Web3j(new HttpService(httpUrl));
    final Eth eth = new Eth(jsonRpc);
    this.transactions = new Transactions(eth);
    this.publicContracts = new PublicContracts(eth);
    this.accounts = new Accounts(eth);
    LOG.info("Http requests being submitted to : {} ", httpUrl);
  }

  public void shutdown() {
    LOG.info("Shutting down Web3Signer");
    vertx.close();
    runner.shutdown();
  }

  public boolean isRunning() {
    return runner.isRunning();
  }

  public int getUpcheckStatus() {
    return requestSpec().baseUri(getUrl()).when().get("/upcheck").then().extract().statusCode();
  }

  public RequestSpecification requestSpec() {
    return given().spec(createRequestSpecification(clientTlsConfig)).baseUri(getUrl());
  }

  public void awaitStartupCompletion() {
    LOG.info("Waiting for Signer to become responsive...");
    waitFor(signerConfig.getStartupTimeout(), () -> assertThat(getUpcheckStatus()).isEqualTo(200));
    LOG.info("Signer is now responsive");
  }

  @Override
  public String getUrl() {
    return String.format(urlFormatting, hostname, runner.httpPort());
  }

  public String getMetricsUrl() {
    return String.format(urlFormatting, hostname, runner.metricsPort());
  }

  public Response eth1Sign(final String publicKey, final Bytes dataToSign) {
    return given()
        .baseUri(getUrl())
        .contentType(ContentType.JSON)
        .pathParam("identifier", publicKey)
        .body(new JsonObject().put("data", dataToSign.toHexString()).toString())
        .post(signPath(KeyType.SECP256K1));
  }

  public Response eth2Sign(final String publicKey, final Eth2SigningRequestBody ethSignBody)
      throws JsonProcessingException {
    return eth2Sign(publicKey, ethSignBody, ContentType.TEXT);
  }

  public Response eth2Sign(
      final String publicKey,
      final Eth2SigningRequestBody ethSignBody,
      final ContentType acceptMediaType)
      throws JsonProcessingException {
    return given()
        .baseUri(getUrl())
        .contentType(ContentType.JSON)
        .accept(acceptMediaType)
        .pathParam("identifier", publicKey)
        .body(ETH_2_INTERFACE_OBJECT_MAPPER.writeValueAsString(ethSignBody))
        .log()
        .all(true)
        .post(signPath(BLS));
  }

  public Response callApiPublicKeys(final KeyType keyType) {
    return given().baseUri(getUrl()).get(publicKeysPath(keyType));
  }

  public List<String> listPublicKeys(final KeyType keyType) {
    return callApiPublicKeys(keyType).as(new TypeRef<>() {});
  }

  public static String publicKeysPath(final KeyType keyType) {
    return keyType == BLS ? ETH2_PUBLIC_KEYS : ETH1_PUBLIC_KEYS;
  }

  public static String signPath(final KeyType keyType) {
    return keyType == BLS ? ETH2_SIGN_ENDPOINT : ETH1_SIGN_ENDPOINT;
  }

  public Set<String> getMetricsMatching(final List<String> metricsOfInterest) {
    final Response response =
        given().baseUri(getMetricsUrl()).contentType(ContentType.JSON).when().get(METRICS_ENDPOINT);

    final List<String> lines =
        Arrays.asList(response.getBody().asString().split(String.format("%n")).clone());

    return lines.stream()
        .filter(line -> metricsOfInterest.contains(Iterables.get(Splitter.on(' ').split(line), 0)))
        .collect(Collectors.toSet());
  }

  public String getSlashingDbUrl() {
    return runner.getSlashingDbUrl();
  }

  public Response callReload() {
    return given().baseUri(getUrl()).post(RELOAD_ENDPOINT);
  }

  public Response healthcheck() {
    return given().baseUri(getUrl()).get(HEALTHCHECK_ENDPOINT);
  }

  public Ethereum jsonRpc() {
    return jsonRpc;
  }

  public Accounts accounts() {
    return accounts;
  }

  public Transactions transactions() {
    return this.transactions;
  }

  public PublicContracts publicContracts() {
    return publicContracts;
  }
}
