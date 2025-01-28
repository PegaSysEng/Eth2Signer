/*
 * Copyright 2023 ConsenSys AG.
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
package tech.pegasys.web3signer.keystorage.aws;

import tech.pegasys.web3signer.keystorage.common.MappedResults;
import tech.pegasys.web3signer.keystorage.common.SecretValueMapperUtil;

import java.io.Closeable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClientBuilder;
import software.amazon.awssdk.services.secretsmanager.model.Filter;
import software.amazon.awssdk.services.secretsmanager.model.FilterNameStringType;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretsRequest;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;
import software.amazon.awssdk.services.secretsmanager.paginators.ListSecretsIterable;

public class AwsSecretsManager implements Closeable {

  private static final Logger LOG = LogManager.getLogger();

  private final SecretsManagerClient secretsManagerClient;

  private AwsSecretsManager(final SecretsManagerClient secretsManagerClient) {
    this.secretsManagerClient = secretsManagerClient;
  }

  static AwsSecretsManager createAwsSecretsManager(
      final String accessKeyId,
      final String secretAccessKey,
      final String region,
      final Optional<URI> awsEndpointURI) {
    final AwsBasicCredentials awsBasicCredentials =
        AwsBasicCredentials.create(accessKeyId, secretAccessKey);
    final StaticCredentialsProvider credentialsProvider =
        StaticCredentialsProvider.create(awsBasicCredentials);

    final SecretsManagerClientBuilder builder =
        SecretsManagerClient.builder()
            .credentialsProvider(credentialsProvider)
            .region(Region.of(region));

    awsEndpointURI.ifPresent(builder::endpointOverride);

    final SecretsManagerClient secretsManagerClient = builder.build();

    return new AwsSecretsManager(secretsManagerClient);
  }

  static AwsSecretsManager createAwsSecretsManager(final Optional<URI> awsEndpointURI) {
    final SecretsManagerClientBuilder builder = SecretsManagerClient.builder();
    awsEndpointURI.ifPresent(builder::endpointOverride);
    final SecretsManagerClient secretsManagerClient = builder.build();

    return new AwsSecretsManager(secretsManagerClient);
  }

  /**
   * Fetch single secret using name.
   *
   * @param secretName Secret Name
   * @return Optional with secret value. Empty if secret name doesn't exist.
   * @throws RuntimeException if AWS SDK throws SecretsManagerException.
   */
  public Optional<String> fetchSecret(final String secretName) {
    try {
      final GetSecretValueRequest getSecretValueRequest =
          GetSecretValueRequest.builder().secretId(secretName).build();
      final GetSecretValueResponse valueResponse =
          secretsManagerClient.getSecretValue(getSecretValueRequest);
      return Optional.of(valueResponse.secretString());
    } catch (final ResourceNotFoundException e) {
      return Optional.empty();
    } catch (final SecretsManagerException e) {
      throw new RuntimeException(
          "Failed to fetch secret from AWS Secrets Manager: " + e.getMessage(), e);
    }
  }

  private ListSecretsIterable listSecrets(
      final Collection<String> namePrefixes, final Map<String, String> tags) {
    final ListSecretsRequest.Builder listSecretsRequestBuilder = ListSecretsRequest.builder();
    final List<Filter> filters = new ArrayList<>();
    if (!namePrefixes.isEmpty()) {
      filters.add(Filter.builder().key(FilterNameStringType.NAME).values(namePrefixes).build());
    }

    if (!tags.isEmpty()) {
      filters.add(Filter.builder().key(FilterNameStringType.TAG_KEY).values(tags.keySet()).build());
      var nonEmptyTagValues = tags.values().stream().filter(value -> !value.isBlank()).toList();
      if (!nonEmptyTagValues.isEmpty()) {
        filters.add(
            Filter.builder().key(FilterNameStringType.TAG_VALUE).values(nonEmptyTagValues).build());
      }
    }

    return secretsManagerClient.listSecretsPaginator(
        listSecretsRequestBuilder.filters(filters).build());
  }

  /**
   * Bulk load secrets.
   *
   * @param namePrefixes Collection of name prefixes to filter.
   * @param tags Map of tag name/value pairs
   * @param mapper The mapper function that can convert secret value to appropriate type
   * @return SecretValueResult with collection of secret values and error count if any.
   */
  public <R> MappedResults<R> mapSecrets(
      final Collection<String> namePrefixes,
      final Map<String, String> tags,
      final BiFunction<String, String, R> mapper) {
    final Set<R> result = ConcurrentHashMap.newKeySet();
    final AtomicInteger errorCount = new AtomicInteger(0);
    try {
      listSecrets(namePrefixes, tags)
          .iterator()
          .forEachRemaining(
              listSecretsResponse ->
                  listSecretsResponse.secretList().parallelStream()
                      .forEach(
                          secretEntry -> {
                            try {
                              final Optional<String> secretValue = fetchSecret(secretEntry.name());
                              if (secretValue.isEmpty()) {
                                LOG.warn(
                                    "Failed to fetch secret name '{}', and was discarded",
                                    secretEntry.name());
                                errorCount.incrementAndGet();
                              } else {
                                MappedResults<R> multiResult =
                                    SecretValueMapperUtil.mapSecretValue(
                                        mapper, secretEntry.name(), secretValue.get());
                                result.addAll(multiResult.getValues());
                                errorCount.addAndGet(multiResult.getErrorCount());
                              }
                            } catch (final Exception e) {
                              LOG.warn(
                                  "Failed to map secret '{}' to requested object type due to: {}.",
                                  secretEntry.name(),
                                  e.getMessage());
                              errorCount.incrementAndGet();
                            }
                          }));
    } catch (final Exception e) {
      LOG.warn("Unexpected error during AWS list-secrets operation", e);
      errorCount.incrementAndGet();
    }
    return MappedResults.newInstance(result, errorCount.intValue());
  }

  @Override
  public void close() {
    this.secretsManagerClient.close();
  }
}
