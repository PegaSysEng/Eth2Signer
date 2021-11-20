/*
 * Copyright 2021 ConsenSys AG.
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
package tech.pegasys.web3signer.core.util;

import static org.assertj.core.api.Assertions.assertThatCode;

import tech.pegasys.web3signer.core.Runner;

import java.nio.file.Path;
import java.util.Optional;

import io.vertx.core.Vertx;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OpenApiResourcesTest {

  @ParameterizedTest
  @ValueSource(
      strings = {"eth1/web3signer.yaml", "eth2/web3signer.yaml", "filecoin/web3signer.yaml"})
  void openapiSpecsAreExtractedAndLoaded(final String spec) throws Exception {
    final OpenApiResources openApiResources = new OpenApiResources();
    final Optional<Path> specPath = openApiResources.getSpecPath(spec);

    // assert that OpenAPI3RouterFactory is able to load the extracted specs
    assertThatCode(() -> Runner.getOpenAPI3RouterFactory(Vertx.vertx(), specPath.get().toString()))
        .doesNotThrowAnyException();
  }
}
