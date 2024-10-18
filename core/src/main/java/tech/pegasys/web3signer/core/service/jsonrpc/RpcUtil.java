/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.web3signer.core.service.jsonrpc;

import static org.web3j.utils.Numeric.decodeQuantity;

import tech.pegasys.web3signer.core.service.jsonrpc.response.JsonRpcError;
import tech.pegasys.web3signer.core.service.jsonrpc.response.JsonRpcErrorResponse;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import org.apache.tuweni.bytes.Bytes;

public class RpcUtil {
  public static final String JSON_RPC_VERSION = "2.0";

  static <T> T fromRpcRequestToJsonParam(final Class<T> type, final JsonRpcRequest request) {

    final Object object;
    final Object params = request.getParams();
    if (params instanceof List<?> paramList) {
      if (paramList.size() != 1) {
        throw new IllegalArgumentException(
            type.getSimpleName()
                + " json Rpc requires a single parameter, request contained "
                + paramList.size());
      }
      object = paramList.get(0);
    } else {
      object = params;
    }

    final JsonObject receivedParams = JsonObject.mapFrom(object);

    return receivedParams.mapTo(type);
  }

  static void validateNotEmpty(final String value) {
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Value cannot be empty");
    }
  }

  static BigInteger decodeBigInteger(final String value) {
    return value == null ? null : decodeQuantity(value);
  }

  static List<Bytes> decodeBytesList(final List<String> value) {
    return value.stream().map(Bytes::fromHexString).collect(Collectors.toList());
  }

  public static JsonRpcError determineErrorCode(final String body, final JsonDecoder decoder) {
    try {
      final JsonRpcErrorResponse response =
          decoder.decodeValue(Buffer.buffer(body), JsonRpcErrorResponse.class);
      return response.getError();
    } catch (final DecodeException e) {
      return JsonRpcError.INTERNAL_ERROR;
    }
  }
}
