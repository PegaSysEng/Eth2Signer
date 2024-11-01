/*
 * Copyright 2024 ConsenSys AG.
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
package tech.pegasys.web3signer.core.routes.eth2;

import tech.pegasys.teku.spec.Spec;
import tech.pegasys.web3signer.core.Context;
import tech.pegasys.web3signer.core.routes.Web3SignerRoute;
import tech.pegasys.web3signer.core.service.http.handlers.commitboost.CommitBoostRequestSignatureHandler;
import tech.pegasys.web3signer.signing.ArtifactSignerProvider;
import tech.pegasys.web3signer.signing.config.CommitBoostParameters;
import tech.pegasys.web3signer.signing.config.DefaultArtifactSignerProvider;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;

public class CommitBoostRequestSignatureRoute implements Web3SignerRoute {
  private static final String PATH = "/signer/v1/request_signature";
  private final Context context;
  private final CommitBoostParameters commitBoostParameters;
  private final Spec eth2Spec;
  private final ArtifactSignerProvider artifactSignerProvider;

  public CommitBoostRequestSignatureRoute(
      final Context context,
      final CommitBoostParameters commitBoostParameters,
      final Spec eth2Spec) {
    this.context = context;
    this.commitBoostParameters = commitBoostParameters;
    this.eth2Spec = eth2Spec;

    // there should be only one DefaultArtifactSignerProvider in eth2 mode
    artifactSignerProvider =
        context.getArtifactSignerProviders().stream()
            .filter(p -> p instanceof DefaultArtifactSignerProvider)
            .findFirst()
            .orElseThrow();
  }

  @Override
  public void register() {
    context
        .getRouter()
        .route(HttpMethod.POST, PATH)
        .blockingHandler(
            new CommitBoostRequestSignatureHandler(
                artifactSignerProvider, commitBoostParameters, eth2Spec),
            false)
        .failureHandler(context.getErrorHandler())
        .failureHandler(
            ctx -> {
              final int statusCode = ctx.statusCode();
              if (statusCode == 400) {
                ctx.response()
                    .setStatusCode(statusCode)
                    .end(
                        new JsonObject()
                            .put("code", statusCode)
                            .put("message", "Bad Request")
                            .encode());
              } else if (statusCode == 404) {
                ctx.response()
                    .setStatusCode(statusCode)
                    .end(
                        new JsonObject()
                            .put("code", statusCode)
                            .put("message", "Identifier not found.")
                            .encode());
              } else if (statusCode == 500) {
                ctx.response()
                    .setStatusCode(statusCode)
                    .end(
                        new JsonObject()
                            .put("code", statusCode)
                            .put("message", "Internal Server Error")
                            .encode());
              } else {
                ctx.next(); // go to global failure handler
              }
            });
  }
}
