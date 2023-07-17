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

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import tech.pegasys.web3signer.core.Eth1Runner;
import tech.pegasys.web3signer.core.service.jsonrpc.handlers.sendtransaction.transaction.TransactionFactory;

import java.math.BigInteger;
import java.util.Optional;

import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.web3j.utils.Base64String;

public class EeaSendTransactionJsonParametersTest {

  private TransactionFactory factory;

  @BeforeEach
  public void setup() {
    // NOTE: the factory has been configured as per its use in the application.
    factory = new TransactionFactory(1337L, Eth1Runner.createJsonDecoder(), null);
  }

  @Test
  public void transactionStoredInJsonArrayCanBeDecoded() {
    final JsonObject parameters = validEeaTransactionParameters();

    final JsonRpcRequest request = wrapParametersInRequest(parameters);
    final EeaSendTransactionJsonParameters txnParams =
        factory.fromRpcRequestToJsonParam(EeaSendTransactionJsonParameters.class, request);

    assertThat(txnParams.gas()).isEqualTo(getStringAsOptionalBigInteger(parameters, "gas"));
    assertThat(txnParams.gasPrice())
        .isEqualTo(getStringAsOptionalBigInteger(parameters, "gasPrice"));
    assertThat(txnParams.nonce()).isEqualTo(getStringAsOptionalBigInteger(parameters, "nonce"));
    assertThat(txnParams.receiver()).isEqualTo(Optional.of(parameters.getString("to")));
    assertThat(txnParams.value()).isEqualTo(getStringAsOptionalBigInteger(parameters, "value"));
    assertThat(txnParams.privateFrom())
        .isEqualTo(Base64String.wrap(parameters.getString("privateFrom")));
    assertThat(txnParams.privateFor().get())
        .containsExactly(Base64String.wrap(parameters.getJsonArray("privateFor").getString(0)));
    assertThat(txnParams.restriction()).isEqualTo(parameters.getString("restriction"));
  }

  @Test
  public void eip1559TransactionStoredInJsonArrayCanBeDecoded() {
    final JsonObject parameters = validEip1559EeaTransactionParameters();

    final JsonRpcRequest request = wrapParametersInRequest(parameters);
    final EeaSendTransactionJsonParameters txnParams =
        factory.fromRpcRequestToJsonParam(EeaSendTransactionJsonParameters.class, request);

    assertThat(txnParams.gas()).isEqualTo(getStringAsOptionalBigInteger(parameters, "gas"));
    assertThat(txnParams.gasPrice()).isEmpty();
    assertThat(txnParams.nonce()).isEqualTo(getStringAsOptionalBigInteger(parameters, "nonce"));
    assertThat(txnParams.receiver()).isEqualTo(Optional.of(parameters.getString("to")));
    assertThat(txnParams.value()).isEqualTo(getStringAsOptionalBigInteger(parameters, "value"));
    assertThat(txnParams.privateFrom())
        .isEqualTo(Base64String.wrap(parameters.getString("privateFrom")));
    assertThat(txnParams.privateFor().get())
        .containsExactly(Base64String.wrap(parameters.getJsonArray("privateFor").getString(0)));
    assertThat(txnParams.restriction()).isEqualTo(parameters.getString("restriction"));
    assertThat(txnParams.maxPriorityFeePerGas())
        .isEqualTo(getStringAsOptionalBigInteger(parameters, "maxPriorityFeePerGas"));
    assertThat(txnParams.maxFeePerGas())
        .isEqualTo(getStringAsOptionalBigInteger(parameters, "maxFeePerGas"));
  }

  @Test
  public void transactionNotStoredInJsonArrayCanBeDecoded() {
    final JsonObject parameters = validEeaTransactionParameters();

    final JsonRpcRequest request = wrapParametersInRequest(parameters);
    final EeaSendTransactionJsonParameters txnParams =
        factory.fromRpcRequestToJsonParam(EeaSendTransactionJsonParameters.class, request);

    assertThat(txnParams.gas()).isEqualTo(getStringAsOptionalBigInteger(parameters, "gas"));
    assertThat(txnParams.gasPrice())
        .isEqualTo(getStringAsOptionalBigInteger(parameters, "gasPrice"));
    assertThat(txnParams.nonce()).isEqualTo(getStringAsOptionalBigInteger(parameters, "nonce"));
    assertThat(txnParams.receiver()).isEqualTo(Optional.of(parameters.getString("to")));
    assertThat(txnParams.value()).isEqualTo(getStringAsOptionalBigInteger(parameters, "value"));
    assertThat(txnParams.privateFrom())
        .isEqualTo(Base64String.wrap(parameters.getString("privateFrom")));
    assertThat(txnParams.privateFor().get())
        .containsExactly(Base64String.wrap(parameters.getJsonArray("privateFor").getString(0)));
    assertThat(txnParams.restriction()).isEqualTo(parameters.getString("restriction"));
  }

