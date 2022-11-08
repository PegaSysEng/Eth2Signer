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

import static tech.pegasys.web3signer.commandline.PicoCliAwsSecretsManagerParameters.AWS_SECRETS_ACCESS_KEY_ID_OPTION;
import static tech.pegasys.web3signer.commandline.PicoCliAwsSecretsManagerParameters.AWS_SECRETS_AUTH_MODE_OPTION;
import static tech.pegasys.web3signer.commandline.PicoCliAwsSecretsManagerParameters.AWS_SECRETS_ENABLED_OPTION;
import static tech.pegasys.web3signer.commandline.PicoCliAwsSecretsManagerParameters.AWS_SECRETS_PREFIXES_FILTER_OPTION;
import static tech.pegasys.web3signer.commandline.PicoCliAwsSecretsManagerParameters.AWS_SECRETS_REGION_OPTION;
import static tech.pegasys.web3signer.commandline.PicoCliAwsSecretsManagerParameters.AWS_SECRETS_SECRET_ACCESS_KEY_OPTION;
import static tech.pegasys.web3signer.commandline.PicoCliAwsSecretsManagerParameters.AWS_SECRETS_TAG_NAMES_FILTER_OPTION;
import static tech.pegasys.web3signer.commandline.PicoCliAwsSecretsManagerParameters.AWS_SECRETS_TAG_VALUES_FILTER_OPTION;

import tech.pegasys.web3signer.core.config.ClientAuthConstraints;
import tech.pegasys.web3signer.core.config.TlsOptions;
import tech.pegasys.web3signer.dsl.signer.SignerConfiguration;
import tech.pegasys.web3signer.dsl.utils.DatabaseUtil;
import tech.pegasys.web3signer.signing.config.AwsSecretsManagerParameters;
import tech.pegasys.web3signer.signing.config.AzureKeyVaultParameters;
import tech.pegasys.web3signer.signing.config.KeystoresParameters;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.Lists;

public class CmdLineParamsDefaultImpl implements CmdLineParamsBuilder {
  private final SignerConfiguration signerConfig;
  private final Path dataPath;
  private Optional<String> slashingProtectionDbUrl = Optional.empty();

  public CmdLineParamsDefaultImpl(final SignerConfiguration signerConfig, final Path dataPath) {
    this.signerConfig = signerConfig;
    this.dataPath = dataPath;
  }

  @Override
  public List<String> createCmdLineParams() {
    final List<String> params = new ArrayList<>();
    params.add("--logging");
    params.add(signerConfig.logLevel());
    params.add("--http-listen-host");
    params.add(signerConfig.hostname());
    params.add("--http-listen-port");
    params.add(String.valueOf(signerConfig.httpPort()));
    if (!signerConfig.getHttpHostAllowList().isEmpty()) {
      params.add("--http-host-allowlist");
      params.add(String.join(",", signerConfig.getHttpHostAllowList()));
    }
    params.add("--key-store-path");
    params.add(signerConfig.getKeyStorePath().toString());
    if (signerConfig.isMetricsEnabled()) {
      params.add("--metrics-enabled");
      params.add("--metrics-port");
      params.add(Integer.toString(signerConfig.getMetricsPort()));
      if (!signerConfig.getMetricsHostAllowList().isEmpty()) {
        params.add("--metrics-host-allowlist");
        params.add(String.join(",", signerConfig.getMetricsHostAllowList()));
      }
      if (!signerConfig.getMetricsCategories().isEmpty()) {
        params.add("--metrics-category");
        params.add(String.join(",", signerConfig.getMetricsCategories()));
      }
    }

    if (signerConfig.isSwaggerUIEnabled()) {
      params.add("--swagger-ui-enabled=true");
    }

    params.add("--access-logs-enabled=true");

    if (signerConfig.isHttpDynamicPortAllocation()) {
      params.add("--data-path");
      params.add(dataPath.toAbsolutePath().toString());
    }

    params.addAll(createServerTlsArgs());

    params.add(signerConfig.getMode());

    if (signerConfig.getMode().equals("eth2")) {
      params.addAll(createEth2Args());

      if (signerConfig.getAzureKeyVaultParameters().isPresent()) {
        final AzureKeyVaultParameters azureParams = signerConfig.getAzureKeyVaultParameters().get();
        params.add("--azure-vault-enabled=true");
        params.add("--azure-vault-auth-mode");
        params.add(azureParams.getAuthenticationMode().name());
        params.add("--azure-vault-name");
        params.add(azureParams.getKeyVaultName());
        params.add("--azure-client-id");
        params.add(azureParams.getClientId());
        params.add("--azure-client-secret");
        params.add(azureParams.getClientSecret());
        params.add("--azure-tenant-id");
        params.add(azureParams.getTenantId());
      }
      if (signerConfig.getKeystoresParameters().isPresent()) {
        final KeystoresParameters keystoresParameters = signerConfig.getKeystoresParameters().get();
        params.add("--keystores-path");
        params.add(keystoresParameters.getKeystoresPath().toAbsolutePath().toString());
        if (keystoresParameters.getKeystoresPasswordsPath() != null) {
          params.add("--keystores-passwords-path");
          params.add(keystoresParameters.getKeystoresPasswordsPath().toAbsolutePath().toString());
        }
        if (keystoresParameters.getKeystoresPasswordFile() != null) {
          params.add("--keystores-password-file");
          params.add(keystoresParameters.getKeystoresPasswordFile().toAbsolutePath().toString());
        }
      }

      signerConfig
          .getAwsSecretsManagerParameters()
          .ifPresent(awsParams -> params.addAll(awsBulkLoadingOptions(awsParams)));
    }

    return params;
  }

