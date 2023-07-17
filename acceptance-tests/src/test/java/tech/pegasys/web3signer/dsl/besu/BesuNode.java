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
package tech.pegasys.web3signer.dsl.besu;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.web3signer.dsl.utils.WaitUtils.waitFor;

import tech.pegasys.web3signer.dsl.Accounts;
import tech.pegasys.web3signer.dsl.Eth;
import tech.pegasys.web3signer.dsl.PublicContracts;
import tech.pegasys.web3signer.dsl.Transactions;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.core.Ethereum;
import org.web3j.protocol.core.JsonRpc2_0Web3j;
import org.web3j.protocol.http.HttpService;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.stream.slf4j.Slf4jStream;

public class BesuNode {

  private static final Logger LOG = LogManager.getLogger();

  /** Besu's dev.json has the hard fork at block 0 */
  private static final BigInteger SPURIOUS_DRAGON_HARD_FORK_BLOCK = BigInteger.valueOf(1);

  private final BesuNodeConfig besuNodeConfig;

  @SuppressWarnings("unused")
  private final String[] args;

  private final Map<String, String> environment;
  private final Properties portsProperties = new Properties();
  private Accounts accounts;
  private Future<ProcessResult> besuProcess;
  private Transactions transactions;
  private Web3j jsonRpc;
  private PublicContracts publicContracts;
  private BesuNodePorts besuNodePorts;

  BesuNode(final BesuNodeConfig besuNodeConfig, String[] args, Map<String, String> environment) {
    this.besuNodeConfig = besuNodeConfig;
    this.args = args;
    this.environment = environment;
  }

  public void start() {
    LOG.info("Starting Besu process ...");
    try {
      besuProcess =
          new ProcessExecutor()
              .command(args)
              .environment(environment)
              .readOutput(true)
              .redirectOutput(Slf4jStream.of(getClass(), besuNodeConfig.getName()).asInfo())
              .destroyOnExit()
              .start()
              .getFuture();
    } catch (IOException e) {
      LOG.error("Unable to start Besu process.", e);
      throw new UncheckedIOException(e);
    }
  }

  public void shutdown() {
    if (besuProcess != null) {
      LOG.info("Shutting down Besu Process");
      besuProcess.cancel(true);
      besuProcess = null;
    } else {
      LOG.info("Besu process is not running");
    }
  }

  public void awaitStartupCompletion() {
    final int secondsToWait = Boolean.getBoolean("debugSubProcess") ? 3600 : 60;

    // wait for besu.networks to get created. It is created after besu.ports which we really want.
    final Path besuNetworks = besuNodeConfig.getDataPath().resolve("besu.networks");
    Awaitility.waitAtMost(secondsToWait, TimeUnit.SECONDS)
        .until(() -> besuNetworks.toFile().exists());

    loadPortsFile();
    besuNodePorts =
        new BesuNodePorts(
            Integer.parseInt(getPortByName("json-rpc")), Integer.parseInt(getPortByName("ws-rpc")));

    final String web3jEndPointURL =
        String.format("http://%s:%d", besuNodeConfig.getHostName(), besuNodePorts.getHttpRpc());
    final Web3jService web3jService = new HttpService(web3jEndPointURL);
    jsonRpc = new JsonRpc2_0Web3j(web3jService);

    // wait for eth blocks
    try {
      LOG.info("Waiting for Besu to become responsive...");
      waitFor(
          Duration.ofSeconds(60),
          () -> assertThat(jsonRpc.ethBlockNumber().send().hasError()).isFalse());
      LOG.info("Besu is now responsive");
      waitFor(
          () ->
              assertThat(jsonRpc.ethBlockNumber().send().getBlockNumber())
                  .isGreaterThan(SPURIOUS_DRAGON_HARD_FORK_BLOCK));
    } catch (final ConditionTimeoutException e) {
      throw new RuntimeException("Failed to start the Besu node", e);
    }

    final Eth eth = new Eth(jsonRpc);

    accounts = new Accounts(eth);
    publicContracts = new PublicContracts(eth);
    transactions = new Transactions(eth);
  }

  public BesuNodePorts ports() {
    return besuNodePorts;
  }

  public Accounts accounts() {
    return accounts;
  }

  public Ethereum jsonRpc() {
    return jsonRpc;
  }

  private String getPortByName(final String name) {
    return Optional.ofNullable(portsProperties.getProperty(name))
        .orElseThrow(
            () -> new IllegalStateException("Requested Port before ports properties were written"));
  }

  private void loadPortsFile() {
    try {
      final String contents = Files.readString(besuNodeConfig.getDataPath().resolve("besu.ports"));
      portsProperties.load(new StringReader(contents));
      LOG.info("Ports for node {}: {}", besuNodeConfig.getName(), portsProperties);
    } catch (final IOException e) {
      throw new RuntimeException("Error reading Besu ports file", e);
    }
  }

  public PublicContracts publicContracts() {
    return publicContracts;
  }

  public Transactions transactions() {
    return transactions;
  }
}
