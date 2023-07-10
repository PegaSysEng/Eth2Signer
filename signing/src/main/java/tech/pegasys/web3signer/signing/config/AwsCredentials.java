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
package tech.pegasys.web3signer.signing.config;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Optional;

public class AwsCredentials {
  private String accessKeyId;
  private String secretAccessKey;
  private Optional<String> sessionToken;

  public static Builder builder() {
    return new Builder();
  }

  public String getAccessKeyId() {
    return accessKeyId;
  }

  public String getSecretAccessKey() {
    return secretAccessKey;
  }

  public Optional<String> getSessionToken() {
    return sessionToken;
  }

  public static final class Builder {
    private String accessKeyId;
    private String secretAccessKey;
    private String sessionToken;

    private Builder() {}

    public Builder withAccessKeyId(final String accessKeyId) {
      this.accessKeyId = accessKeyId;
      return this;
    }

    public Builder withSecretAccessKey(final String secretAccessKey) {
      this.secretAccessKey = secretAccessKey;
      return this;
    }

    public Builder withSessionToken(final String sessionToken) {
      this.sessionToken = sessionToken;
      return this;
    }

    public AwsCredentials build() {
      checkArgument(accessKeyId != null, "Access Key Id must be provided");
      checkArgument(secretAccessKey != null, "Secret Access Key must be provided");

      final AwsCredentials awsCredentials = new AwsCredentials();
      awsCredentials.accessKeyId = this.accessKeyId;
      awsCredentials.secretAccessKey = this.secretAccessKey;
      awsCredentials.sessionToken = Optional.ofNullable(this.sessionToken);
      return awsCredentials;
    }
  }
}