  @Override
  public Optional<String> slashingProtectionDbUrl() {
    return slashingProtectionDbUrl;
  }

  private Collection<? extends String> createServerTlsArgs() {
    final List<String> params = Lists.newArrayList();

    if (signerConfig.getServerTlsOptions().isPresent()) {
      final TlsOptions serverTlsOptions = signerConfig.getServerTlsOptions().get();
      params.add("--tls-keystore-file");
      params.add(serverTlsOptions.getKeyStoreFile().toString());
      params.add("--tls-keystore-password-file");
      params.add(serverTlsOptions.getKeyStorePasswordFile().toString());
      if (serverTlsOptions.getClientAuthConstraints().isEmpty()) {
        params.add("--tls-allow-any-client=true");
      } else {
        final ClientAuthConstraints constraints = serverTlsOptions.getClientAuthConstraints().get();
        if (constraints.getKnownClientsFile().isPresent()) {
          params.add("--tls-known-clients-file");
          params.add(constraints.getKnownClientsFile().get().toString());
        }
        if (constraints.isCaAuthorizedClientAllowed()) {
          params.add("--tls-allow-ca-clients=true");
        }
      }
    }
    return params;
  }

  private Collection<String> createEth2Args() {
    final List<String> params = Lists.newArrayList();
    params.add("--slashing-protection-enabled");
    params.add(Boolean.toString(signerConfig.isSlashingProtectionEnabled()));

    if (signerConfig.isSlashingProtectionEnabled()) {
      slashingProtectionDbUrl =
          signerConfig
              .getSlashingProtectionDbUrl()
              .or(() -> Optional.of(DatabaseUtil.create().databaseUrl()));
      params.add("--slashing-protection-db-url");
      params.add(slashingProtectionDbUrl.get());
      params.add("--slashing-protection-db-username");
      params.add(signerConfig.getSlashingProtectionDbUsername());
      params.add("--slashing-protection-db-password");
      params.add(signerConfig.getSlashingProtectionDbPassword());
      if (signerConfig.getSlashingProtectionDbPoolConfigurationFile().isPresent()) {
        params.add("--slashing-protection-db-pool-configuration-file");
        params.add(signerConfig.getSlashingProtectionDbPoolConfigurationFile().toString());
      }

      // enabled by default, explicitly set when false
      if (!signerConfig.isSlashingProtectionDbConnectionPoolEnabled()) {
        params.add("--Xslashing-protection-db-connection-pool-enabled=false");
      }
    }

    if (signerConfig.getSlashingExportPath().isPresent()) {
      params.add("export");
      params.add("--to");
      params.add(signerConfig.getSlashingExportPath().get().toAbsolutePath().toString());
    } else if (signerConfig.getSlashingImportPath().isPresent()) {
      params.add("import");
      params.add("--from");
      params.add(signerConfig.getSlashingImportPath().get().toAbsolutePath().toString());
    }

    if (signerConfig.isSlashingProtectionPruningEnabled()) {
      params.add("--slashing-protection-pruning-enabled");
      params.add(Boolean.toString(signerConfig.isSlashingProtectionPruningEnabled()));
      params.add("--slashing-protection-pruning-at-boot-enabled");
      params.add(Boolean.toString(signerConfig.isSlashingProtectionPruningAtBootEnabled()));
      params.add("--slashing-protection-pruning-epochs-to-keep");
      params.add(Long.toString(signerConfig.getSlashingProtectionPruningEpochsToKeep()));
      params.add("--slashing-protection-pruning-slots-per-epoch");
      params.add(Long.toString(signerConfig.getSlashingProtectionPruningSlotsPerEpoch()));
      params.add("--slashing-protection-pruning-interval");
      params.add(Long.toString(signerConfig.getSlashingProtectionPruningInterval()));
    }

    if (signerConfig.getAltairForkEpoch().isPresent()) {
      params.add("--Xnetwork-altair-fork-epoch");
      params.add(Long.toString(signerConfig.getAltairForkEpoch().get()));
    }

    if (signerConfig.getBellatrixForkEpoch().isPresent()) {
      params.add("--Xnetwork-bellatrix-fork-epoch");
      params.add(Long.toString(signerConfig.getBellatrixForkEpoch().get()));
    }

    if (signerConfig.getNetwork().isPresent()) {
      params.add("--network");
      params.add(signerConfig.getNetwork().get());
    }

    if (signerConfig.isKeyManagerApiEnabled()) {
      params.add("--key-manager-api-enabled");
      params.add(Boolean.toString(signerConfig.isKeyManagerApiEnabled()));
    }

    return params;
  }

