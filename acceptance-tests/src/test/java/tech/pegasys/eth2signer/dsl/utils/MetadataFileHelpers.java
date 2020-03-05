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
package tech.pegasys.eth2signer.dsl.utils;

import static org.assertj.core.api.AssertionsForClassTypes.fail;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class MetadataFileHelpers {
  final ObjectMapper YAML_OBJECT_MAPPER = new ObjectMapper(new YAMLFactory());

  public void createUnencryptedYamlFileAt(final Path metadataFilePath, final String keyContent) {
    final Map<String, String> signingMetadata = new HashMap<>();
    signingMetadata.put("type", "file-raw");
    signingMetadata.put("privateKey", keyContent);
    createYamlFile(metadataFilePath, signingMetadata);
  }

  private void createYamlFile(final Path filePath, final Map<String, String> signingMetadata) {
    try {
      YAML_OBJECT_MAPPER.writeValue(filePath.toFile(), signingMetadata);
    } catch (final IOException e) {
      fail("Unable to create metadata file.");
    }
  }
}
