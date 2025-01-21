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
package tech.pegasys.web3signer.core.service.http.handlers;

import tech.pegasys.web3signer.signing.ArtifactSignerProvider;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

public class ReloadHandler implements Handler<RoutingContext> {
  private final List<ArtifactSignerProvider> orderedArtifactSignerProviders;

  public ReloadHandler(final List<ArtifactSignerProvider> orderedArtifactSignerProviders) {
    this.orderedArtifactSignerProviders = orderedArtifactSignerProviders;
  }

  @Override
  public void handle(final RoutingContext routingContext) {

    Executors.newSingleThreadExecutor()
        .submit(
            () ->
                orderedArtifactSignerProviders.forEach(
                    signer -> {
                      try {
                        signer.load().get();
                      } catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException(e);
                      }
                    }));
    routingContext.response().setStatusCode(200).end();
  }
}
