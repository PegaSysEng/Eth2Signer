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
package tech.pegasys.web3signer.commandline;

import tech.pegasys.web3signer.common.config.AwsAuthenticationMode;
import tech.pegasys.web3signer.signing.config.AwsVaultParameters;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import picocli.CommandLine;
import picocli.CommandLine.Option;

public class PicoCliAwsSecretsManagerParameters implements AwsVaultParameters {
  public static final String AWS_SECRETS_ENABLED_OPTION = "--aws-secrets-enabled";
  public static final String AWS_SECRETS_AUTH_MODE_OPTION = "--aws-secrets-auth-mode";
  public static final String AWS_SECRETS_ACCESS_KEY_ID_OPTION = "--aws-secrets-access-key-id";
  public static final String AWS_SECRETS_SECRET_ACCESS_KEY_OPTION =
      "--aws-secrets-secret-access-key";
  public static final String AWS_SECRETS_REGION_OPTION = "--aws-secrets-region";
  public static final String AWS_ENDPOINT_OVERRIDE_OPTION = "--aws-endpoint-override";
  public static final String AWS_SECRETS_PREFIXES_FILTER_OPTION = "--aws-secrets-prefixes-filter";
  public static final String AWS_SECRETS_TAG_OPTION = "--aws-secrets-tag";
  public static final String AWS_CONNECTION_CACHE_SIZE_OPTION = "--aws-connection-cache-size";

  @Option(
      names = AWS_SECRETS_ENABLED_OPTION,
      description =
          "Set to true to enable bulk loading from the AWS Secrets Manager service."
              + " (Default: ${DEFAULT-VALUE})",
      paramLabel = "<BOOL>")
  private boolean awsSecretsManagerEnabled = false;

  @Option(
      names = AWS_SECRETS_AUTH_MODE_OPTION,
      description =
          "Authentication mode for AWS Secrets Manager service. Valid Values: [${COMPLETION-CANDIDATES}]"
              + " (Default: ${DEFAULT-VALUE})",
      paramLabel = "<AUTHENTICATION_MODE>")
  private AwsAuthenticationMode authenticationMode = AwsAuthenticationMode.SPECIFIED;

  @Option(
      names = {AWS_SECRETS_ACCESS_KEY_ID_OPTION},
      description =
          "AWS Access Key Id to authenticate Aws Secrets Manager. Required for SPECIFIED authentication mode.",
      paramLabel = "<ACCESS_KEY_ID>")
  private String accessKeyId;

  @Option(
      names = {AWS_SECRETS_SECRET_ACCESS_KEY_OPTION},
      description =
          "AWS Secret Access Key to authenticate Aws Secrets Manager. Required for SPECIFIED authentication mode.",
      paramLabel = "<SECRET_ACCESS_KEY>")
  private String secretAccessKey;

  @Option(
      names = {AWS_SECRETS_REGION_OPTION},
      description =
          "AWS region where Secrets Manager service is available. Required for SPECIFIED authentication mode.",
      paramLabel = "<Region>")
  private String region;

  @Option(
      names = {AWS_ENDPOINT_OVERRIDE_OPTION},
      description = "Override AWS endpoint.",
      paramLabel = "<URI>")
  private Optional<URI> endpointOverride;

  @Option(
      names = AWS_SECRETS_PREFIXES_FILTER_OPTION,
      description =
          "Optional comma-separated list of secret name prefixes filter to apply while fetching secrets from AWS secrets manager."
              + " Applied as AND operation with other filters.",
      split = ",")
  private List<String> prefixesFilter = Collections.emptyList();

  @Option(
      names = AWS_SECRETS_TAG_OPTION,
      mapFallbackValue = "",
      description =
          "Optional key-value pair to filter secrets based on tags. Format --aws-secrets-tag <tag-name>=<tag-value>. Can be repeated.")
  private Map<String, String> tags = new LinkedHashMap<>();

  @CommandLine.Option(
      names = {AWS_CONNECTION_CACHE_SIZE_OPTION},
      paramLabel = "<LONG>",
      description =
          "Maximum number of connections to cache to the AWS Secrets Manager (default: ${DEFAULT-VALUE})")
  private long cacheMaximumSize = 1;

  @Override
  public boolean isEnabled() {
    return awsSecretsManagerEnabled;
  }

  @Override
  public AwsAuthenticationMode getAuthenticationMode() {
    return authenticationMode;
  }

  @Override
  public String getAccessKeyId() {
    return accessKeyId;
  }

  @Override
  public String getSecretAccessKey() {
    return secretAccessKey;
  }

  @Override
  public String getRegion() {
    return region;
  }

  @Override
  public long getCacheMaximumSize() {
    return cacheMaximumSize;
  }

  @Override
  public Collection<String> getPrefixesFilter() {
    return prefixesFilter;
  }

  @Override
  public Map<String, String> getTags() {
    return tags;
  }

  @Override
  public Optional<URI> getEndpointOverride() {
    return endpointOverride;
  }
}
