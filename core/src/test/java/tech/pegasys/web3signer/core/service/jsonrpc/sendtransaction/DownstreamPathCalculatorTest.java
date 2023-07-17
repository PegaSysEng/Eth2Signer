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
package tech.pegasys.web3signer.core.service.jsonrpc.sendtransaction;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.web3signer.core.service.DownstreamPathCalculator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DownstreamPathCalculatorTest {

  @ParameterizedTest
  @ValueSource(strings = {"rootPath/child", "/rootPath/child", "/rootPath/child/"})
  void variousPathPrefixesResolveToTheSameDownstreamEndpoint(final String httpDownstreamPath) {
    final DownstreamPathCalculator calc = new DownstreamPathCalculator(httpDownstreamPath);
    final String result = calc.calculateDownstreamPath("/login");
    assertThat(result).isEqualTo("/rootPath/child/login");
  }

  @ParameterizedTest
  @ValueSource(strings = {"rootPath/child", "/rootPath/child", "/rootPath/child/"})
  void variousPathPrefixesResolveToTheSameDownstreamEndpointWithNoSlashOnReceivedUri(
      final String httpDownstreamPath) {
    final DownstreamPathCalculator calc = new DownstreamPathCalculator(httpDownstreamPath);
    final String result = calc.calculateDownstreamPath("login");
    assertThat(result).isEqualTo("/rootPath/child/login");
  }

  @Test
  void blankRequestUriProducesAbsolutePathOfPrefix() {
    final DownstreamPathCalculator calc = new DownstreamPathCalculator("prefix");
    final String result = calc.calculateDownstreamPath("");
    assertThat(result).isEqualTo("/prefix");
  }

  @Test
  void slashRequestUriProducesAbsolutePathOfPrefix() {
    final DownstreamPathCalculator calc = new DownstreamPathCalculator("prefix");
    final String result = calc.calculateDownstreamPath("/");
    assertThat(result).isEqualTo("/prefix");
  }

  @Test
  void slashPrefixWithNoUriResultsInDownstreamOfJustSlash() {
    final DownstreamPathCalculator calc = new DownstreamPathCalculator("/");
    final String result = calc.calculateDownstreamPath("/");
    assertThat(result).isEqualTo("/");
  }

  @Test
  void emptyRootPathIsReplacedWithSlash() {
    final DownstreamPathCalculator calc = new DownstreamPathCalculator("");
    final String result = calc.calculateDownstreamPath("arbitraryPath");
    assertThat(result).isEqualTo("/arbitraryPath");
  }

  @Test
  void multipleSlashInRootAreDiscarded() {
    final DownstreamPathCalculator calc = new DownstreamPathCalculator("////");
    final String result = calc.calculateDownstreamPath("arbitraryPath");
    assertThat(result).isEqualTo("/arbitraryPath");
  }
}
