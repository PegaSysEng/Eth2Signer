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
package tech.pegasys.web3signer.core.service.jsonrpc;

import static tech.pegasys.web3signer.core.service.jsonrpc.RpcUtil.decodeBigInteger;
import static tech.pegasys.web3signer.core.service.jsonrpc.RpcUtil.validateNotEmpty;

import java.math.BigInteger;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EthSendTransactionJsonParameters {
  private final String sender;
  private BigInteger gas;
  private BigInteger gasPrice;
  private BigInteger nonce;
  private BigInteger value;
  private String receiver;
  private String data;
  private BigInteger maxFeePerGas;
  private BigInteger maxPriorityFeePerGas;

  @JsonCreator
  public EthSendTransactionJsonParameters(@JsonProperty("from") final String sender) {
    validateNotEmpty(sender);
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
    this.value = decodeBigInteger(value);
  }

  @JsonSetter("data")
  public void data(final String data) {
    this.data = data;
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

  public String sender() {
    return sender;
  }

  public Optional<BigInteger> maxPriorityFeePerGas() {
    return Optional.ofNullable(maxPriorityFeePerGas);
  }

  public Optional<BigInteger> maxFeePerGas() {
    return Optional.ofNullable(maxFeePerGas);
  }
}