  private Collection<String> awsBulkLoadingOptions(
      final AwsSecretsManagerParameters awsSecretsManagerParameters) {
    final List<String> params = new ArrayList<>();

    params.add(AWS_SECRETS_ENABLED_OPTION + "=" + awsSecretsManagerParameters.isEnabled());

    params.add(AWS_SECRETS_AUTH_MODE_OPTION);
    params.add(awsSecretsManagerParameters.getAuthenticationMode().name());

    if (awsSecretsManagerParameters.getAccessKeyId() != null) {
      params.add(AWS_SECRETS_ACCESS_KEY_ID_OPTION);
      params.add(awsSecretsManagerParameters.getAccessKeyId());
    }

    if (awsSecretsManagerParameters.getSecretAccessKey() != null) {
      params.add(AWS_SECRETS_SECRET_ACCESS_KEY_OPTION);
      params.add(awsSecretsManagerParameters.getSecretAccessKey());
    }

    if (awsSecretsManagerParameters.getRegion() != null) {
      params.add(AWS_SECRETS_REGION_OPTION);
      params.add(awsSecretsManagerParameters.getRegion());
    }

    if (!awsSecretsManagerParameters.getPrefixesFilter().isEmpty()) {
      params.add(AWS_SECRETS_PREFIXES_FILTER_OPTION);
      params.add(String.join(",", awsSecretsManagerParameters.getPrefixesFilter()));
    }

    if (!awsSecretsManagerParameters.getTagNamesFilter().isEmpty()) {
      params.add(AWS_SECRETS_TAG_NAMES_FILTER_OPTION);
      params.add(String.join(",", awsSecretsManagerParameters.getTagNamesFilter()));
    }

    if (!awsSecretsManagerParameters.getTagValuesFilter().isEmpty()) {
      params.add(AWS_SECRETS_TAG_VALUES_FILTER_OPTION);
      params.add(String.join(",", awsSecretsManagerParameters.getTagValuesFilter()));
    }

    return params;
  }
}
