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
package tech.pegasys.web3signer.dsl.signer.runner;

import static tech.pegasys.web3signer.commandline.PicoCliAwsKmsParameters.AWS_KMS_ACCESS_KEY_ID_OPTION;
import static tech.pegasys.web3signer.commandline.PicoCliAwsKmsParameters.AWS_KMS_AUTH_MODE_OPTION;
import static tech.pegasys.web3signer.commandline.PicoCliAwsKmsParameters.AWS_KMS_ENABLED_OPTION;
import static tech.pegasys.web3signer.commandline.PicoCliAwsKmsParameters.AWS_KMS_REGION_OPTION;
import static tech.pegasys.web3signer.commandline.PicoCliAwsKmsParameters.AWS_KMS_SECRET_ACCESS_KEY_OPTION;
import static tech.pegasys.web3signer.commandline.PicoCliAwsKmsParameters.AWS_KMS_TAGS_OPTION;
import static tech.pegasys.web3signer.commandline.PicoCliAwsSecretsManagerParameters.AWS_ENDPOINT_OVERRIDE_OPTION;
import static tech.pegasys.web3signer.commandline.PicoCliAwsSecretsManagerParameters.AWS_SECRETS_ACCESS_KEY_ID_OPTION;
import static tech.pegasys.web3signer.commandline.PicoCliAwsSecretsManagerParameters.AWS_SECRETS_AUTH_MODE_OPTION;
import static tech.pegasys.web3signer.commandline.PicoCliAwsSecretsManagerParameters.AWS_SECRETS_ENABLED_OPTION;
import static tech.pegasys.web3signer.commandline.PicoCliAwsSecretsManagerParameters.AWS_SECRETS_PREFIXES_FILTER_OPTION;
import static tech.pegasys.web3signer.commandline.PicoCliAwsSecretsManagerParameters.AWS_SECRETS_REGION_OPTION;
import static tech.pegasys.web3signer.commandline.PicoCliAwsSecretsManagerParameters.AWS_SECRETS_SECRET_ACCESS_KEY_OPTION;
import static tech.pegasys.web3signer.commandline.PicoCliAwsSecretsManagerParameters.AWS_SECRETS_TAG_OPTION;
import static tech.pegasys.web3signer.signing.config.KeystoresParameters.KEYSTORES_PASSWORDS_PATH;
import static tech.pegasys.web3signer.signing.config.KeystoresParameters.KEYSTORES_PASSWORD_FILE;
import static tech.pegasys.web3signer.signing.config.KeystoresParameters.KEYSTORES_PATH;

import tech.pegasys.web3signer.commandline.PicoCliGcpSecretManagerParameters;
import tech.pegasys.web3signer.core.config.ClientAuthConstraints;
import tech.pegasys.web3signer.core.config.TlsOptions;
import tech.pegasys.web3signer.core.config.client.ClientTlsOptions;
import tech.pegasys.web3signer.dsl.signer.SignerConfiguration;
import tech.pegasys.web3signer.dsl.signer.WatermarkRepairParameters;
import tech.pegasys.web3signer.dsl.utils.DatabaseUtil;
import tech.pegasys.web3signer.signing.config.AwsVaultParameters;
import tech.pegasys.web3signer.signing.config.AzureKeyVaultParameters;
import tech.pegasys.web3signer.signing.config.GcpSecretManagerParameters;
import tech.pegasys.web3signer.signing.config.KeystoresParameters;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;

public class CmdLineParamsConfigFileImpl implements CmdLineParamsBuilder {
  private final SignerConfiguration signerConfig;
  private final Path dataPath;
  private Optional<String> slashingProtectionDbUrl = Optional.empty();
  private static final String YAML_STRING_FMT = "%s: \"%s\"%n";
  private static final String YAML_NUMERIC_FMT = "%s: %d%n";
  private static final String YAML_BOOLEAN_FMT = "%s: %b%n";

  public CmdLineParamsConfigFileImpl(final SignerConfiguration signerConfig, final Path dataPath) {
    this.signerConfig = signerConfig;
    this.dataPath = dataPath;
  }

