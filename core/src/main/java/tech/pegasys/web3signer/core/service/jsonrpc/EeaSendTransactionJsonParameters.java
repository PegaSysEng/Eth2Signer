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
import static tech.pegasys.web3signer.core.service.jsonrpc.RpcUtil.decodeBigInteger;
import static tech.pegasys.web3signer.core.service.jsonrpc.RpcUtil.fromRpcRequestToJsonParam;
import static tech.pegasys.web3signer.core.service.jsonrpc.RpcUtil.validateNotEmpty;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import org.web3j.utils.Base64String;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EeaSendTransactionJsonParameters {

  private final String sender;
  private final Base64String privateFrom;
  private final String restriction;

  private BigInteger gas;
  private BigInteger gasPrice;
  private BigInteger nonce;
  private BigInteger value;
  private String receiver;
  private String data;
  private Base64String privacyGroupId;
  private List<Base64String> privateFor;
  private BigInteger maxFeePerGas;
  private BigInteger maxPriorityFeePerGas;

  @JsonCreator
  public EeaSendTransactionJsonParameters(
      @JsonProperty("from") final String sender,
      @JsonProperty("privateFrom") final String privateFrom,
      @JsonProperty("restriction") final String restriction) {
    validateNotEmpty(sender);
    this.privateFrom = Base64String.wrap(privateFrom);
    this.restriction = restriction;
    this.sender = sender;
  }

  @JsonSetter("gas")
  public void gas(final String gas) {
    this.gas = decodeBigInteger(gas);
  }

  @JsonSetter("gasPrice")
  public void gasPrice(final String gasPrice) {
    this.gasPrice = decodeBigInteger(gasPrice);
  }

  @JsonSetter("nonce")
  public void nonce(final String nonce) {
    this.nonce = decodeBigInteger(nonce);
  }

  @JsonSetter("to")
  public void receiver(final String receiver) {
    this.receiver = receiver;
  }

  @JsonSetter("value")
  public void value(final String value) {
    validateValue(value);
    this.value = decodeBigInteger(value);
  }

  @JsonSetter("data")
  public void data(final String data) {
    this.data = data;
  }

  @JsonSetter("privateFor")
  public void privateFor(final String[] privateFor) {
    this.privateFor =
        Arrays.stream(privateFor).map(Base64String::wrap).collect(Collectors.toList());
  }

  @JsonSetter("privacyGroupId")
  public void privacyGroupId(final String privacyGroupId) {
    this.privacyGroupId = Base64String.wrap(privacyGroupId);
  }

  @JsonSetter("maxPriorityFeePerGas")
  public void maxPriorityFeePerGas(final String maxPriorityFeePerGas) {
    this.maxPriorityFeePerGas = decodeBigInteger(maxPriorityFeePerGas);
  }

  @JsonSetter("maxFeePerGas")
  public void maxFeePerGas(final String maxFeePerGas) {
    this.maxFeePerGas = decodeBigInteger(maxFeePerGas);
  }

  public Optional<String> data() {
    return Optional.ofNullable(data);
  }

  public Optional<BigInteger> gas() {
    return Optional.ofNullable(gas);
  }

  public Optional<BigInteger> gasPrice() {
    return Optional.ofNullable(gasPrice);
  }

  public Optional<String> receiver() {
    return Optional.ofNullable(receiver);
  }

  public Optional<BigInteger> value() {
    return Optional.ofNullable(value);
  }

  public Optional<BigInteger> nonce() {
    return Optional.ofNullable(nonce);
  }

  public Optional<List<Base64String>> privateFor() {
    return Optional.ofNullable(privateFor);
  }

  public Optional<Base64String> privacyGroupId() {
    return Optional.ofNullable(privacyGroupId);
  }

  public String sender() {
    return sender;
  }

  public Base64String privateFrom() {
    return privateFrom;
  }

  public String restriction() {
    return restriction;
  }

  public Optional<BigInteger> maxPriorityFeePerGas() {
    return Optional.ofNullable(maxPriorityFeePerGas);
  }

  public Optional<BigInteger> maxFeePerGas() {
    return Optional.ofNullable(maxFeePerGas);
  }

  public static EeaSendTransactionJsonParameters from(final JsonRpcRequest request) {
    return fromRpcRequestToJsonParam(EeaSendTransactionJsonParameters.class, request);
  }

  private void validateValue(final String value) {
    if (value != null && !decodeQuantity(value).equals(BigInteger.ZERO)) {
      throw new IllegalArgumentException(
          "Non-zero value, private transactions cannot transfer ether");
    }
  }
}
