/*
 * Copyright 2022 ConsenSys AG.
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
package tech.pegasys.web3signer.core.service.http.handlers.keymanager.delete;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tech.pegasys.web3signer.core.signing.ArtifactSigner;
import tech.pegasys.web3signer.core.signing.ArtifactSignerProvider;
import tech.pegasys.web3signer.slashingprotection.SlashingProtection;
import tech.pegasys.web3signer.slashingprotection.interchange.IncrementalExporter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeleteKeystoresProcessorTest {

  public static final String PUBLIC_KEY =
      "09b02f8a5fddd222ade4ea4528faefc399623af3f736be3c44f03e2df22fb792f3931a4d9573d333ca74343305762a753388c3422a86d98b713fc91c1ea04842";

  @Mock KeystoreFileManager keystoreFileManager;
  @Mock SlashingProtection slashingProtection;
  @Mock ArtifactSignerProvider artifactSignerProvider;
  @Mock ArtifactSigner signer;
  @Mock IncrementalExporter incrementalExporter;

  DeleteKeystoresProcessor processor;

  @BeforeEach
  void setup() {
    processor =
        new DeleteKeystoresProcessor(
            keystoreFileManager, Optional.of(slashingProtection), artifactSignerProvider);
    when(slashingProtection.createIncrementalExporter(any())).thenReturn(incrementalExporter);
  }

  @Test
  void testSuccess() throws IOException {
    when(artifactSignerProvider.getSigner(any())).thenReturn(Optional.of(signer));
    when(artifactSignerProvider.removeSigner(any()))
        .thenReturn(CompletableFuture.completedFuture(null));
    doNothing().when(keystoreFileManager).deleteKeystoreFiles(any());

    final DeleteKeystoresRequestBody requestBody =
        new DeleteKeystoresRequestBody(List.of(PUBLIC_KEY));
    final DeleteKeystoresResponse response = processor.process(requestBody);
    assertThat(response.getData().size()).isEqualTo(1);
    assertThat(response.getData().get(0).getMessage()).isEqualTo("");
    assertThat(response.getData().get(0).getStatus()).isEqualTo(DeleteKeystoreStatus.DELETED);
  }

  @Test
  void validatorIsDisabledWhenDeleteIsSuccessful() throws IOException {
    when(artifactSignerProvider.getSigner(any())).thenReturn(Optional.of(signer));
    when(artifactSignerProvider.removeSigner(any()))
        .thenReturn(CompletableFuture.completedFuture(null));
    doNothing().when(keystoreFileManager).deleteKeystoreFiles(any());

    final DeleteKeystoresRequestBody requestBody =
        new DeleteKeystoresRequestBody(List.of(PUBLIC_KEY));
    processor.process(requestBody);
    verify(slashingProtection).updateValidatorEnabledStatus(Bytes.fromHexString(PUBLIC_KEY), false);
  }

  @Test
  void testSignerNotFound() {
    when(artifactSignerProvider.getSigner(any())).thenReturn(Optional.empty());
    when(slashingProtection.hasSlashingProtectionDataFor(any())).thenReturn(false);

    final DeleteKeystoresRequestBody requestBody =
        new DeleteKeystoresRequestBody(List.of(PUBLIC_KEY));
    final DeleteKeystoresResponse response = processor.process(requestBody);
    assertThat(response.getData().size()).isEqualTo(1);
    assertThat(response.getData().get(0).getMessage()).isEqualTo("");
    assertThat(response.getData().get(0).getStatus()).isEqualTo(DeleteKeystoreStatus.NOT_FOUND);
    verify(slashingProtection, never())
        .updateValidatorEnabledStatus(eq(Bytes.fromHexString(PUBLIC_KEY)), anyBoolean());
  }

  @Test
  void testSignerNotActive() {
    when(artifactSignerProvider.getSigner(any())).thenReturn(Optional.empty());
    when(slashingProtection.hasSlashingProtectionDataFor(any())).thenReturn(true);

    final DeleteKeystoresRequestBody requestBody =
        new DeleteKeystoresRequestBody(List.of(PUBLIC_KEY));
    final DeleteKeystoresResponse response = processor.process(requestBody);
    assertThat(response.getData().size()).isEqualTo(1);
    assertThat(response.getData().get(0).getMessage()).isEqualTo("");
    assertThat(response.getData().get(0).getStatus()).isEqualTo(DeleteKeystoreStatus.NOT_ACTIVE);
    verify(slashingProtection, never())
        .updateValidatorEnabledStatus(eq(Bytes.fromHexString(PUBLIC_KEY)), anyBoolean());
  }

  @Test
  void testIOException() throws IOException {
    when(artifactSignerProvider.getSigner(any())).thenReturn(Optional.of(signer));
    when(artifactSignerProvider.removeSigner(any()))
        .thenReturn(CompletableFuture.completedFuture(null));
    doThrow(new IOException("io error")).when(keystoreFileManager).deleteKeystoreFiles(any());

    final DeleteKeystoresRequestBody requestBody =
        new DeleteKeystoresRequestBody(List.of(PUBLIC_KEY));
    final DeleteKeystoresResponse response = processor.process(requestBody);
    assertThat(response.getData().size()).isEqualTo(1);
    assertThat(response.getData().get(0).getMessage())
        .isEqualTo("Error deleting keystore file: io error");
    assertThat(response.getData().get(0).getStatus()).isEqualTo(DeleteKeystoreStatus.ERROR);
  }

  @Test
  void testInterruptedException() {
    when(artifactSignerProvider.getSigner(any())).thenReturn(Optional.of(signer));
    when(artifactSignerProvider.removeSigner(any()))
        .thenReturn(CompletableFuture.failedFuture(new InterruptedException("interrupted")));

    final DeleteKeystoresRequestBody requestBody =
        new DeleteKeystoresRequestBody(List.of(PUBLIC_KEY));
    final DeleteKeystoresResponse response = processor.process(requestBody);
    assertThat(response.getData().size()).isEqualTo(1);
    assertThat(response.getData().get(0).getMessage())
        .isEqualTo("Error deleting keystore file: java.lang.InterruptedException: interrupted");
    assertThat(response.getData().get(0).getStatus()).isEqualTo(DeleteKeystoreStatus.ERROR);
  }

  @Test
  void disabledValidatorRemainsDisabledWhenDeleteFails() throws IOException {
    when(artifactSignerProvider.getSigner(any())).thenReturn(Optional.of(signer));
    when(artifactSignerProvider.removeSigner(any()))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(slashingProtection.isEnabledValidator(Bytes.fromHexString(PUBLIC_KEY))).thenReturn(false);
    doThrow(new IOException("io error")).when(keystoreFileManager).deleteKeystoreFiles(any());

    final DeleteKeystoresRequestBody requestBody =
        new DeleteKeystoresRequestBody(List.of(PUBLIC_KEY));
    processor.process(requestBody);

    verify(slashingProtection, times(2))
        .updateValidatorEnabledStatus(Bytes.fromHexString(PUBLIC_KEY), false);
    verify(slashingProtection, never())
        .updateValidatorEnabledStatus(Bytes.fromHexString(PUBLIC_KEY), true);
  }

  @Test
  void enabledValidatorRemainsEnabledWhenDeleteFails() throws IOException {
    when(artifactSignerProvider.getSigner(any())).thenReturn(Optional.of(signer));
    when(artifactSignerProvider.removeSigner(any()))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(slashingProtection.isEnabledValidator(Bytes.fromHexString(PUBLIC_KEY))).thenReturn(true);
    doThrow(new IOException("io error")).when(keystoreFileManager).deleteKeystoreFiles(any());

    final DeleteKeystoresRequestBody requestBody =
        new DeleteKeystoresRequestBody(List.of(PUBLIC_KEY));
    processor.process(requestBody);

    final InOrder inorder = Mockito.inOrder(slashingProtection);
    inorder
        .verify(slashingProtection)
        .updateValidatorEnabledStatus(Bytes.fromHexString(PUBLIC_KEY), false);
    inorder
        .verify(slashingProtection)
        .updateValidatorEnabledStatus(Bytes.fromHexString(PUBLIC_KEY), true);
  }

  @Test
  void testSlashingExportException() throws IOException {
    when(artifactSignerProvider.getSigner(any())).thenReturn(Optional.of(signer));
    when(artifactSignerProvider.removeSigner(any()))
        .thenReturn(CompletableFuture.completedFuture(null));
    doNothing().when(keystoreFileManager).deleteKeystoreFiles(any());
    doThrow(new RuntimeException("db error")).when(incrementalExporter).export(any());

    final DeleteKeystoresRequestBody requestBody =
        new DeleteKeystoresRequestBody(List.of(PUBLIC_KEY));
    final DeleteKeystoresResponse response = processor.process(requestBody);
    assertThat(response.getData().size()).isEqualTo(1);
    assertThat(response.getData().get(0).getMessage())
        .isEqualTo("Error exporting slashing data: db error");
    assertThat(response.getData().get(0).getStatus()).isEqualTo(DeleteKeystoreStatus.ERROR);
  }

  @Test
  void enabledValidatorRemainsEnabledWhenSlashingExportFails() throws IOException {
    when(artifactSignerProvider.getSigner(any())).thenReturn(Optional.of(signer));
    when(artifactSignerProvider.removeSigner(any()))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(slashingProtection.isEnabledValidator(Bytes.fromHexString(PUBLIC_KEY))).thenReturn(true);
    doNothing().when(keystoreFileManager).deleteKeystoreFiles(any());
    doThrow(new RuntimeException("db error"))
        .when(slashingProtection)
        .exportDataWithFilter(any(), any());

    final DeleteKeystoresRequestBody requestBody =
        new DeleteKeystoresRequestBody(List.of(PUBLIC_KEY));
    processor.process(requestBody);

    final InOrder inorder = Mockito.inOrder(slashingProtection);
    inorder
        .verify(slashingProtection)
        .updateValidatorEnabledStatus(Bytes.fromHexString(PUBLIC_KEY), false);
    inorder
        .verify(slashingProtection)
        .updateValidatorEnabledStatus(Bytes.fromHexString(PUBLIC_KEY), true);
  }

  @Test
  void disabledValidatorRemainsDisabledWhenSlashingExportFails() throws IOException {
    when(artifactSignerProvider.getSigner(any())).thenReturn(Optional.of(signer));
    when(artifactSignerProvider.removeSigner(any()))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(slashingProtection.isEnabledValidator(Bytes.fromHexString(PUBLIC_KEY))).thenReturn(false);
    doNothing().when(keystoreFileManager).deleteKeystoreFiles(any());
    doThrow(new RuntimeException("db error"))
        .when(slashingProtection)
        .exportDataWithFilter(any(), any());

    final DeleteKeystoresRequestBody requestBody =
        new DeleteKeystoresRequestBody(List.of(PUBLIC_KEY));
    processor.process(requestBody);

    verify(slashingProtection, times(2))
        .updateValidatorEnabledStatus(Bytes.fromHexString(PUBLIC_KEY), false);
    verify(slashingProtection, never())
        .updateValidatorEnabledStatus(Bytes.fromHexString(PUBLIC_KEY), true);
  }
}
