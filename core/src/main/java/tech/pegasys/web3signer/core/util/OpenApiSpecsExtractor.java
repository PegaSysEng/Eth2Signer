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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.tuweni.io.Resources;

/**
 * Extract OpenAPI specs from resources /openapi to a temporary folder on disk with capability to
 * fix relative $ref paths so that they can be used by OpenApi3RouterFactory which doesn't deal with
 * relative $ref paths.
 */
public class OpenApiSpecsExtractor {
  private static final String OPENAPI_RESOURCES_ROOT = "openapi";
  private static final ObjectMapper objectMapper =
      new ObjectMapper(new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));

  private final Path destinationDirectory;
  private final List<Path> destinationSpecPaths;

  private OpenApiSpecsExtractor(
      final Path destinationDirectory,
      final boolean fixRelativeRefPaths,
      final boolean forceDeleteOnJvmExit)
      throws IOException {
    this.destinationDirectory = destinationDirectory;
    this.destinationSpecPaths = extractToDestination();

    if (fixRelativeRefPaths) {
      fixRelativeRefAtDestination();
    }

    if (forceDeleteOnJvmExit) {
      FileUtils.forceDeleteOnExit(destinationDirectory.toFile());
    }
  }

  public Optional<Path> getSpecFilePathAtDestination(final String specFilename) {
    return destinationSpecPaths.stream().filter(path -> path.endsWith(specFilename)).findFirst();
  }

  public Path getDestinationDirectory() {
    return destinationDirectory;
  }

  public List<Path> getDestinationSpecPaths() {
    return destinationSpecPaths;
  }

  private void fixRelativeRefAtDestination() {
    destinationSpecPaths.stream()
        .filter(path -> path.getFileName().toString().endsWith(".yaml"))
        .forEach(
            path -> {
              try {
                // load openapi yaml in a map
                final Map<String, Object> yamlMap =
                    objectMapper.readValue(
                        path.toFile(), new TypeReference<HashMap<String, Object>>() {});
                fixRelativePathInOpenApiMap(path, yamlMap);
                // write map back as yaml
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), yamlMap);

              } catch (final IOException e) {
                throw new UncheckedIOException(e);
              }
            });
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void fixRelativePathInOpenApiMap(final Path yamlFile, final Map<String, Object> yamlMap) {
    for (Map.Entry<String, Object> entry : yamlMap.entrySet()) {
      final String key = entry.getKey();
      final Object value = entry.getValue();

      if ("$ref".equals(key) && value instanceof String) {
        modifyRefValue(yamlFile, entry);
      } else if ("discriminator".equals(key) && value instanceof Map) {
        if (((Map<String, Object>) value).containsKey("mapping")) {
          Map<String, Object> discriminatorMappings =
              (Map<String, Object>) ((Map<String, Object>) value).get("mapping");
          discriminatorMappings
              .entrySet()
              .forEach(discriminatorMapEntry -> modifyRefValue(yamlFile, discriminatorMapEntry));
        }
      } else if (value instanceof List) {
        List listItems = (List) value;
        for (Object listItem : listItems) {
          if (listItem instanceof Map) {
            fixRelativePathInOpenApiMap(yamlFile, (Map<String, Object>) listItem);
          } // else we are not interested
        }
      } else if (value instanceof Map) {
        fixRelativePathInOpenApiMap(yamlFile, (Map<String, Object>) value);
      }
    }
  }

  private void modifyRefValue(final Path yamlFile, final Map.Entry<String, Object> entry) {
    final String fileName = StringUtils.substringBefore(entry.getValue().toString(), "#");
    final String jsonPointer = StringUtils.substringAfter(entry.getValue().toString(), "#");
    if (StringUtils.isNotBlank(fileName)) {
      // convert filename path to absolute path
      final Path parent = yamlFile.getParent();
      final Path nonNormalizedRefPath = parent.resolve(Path.of(fileName));
      // remove any . or .. from path
      final Path normalizedPath = nonNormalizedRefPath.toAbsolutePath().normalize();

      final String updatedValue =
          StringUtils.isBlank(jsonPointer)
              ? normalizedPath.toString()
              : normalizedPath + "#" + jsonPointer;
      entry.setValue(updatedValue);
    }
  }

  private List<Path> extractToDestination() throws IOException {
    final List<Path> extractedResources = new ArrayList<>();

    try (final Stream<URL> webrootYamlFiles =
        Resources.find("/" + OPENAPI_RESOURCES_ROOT + "**.*")) {
      webrootYamlFiles.forEach(
          openapiResource -> {
            final String jarPath = openapiResource.getPath();
            final String filePath = StringUtils.substringAfter(jarPath, OPENAPI_RESOURCES_ROOT + "/");
            final Path extractedResource = destinationDirectory.resolve(filePath);
            copyContents(openapiResource, extractedResource);
            extractedResources.add(extractedResource);
          });
    }
    return Collections.unmodifiableList(extractedResources);
  }

  private static void copyContents(final URL openapiResource, final Path extractedResource) {
    final String contents;
    try {
      contents = com.google.common.io.Resources.toString(openapiResource, StandardCharsets.UTF_8);
      extractedResource.getParent().toFile().mkdirs();
      Files.writeString(extractedResource, contents);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Builder class for OpenApiSpecsExtractor */
  public static class OpenApiSpecsExtractorBuilder {
    private Path destinationDirectory;
    private boolean forceDeleteOnJvmExit = true;
    private boolean fixRelativeRefPaths = true;

    public OpenApiSpecsExtractorBuilder withDestinationDirectory(final Path destinationDirectory) {
      this.destinationDirectory = destinationDirectory;
      return this;
    }

    public OpenApiSpecsExtractorBuilder withFixRelativeRefPaths(final boolean fixRelativeRefPaths) {
      this.fixRelativeRefPaths = fixRelativeRefPaths;
      return this;
    }

    public OpenApiSpecsExtractorBuilder withForceDeleteOnJvmExit(
        final boolean forceDeleteOnJvmExit) {
      this.forceDeleteOnJvmExit = forceDeleteOnJvmExit;
      return this;
    }

    public OpenApiSpecsExtractor build() throws IOException {
      if (destinationDirectory == null) {
        this.destinationDirectory = Files.createTempDirectory("w3s_openapi_");
      }
      return new OpenApiSpecsExtractor(
          destinationDirectory, fixRelativeRefPaths, forceDeleteOnJvmExit);
    }
  }
}