  @Test
  public void transactionWithNonZeroValueFails() {
    final JsonObject parameters = validEeaTransactionParameters();
    parameters.put("value", "0x9184e72a");

    final JsonRpcRequest request = wrapParametersInRequest(parameters);

    assertThatExceptionOfType(DecodeException.class)
        .isThrownBy(
            () ->
                factory.fromRpcRequestToJsonParam(EeaSendTransactionJsonParameters.class, request));
  }

  @Test
  public void transactionWithInvalidPrivateFromThrowsIllegalArgumentException() {
    final JsonObject parameters = validEeaTransactionParameters();
    parameters.put("privateFrom", "invalidThirtyTwoByteData=");

    final JsonRpcRequest request = wrapParametersInRequest(parameters);

    assertThatExceptionOfType(DecodeException.class)
        .isThrownBy(
            () ->
                factory.fromRpcRequestToJsonParam(EeaSendTransactionJsonParameters.class, request));
  }

  @Test
  public void transactionWithInvalidPrivateForThrowsIllegalArgumentException() {
    final JsonObject parameters = validEeaTransactionParameters();
    parameters.put("privateFor", singletonList("invalidThirtyTwoByteData="));

    final JsonRpcRequest request = wrapParametersInRequest(parameters);

    assertThatExceptionOfType(DecodeException.class)
        .isThrownBy(
            () ->
                factory.fromRpcRequestToJsonParam(EeaSendTransactionJsonParameters.class, request));
  }

  @Test
  public void transactionWithInvalidRestrictionCanBeDecoded() {
    final JsonObject parameters = validEeaTransactionParameters();
    parameters.put("restriction", "invalidRestriction");

    final JsonRpcRequest request = wrapParametersInRequest(parameters);
    final EeaSendTransactionJsonParameters txnParams =
        factory.fromRpcRequestToJsonParam(EeaSendTransactionJsonParameters.class, request);

    assertThat(txnParams.restriction()).isEqualTo("invalidRestriction");
  }

  @Test
  public void transactionWithInvalidFromCanBeDecoded() {
    final JsonObject parameters = validEeaTransactionParameters();
    parameters.put("from", "invalidFromAddress");

    final JsonRpcRequest request = wrapParametersInRequest(parameters);
    final EeaSendTransactionJsonParameters txnParams =
        factory.fromRpcRequestToJsonParam(EeaSendTransactionJsonParameters.class, request);

    assertThat(txnParams.sender()).isEqualTo("invalidFromAddress");
  }

  @Test
  public void transactionWithInvalidToCanBeDecoded() {
    final JsonObject parameters = validEeaTransactionParameters();
    parameters.put("to", "invalidToAddress");

    final JsonRpcRequest request = wrapParametersInRequest(parameters);
    final EeaSendTransactionJsonParameters txnParams =
        factory.fromRpcRequestToJsonParam(EeaSendTransactionJsonParameters.class, request);

    assertThat(txnParams.receiver()).contains("invalidToAddress");
  }

  private Optional<BigInteger> getStringAsOptionalBigInteger(
      final JsonObject object, final String key) {
    final String value = object.getString(key);
    return Optional.of(new BigInteger(value.substring(2), 16));
  }

  private JsonObject validEeaTransactionParameters() {
    final JsonObject parameters = new JsonObject();
    parameters.put("from", "0xb60e8dd61c5d32be8058bb8eb970870f07233155");
    parameters.put("to", "0xd46e8dd67c5d32be8058bb8eb970870f07244567");
    parameters.put("nonce", "0x1");
    parameters.put("gas", "0x76c0");
    parameters.put("gasPrice", "0x9184e72a000");
    parameters.put("value", "0x0");
    parameters.put(
        "data",
        "0xd46e8dd67c5d32be8d46e8dd67c5d32be8058bb8eb970870f072445675058bb8eb970870f072445675");
    parameters.put("privateFrom", "ZlapEsl9qDLPy/e88+/6yvCUEVIvH83y0N4A6wHuKXI=");
    parameters.put("privateFor", singletonList("GV8m0VZAccYGAAYMBuYQtKEj0XtpXeaw2APcoBmtA2w="));
    parameters.put("restriction", "restricted");

    return parameters;
  }

  private JsonObject validEip1559EeaTransactionParameters() {
    final JsonObject parameters = validEeaTransactionParameters();
    parameters.put("maxFeePerGas", "0x9184e72a000");
    parameters.put("maxPriorityFeePerGas", "0x9184e72a000");
    parameters.put("gasPrice", null);

    return parameters;
  }

  private <T> JsonRpcRequest wrapParametersInRequest(final T parameters) {
    final JsonObject input = new JsonObject();
    input.put("jsonrpc", 2.0);
    input.put("method", "mine");
    input.put("params", parameters);

    return input.mapTo(JsonRpcRequest.class);
  }
}