  @Override
  public List<String> createCmdLineParams() {

    final ArrayList<String> params = new ArrayList<>();

    final StringBuilder yamlConfig = new StringBuilder();
    yamlConfig.append(String.format(YAML_STRING_FMT, "logging", signerConfig.logLevel()));
    yamlConfig.append(String.format(YAML_STRING_FMT, "http-listen-host", signerConfig.hostname()));
    yamlConfig.append(String.format(YAML_NUMERIC_FMT, "http-listen-port", signerConfig.httpPort()));

    if (!signerConfig.getHttpHostAllowList().isEmpty()) {
      yamlConfig.append(
          String.format(
              YAML_STRING_FMT,
              "http-host-allowlist",
              String.join(",", signerConfig.getHttpHostAllowList())));
    }
    yamlConfig.append(
        String.format(
            YAML_STRING_FMT, "key-store-path", signerConfig.getKeyStorePath().toString()));
    if (signerConfig.isMetricsEnabled()) {
      yamlConfig.append(String.format(YAML_BOOLEAN_FMT, "metrics-enabled", Boolean.TRUE));
      yamlConfig.append(
          String.format(YAML_NUMERIC_FMT, "metrics-port", signerConfig.getMetricsPort()));

      if (!signerConfig.getMetricsHostAllowList().isEmpty()) {
        yamlConfig.append(
            String.format(
                YAML_STRING_FMT,
                "metrics-host-allowlist",
                String.join(",", signerConfig.getMetricsHostAllowList())));
      }
      if (!signerConfig.getMetricsCategories().isEmpty()) {
        yamlConfig.append(
            String.format(
                YAML_STRING_FMT,
                "metrics-categories", // config-file can only use longest options if more than one
                // option is specified.
                String.join(",", signerConfig.getMetricsCategories())));
      }
    }

    if (signerConfig.isSwaggerUIEnabled()) {
      yamlConfig.append(String.format(YAML_BOOLEAN_FMT, "swagger-ui-enabled", Boolean.TRUE));
    }

    yamlConfig.append(String.format(YAML_BOOLEAN_FMT, "access-logs-enabled", Boolean.TRUE));

    if (signerConfig.isHttpDynamicPortAllocation()) {
      yamlConfig.append(String.format(YAML_STRING_FMT, "data-path", dataPath.toAbsolutePath()));
    }

    yamlConfig.append(createServerTlsArgs());

    params.add(signerConfig.getMode()); // sub-command .. it can't go to config file

    if (signerConfig.getMode().equals("eth2")) {
      yamlConfig.append(createEth2SlashingProtectionArgs());

      if (signerConfig.getKeystoresParameters().isPresent()) {
        final KeystoresParameters keystoresParameters = signerConfig.getKeystoresParameters().get();
        yamlConfig.append(
            String.format(
                YAML_STRING_FMT,
                "eth2.keystores-path",
                keystoresParameters.getKeystoresPath().toAbsolutePath()));
        if (keystoresParameters.getKeystoresPasswordsPath() != null) {
          yamlConfig.append(
              String.format(
                  YAML_STRING_FMT,
                  "eth2.keystores-passwords-path",
                  keystoresParameters.getKeystoresPasswordsPath().toAbsolutePath()));
        }
        if (keystoresParameters.getKeystoresPasswordFile() != null) {
          yamlConfig.append(
              String.format(
                  YAML_STRING_FMT,
                  "eth2.keystores-password-file",
                  keystoresParameters.getKeystoresPasswordFile().toAbsolutePath()));
        }
      }

      signerConfig
          .getAwsParameters()
          .ifPresent(
              awsParams -> yamlConfig.append(awsSecretsManagerBulkLoadingOptions(awsParams)));
      signerConfig
          .getGcpParameters()
          .ifPresent(gcpParameters -> yamlConfig.append(gcpBulkLoadingOptions(gcpParameters)));

      if (signerConfig.isSigningExtEnabled()) {
        yamlConfig.append(
            String.format(YAML_BOOLEAN_FMT, "eth2.Xsigning-ext-enabled", Boolean.TRUE));
      }

      signerConfig
          .getCommitBoostParameters()
          .ifPresent(
              commitBoostParameters ->
                  appendCommitBoostParameters(commitBoostParameters, yamlConfig));

      final CommandArgs subCommandArgs = createSubCommandArgs();
      params.addAll(subCommandArgs.params);
      yamlConfig.append(subCommandArgs.yamlConfig);
    } else if (signerConfig.getMode().equals("eth1")) {
      yamlConfig.append(
          String.format(
              YAML_NUMERIC_FMT, "eth1.downstream-http-port", signerConfig.getDownstreamHttpPort()));
      yamlConfig.append(
          String.format(YAML_NUMERIC_FMT, "eth1.chain-id", signerConfig.getChainIdProvider().id()));
      yamlConfig.append(createDownstreamTlsArgs());

      signerConfig
          .getV3KeystoresBulkloadParameters()
          .ifPresent(setV3KeystoresBulkloadParameters(yamlConfig));

      signerConfig
          .getAwsParameters()
          .ifPresent(awsParams -> yamlConfig.append(awsKmsBulkLoadingOptions(awsParams)));
    }

    signerConfig
        .getAzureKeyVaultParameters()
        .ifPresent(
            azureParams ->
                yamlConfig.append(azureBulkLoadingOptions(signerConfig.getMode(), azureParams)));

    // create temporary config file
    try {
      final Path configFile = Files.createTempFile("web3signer_config", ".yaml");
      FileUtils.forceDeleteOnExit(configFile.toFile());
      Files.writeString(configFile, yamlConfig.toString());

      params.add(0, configFile.toAbsolutePath().toString());
      params.add(0, "--config-file");
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    return params;
  }

  private static void appendCommitBoostParameters(
      final Pair<Path, Path> commitBoostParameters, final StringBuilder yamlConfig) {
    yamlConfig.append(
        String.format(YAML_BOOLEAN_FMT, "eth2.commit-boost-api-enabled", Boolean.TRUE));
    yamlConfig.append(
        String.format(
            YAML_STRING_FMT,
            "eth2.proxy-keystores-path",
            commitBoostParameters.getLeft().toAbsolutePath()));
    yamlConfig.append(
        String.format(
            YAML_STRING_FMT,
            "eth2.proxy-keystores-password-file",
            commitBoostParameters.getRight().toAbsolutePath()));
  }

  private Consumer<? super KeystoresParameters> setV3KeystoresBulkloadParameters(
      final StringBuilder yamlConfig) {
    return keystoresParameters -> {
      yamlConfig.append(
          String.format(
              YAML_STRING_FMT,
              KEYSTORES_PATH.replace("--", "eth1."),
              keystoresParameters.getKeystoresPath().toAbsolutePath()));

      if (keystoresParameters.getKeystoresPasswordsPath() != null) {
        yamlConfig.append(
            String.format(
                YAML_STRING_FMT,
                KEYSTORES_PASSWORDS_PATH.replace("--", "eth1."),
                keystoresParameters.getKeystoresPasswordsPath().toAbsolutePath()));
      }
      if (keystoresParameters.getKeystoresPasswordFile() != null) {
        yamlConfig.append(
            String.format(
                YAML_STRING_FMT,
                KEYSTORES_PASSWORD_FILE.replace("--", "eth1."),
                keystoresParameters.getKeystoresPasswordFile().toAbsolutePath()));
      }
    };
  }

  private String azureBulkLoadingOptions(
      final String mode, final AzureKeyVaultParameters azureParams) {
    final StringBuilder yamlConfig = new StringBuilder();
    yamlConfig.append(String.format(YAML_BOOLEAN_FMT, mode + ".azure-vault-enabled", Boolean.TRUE));
    yamlConfig.append(
        String.format(
            YAML_STRING_FMT,
            mode + ".azure-vault-auth-mode",
            azureParams.getAuthenticationMode().name()));
    yamlConfig.append(
        String.format(YAML_STRING_FMT, mode + ".azure-vault-name", azureParams.getKeyVaultName()));
    yamlConfig.append(
        String.format(YAML_STRING_FMT, mode + ".azure-client-id", azureParams.getClientId()));
    yamlConfig.append(
        String.format(
            YAML_STRING_FMT, mode + ".azure-client-secret", azureParams.getClientSecret()));
    yamlConfig.append(
        String.format(YAML_STRING_FMT, mode + ".azure-tenant-id", azureParams.getTenantId()));

    azureParams
        .getTags()
        .forEach(
            (tagName, tagValue) ->
                yamlConfig.append(
                    String.format(
                        YAML_STRING_FMT, mode + ".azure-tags", tagName + "=" + tagValue)));
    return yamlConfig.toString();
  }

  private CommandArgs createSubCommandArgs() {
    final List<String> params = new ArrayList<>();
    final StringBuilder yamlConfig = new StringBuilder();

    if (signerConfig.getSlashingExportPath().isPresent()) {
      params.add("export"); // sub-sub command
      yamlConfig.append(
          String.format(
              YAML_STRING_FMT,
              "eth2.export.to",
              signerConfig.getSlashingExportPath().get().toAbsolutePath()));
    } else if (signerConfig.getSlashingImportPath().isPresent()) {
      params.add("import"); // sub-sub command
      yamlConfig.append(
          String.format(
              YAML_STRING_FMT,
              "eth2.import.from",
              signerConfig.getSlashingImportPath().get().toAbsolutePath()));
    } else if (signerConfig.getWatermarkRepairParameters().isPresent()) {
      params.add("watermark-repair"); // sub-sub command
      final WatermarkRepairParameters watermarkRepairParameters =
          signerConfig.getWatermarkRepairParameters().get();
      if (watermarkRepairParameters.isRemoveHighWatermark()) {
        yamlConfig.append(
            String.format(
                YAML_BOOLEAN_FMT,
                "eth2.watermark-repair.remove-high-watermark",
                watermarkRepairParameters.isRemoveHighWatermark()));
      } else {
        yamlConfig.append(
            String.format(
                YAML_NUMERIC_FMT,
                "eth2.watermark-repair.slot",
                watermarkRepairParameters.getSlot()));
        yamlConfig.append(
            String.format(
                YAML_NUMERIC_FMT,
                "eth2.watermark-repair.epoch",
                watermarkRepairParameters.getEpoch()));
        yamlConfig.append(
            String.format(
                YAML_BOOLEAN_FMT,
                "eth2.watermark-repair.set-high-watermark",
                watermarkRepairParameters.isSetHighWatermark()));
      }
    }

    return new CommandArgs(params, yamlConfig.toString());
  }

  @Override
  public Optional<String> slashingProtectionDbUrl() {
    return slashingProtectionDbUrl;
  }

  private String createServerTlsArgs() {
    final StringBuilder yamlConfig = new StringBuilder();

    if (signerConfig.getServerTlsOptions().isPresent()) {
      final TlsOptions serverTlsOptions = signerConfig.getServerTlsOptions().get();
      yamlConfig.append(
          String.format(
              YAML_STRING_FMT, "tls-keystore-file", serverTlsOptions.getKeyStoreFile().toString()));
      yamlConfig.append(
          String.format(
              YAML_STRING_FMT,
              "tls-keystore-password-file",
              serverTlsOptions.getKeyStorePasswordFile().toString()));
      if (serverTlsOptions.getClientAuthConstraints().isEmpty()) {
        yamlConfig.append(String.format(YAML_BOOLEAN_FMT, "tls-allow-any-client", Boolean.TRUE));
      } else {
        final ClientAuthConstraints constraints = serverTlsOptions.getClientAuthConstraints().get();
        if (constraints.getKnownClientsFile().isPresent()) {
          yamlConfig.append(
              String.format(
                  YAML_STRING_FMT,
                  "tls-known-clients-file",
                  constraints.getKnownClientsFile().get()));
        }
        if (constraints.isCaAuthorizedClientAllowed()) {
          yamlConfig.append(String.format(YAML_BOOLEAN_FMT, "tls-allow-ca-clients", Boolean.TRUE));
        }
      }
    }
    return yamlConfig.toString();
  }

  private String createDownstreamTlsArgs() {
    final Optional<ClientTlsOptions> optionalClientTlsOptions =
        signerConfig.getDownstreamTlsOptions();
    final StringBuilder yamlConfig = new StringBuilder();
    if (optionalClientTlsOptions.isEmpty()) {
      return yamlConfig.toString();
    }

    final ClientTlsOptions clientTlsOptions = optionalClientTlsOptions.get();
    yamlConfig.append(
        String.format(YAML_BOOLEAN_FMT, "eth1.downstream-http-tls-enabled", Boolean.TRUE));

    clientTlsOptions
        .getKeyStoreOptions()
        .ifPresent(
            pkcsStoreConfig -> {
              yamlConfig.append(
                  String.format(
                      YAML_STRING_FMT,
                      "eth1.downstream-http-tls-keystore-file",
                      pkcsStoreConfig.getKeyStoreFile().toString()));
              yamlConfig.append(
                  String.format(
                      YAML_STRING_FMT,
                      "eth1.downstream-http-tls-keystore-password-file",
                      pkcsStoreConfig.getPasswordFile().toString()));
            });

    if (clientTlsOptions.getKnownServersFile().isPresent()) {
      yamlConfig.append(
          String.format(
              YAML_STRING_FMT,
              "eth1.downstream-http-tls-known-servers-file",
              clientTlsOptions.getKnownServersFile().get().toAbsolutePath()));
    }
    if (!clientTlsOptions.isCaAuthEnabled()) {
      yamlConfig.append(
          String.format(
              YAML_BOOLEAN_FMT, "eth1.downstream-http-tls-ca-auth-enabled", Boolean.FALSE));
    }

    return yamlConfig.toString();
  }

  private String createEth2SlashingProtectionArgs() {
    final StringBuilder yamlConfig = new StringBuilder();
    yamlConfig.append(
        String.format(
            YAML_BOOLEAN_FMT,
            "eth2.slashing-protection-enabled",
            signerConfig.isSlashingProtectionEnabled()));

    if (signerConfig.isSlashingProtectionEnabled()) {
      slashingProtectionDbUrl =
          signerConfig
              .getSlashingProtectionDbUrl()
              .or(() -> Optional.of(DatabaseUtil.create().databaseUrl()));
      yamlConfig.append(
          String.format(
              YAML_STRING_FMT, "eth2.slashing-protection-db-url", slashingProtectionDbUrl.get()));
      yamlConfig.append(
          String.format(
              YAML_STRING_FMT,
              "eth2.slashing-protection-db-username",
              signerConfig.getSlashingProtectionDbUsername()));
      yamlConfig.append(
          String.format(
              YAML_STRING_FMT,
              "eth2.slashing-protection-db-password",
              signerConfig.getSlashingProtectionDbPassword()));
      if (signerConfig.getSlashingProtectionDbPoolConfigurationFile().isPresent()) {
        yamlConfig.append(
            String.format(
                YAML_STRING_FMT,
                "eth2.slashing-protection-db-pool-configuration-file",
                signerConfig.getSlashingProtectionDbPoolConfigurationFile()));
      }

      // enabled by default, explicitly set when false
      if (!signerConfig.isSlashingProtectionDbConnectionPoolEnabled()) {
        yamlConfig.append(
            String.format(
                YAML_BOOLEAN_FMT,
                "eth2.Xslashing-protection-db-connection-pool-enabled",
                signerConfig.isSlashingProtectionDbConnectionPoolEnabled()));
      }
    }

    if (signerConfig.isSlashingProtectionPruningEnabled()) {
      yamlConfig.append(
          String.format(
              YAML_BOOLEAN_FMT,
              "eth2.slashing-protection-pruning-enabled",
              signerConfig.isSlashingProtectionPruningEnabled()));
      yamlConfig.append(
          String.format(
              YAML_BOOLEAN_FMT,
              "eth2.slashing-protection-pruning-at-boot-enabled",
              signerConfig.isSlashingProtectionPruningAtBootEnabled()));
      yamlConfig.append(
          String.format(
              YAML_NUMERIC_FMT,
              "eth2.slashing-protection-pruning-epochs-to-keep",
              signerConfig.getSlashingProtectionPruningEpochsToKeep()));
      yamlConfig.append(
          String.format(
              YAML_NUMERIC_FMT,
              "eth2.slashing-protection-pruning-slots-per-epoch",
              signerConfig.getSlashingProtectionPruningSlotsPerEpoch()));
      yamlConfig.append(
          String.format(
              YAML_NUMERIC_FMT,
              "eth2.slashing-protection-pruning-interval",
              signerConfig.getSlashingProtectionPruningInterval()));
    }

    if (signerConfig.getAltairForkEpoch().isPresent()) {
      yamlConfig.append(
          String.format(
              YAML_NUMERIC_FMT,
              "eth2.Xnetwork-altair-fork-epoch",
              signerConfig.getAltairForkEpoch().get()));
    }

    if (signerConfig.getBellatrixForkEpoch().isPresent()) {
      yamlConfig.append(
          String.format(
              YAML_NUMERIC_FMT,
              "eth2.Xnetwork-bellatrix-fork-epoch",
              signerConfig.getBellatrixForkEpoch().get()));
    }

    if (signerConfig.getCapellaForkEpoch().isPresent()) {
      yamlConfig.append(
          String.format(
              YAML_NUMERIC_FMT,
              "eth2.Xnetwork-capella-fork-epoch",
              signerConfig.getCapellaForkEpoch().get()));
    }

    if (signerConfig.getDenebForkEpoch().isPresent()) {
      yamlConfig.append(
          String.format(
              YAML_NUMERIC_FMT,
              "eth2.Xnetwork-deneb-fork-epoch",
              signerConfig.getDenebForkEpoch().get()));
    }

    if (signerConfig.getElectraForkEpoch().isPresent()) {
      yamlConfig.append(
          String.format(
              YAML_NUMERIC_FMT,
              "eth2.Xnetwork-electra-fork-epoch",
              signerConfig.getElectraForkEpoch().get()));
    }

    if (signerConfig.getNetwork().isPresent()) {
      yamlConfig.append(
          String.format(YAML_STRING_FMT, "eth2.network", signerConfig.getNetwork().get()));
    }

    return yamlConfig.toString();
  }

  private String awsSecretsManagerBulkLoadingOptions(final AwsVaultParameters awsVaultParameters) {
    final StringBuilder yamlConfig = new StringBuilder();

    yamlConfig.append(
        String.format(
            YAML_BOOLEAN_FMT,
            "eth2." + AWS_SECRETS_ENABLED_OPTION.substring(2),
            awsVaultParameters.isEnabled()));

    yamlConfig.append(
        String.format(
            YAML_STRING_FMT,
            "eth2." + AWS_SECRETS_AUTH_MODE_OPTION.substring(2),
            awsVaultParameters.getAuthenticationMode().name()));

    if (awsVaultParameters.getAccessKeyId() != null) {
      yamlConfig.append(
          String.format(
              YAML_STRING_FMT,
              "eth2." + AWS_SECRETS_ACCESS_KEY_ID_OPTION.substring(2),
              awsVaultParameters.getAccessKeyId()));
    }

    if (awsVaultParameters.getSecretAccessKey() != null) {
      yamlConfig.append(
          String.format(
              YAML_STRING_FMT,
              "eth2." + AWS_SECRETS_SECRET_ACCESS_KEY_OPTION.substring(2),
              awsVaultParameters.getSecretAccessKey()));
    }

    if (awsVaultParameters.getRegion() != null) {
      yamlConfig.append(
          String.format(
              YAML_STRING_FMT,
              "eth2." + AWS_SECRETS_REGION_OPTION.substring(2),
              awsVaultParameters.getRegion()));
    }

    if (!awsVaultParameters.getPrefixesFilter().isEmpty()) {
      yamlConfig.append(
          String.format(
              YAML_STRING_FMT,
              "eth2." + AWS_SECRETS_PREFIXES_FILTER_OPTION.substring(2),
              String.join(",", awsVaultParameters.getPrefixesFilter())));
    }

    if (!awsVaultParameters.getTags().isEmpty()) {
      yamlConfig.append("eth2.").append(AWS_SECRETS_TAG_OPTION.substring(2)).append(":\n");
      awsVaultParameters
          .getTags()
          .forEach((key, value) -> yamlConfig.append(String.format("  %s: \"%s\"%n", key, value)));
    }

    awsVaultParameters
        .getEndpointOverride()
        .ifPresent(
            uri ->
                yamlConfig.append(
                    String.format(
                        YAML_STRING_FMT,
                        "eth2." + AWS_ENDPOINT_OVERRIDE_OPTION.substring(2),
                        uri)));

    return yamlConfig.toString();
  }

  private String gcpBulkLoadingOptions(
      final GcpSecretManagerParameters gcpSecretManagerParameters) {
    final StringBuilder yamlConfig = new StringBuilder();
    yamlConfig.append(
        String.format(
            YAML_BOOLEAN_FMT,
            "eth2." + PicoCliGcpSecretManagerParameters.GCP_SECRETS_ENABLED_OPTION.substring(2),
            gcpSecretManagerParameters.isEnabled()));
    if (gcpSecretManagerParameters.getProjectId() != null) {
      yamlConfig.append(
          String.format(
              YAML_STRING_FMT,
              "eth2." + PicoCliGcpSecretManagerParameters.GCP_PROJECT_ID_OPTION.substring(2),
              gcpSecretManagerParameters.getProjectId()));
    }
    if (gcpSecretManagerParameters.getFilter().isPresent()) {
      yamlConfig.append(
          String.format(
              YAML_STRING_FMT,
              "eth2." + PicoCliGcpSecretManagerParameters.GCP_SECRETS_FILTER_OPTION.substring(2),
              gcpSecretManagerParameters.getFilter().get()));
    }
    return yamlConfig.toString();
  }

  private String awsKmsBulkLoadingOptions(final AwsVaultParameters awsVaultParameters) {
    final StringBuilder yamlConfig = new StringBuilder();

    yamlConfig.append(
        String.format(
            YAML_BOOLEAN_FMT,
            "eth1." + AWS_KMS_ENABLED_OPTION.substring(2),
            awsVaultParameters.isEnabled()));

    yamlConfig.append(
        String.format(
            YAML_STRING_FMT,
            "eth1." + AWS_KMS_AUTH_MODE_OPTION.substring(2),
            awsVaultParameters.getAuthenticationMode().name()));

    if (awsVaultParameters.getAccessKeyId() != null) {
      yamlConfig.append(
          String.format(
              YAML_STRING_FMT,
              "eth1." + AWS_KMS_ACCESS_KEY_ID_OPTION.substring(2),
              awsVaultParameters.getAccessKeyId()));
    }

    if (awsVaultParameters.getSecretAccessKey() != null) {
      yamlConfig.append(
          String.format(
              YAML_STRING_FMT,
              "eth1." + AWS_KMS_SECRET_ACCESS_KEY_OPTION.substring(2),
              awsVaultParameters.getSecretAccessKey()));
    }

    if (awsVaultParameters.getRegion() != null) {
      yamlConfig.append(
          String.format(
              YAML_STRING_FMT,
              "eth1." + AWS_KMS_REGION_OPTION.substring(2),
              awsVaultParameters.getRegion()));
    }

    if (!awsVaultParameters.getTags().isEmpty()) {
      yamlConfig.append("eth1.").append(AWS_KMS_TAGS_OPTION.substring(2)).append(":\n");
      awsVaultParameters
          .getTags()
          .forEach((key, value) -> yamlConfig.append(String.format("  %s: \"%s\"%n", key, value)));
    }

    awsVaultParameters
        .getTags()
        .forEach(
            (key, value) -> {
              yamlConfig.append(
                  String.format(
                      YAML_STRING_FMT,
                      "eth1." + AWS_KMS_TAGS_OPTION.substring(2),
                      key + "=" + value));
            });

    awsVaultParameters
        .getEndpointOverride()
        .ifPresent(
            uri ->
                yamlConfig.append(
                    String.format(
                        YAML_STRING_FMT,
                        "eth1." + AWS_ENDPOINT_OVERRIDE_OPTION.substring(2),
                        uri)));

    return yamlConfig.toString();
  }

  private record CommandArgs(List<String> params, String yamlConfig) {}
}
