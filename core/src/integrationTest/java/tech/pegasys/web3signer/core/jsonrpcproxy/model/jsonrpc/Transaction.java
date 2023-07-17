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
package tech.pegasys.web3signer.core.jsonrpcproxy.model.jsonrpc;

import static tech.pegasys.web3signer.core.jsonrpcproxy.model.jsonrpc.EeaSendTransaction.UNLOCKED_ACCOUNT;
import static tech.pegasys.web3signer.core.jsonrpcproxy.model.jsonrpc.SendTransaction.FIELD_VALUE_DEFAULT;

import java.util.Optional;

public class Transaction {
  // Values are held using a value holder as an Optional cannot contain a null value and we want to
  // represent missing values using Optional.empty, null values and non-null values
  private final Optional<ValueHolder<String>> from;
  private final Optional<ValueHolder<String>> nonce;
  private final Optional<ValueHolder<String>> gasPrice;
  private final Optional<ValueHolder<String>> gas;
  private final Optional<ValueHolder<String>> to;
  private final Optional<ValueHolder<String>> value;
  private final Optional<ValueHolder<String>> data;

  public Transaction(
      final Optional<ValueHolder<String>> from,
      final Optional<ValueHolder<String>> nonce,
      final Optional<ValueHolder<String>> gasPrice,
      final Optional<ValueHolder<String>> gas,
      final Optional<ValueHolder<String>> to,
      final Optional<ValueHolder<String>> value,
      final Optional<ValueHolder<String>> data) {
    this.from = from;
    this.nonce = nonce;
    this.gasPrice = gasPrice;
    this.gas = gas;
    this.to = to;
    this.value = value;
    this.data = data;
  }

  public Optional<ValueHolder<String>> getFrom() {
    return from;
  }

  public Optional<ValueHolder<String>> getNonce() {
    return nonce;
  }

  public Optional<ValueHolder<String>> getGasPrice() {
    return gasPrice;
  }

  public Optional<ValueHolder<String>> getGas() {
    return gas;
  }

  public Optional<ValueHolder<String>> getTo() {
    return to;
  }

  public Optional<ValueHolder<String>> getValue() {
    return value;
  }

  public Optional<ValueHolder<String>> getData() {
    return data;
  }

  public static Builder smartContract() {
    return new Builder()
        .withFrom(UNLOCKED_ACCOUNT)
        .withGas("0x76c0")
        .withGasPrice("0x9184e72a000")
        .withValue(FIELD_VALUE_DEFAULT)
        .withNonce("0x1")
        .withData(
            "0x608060405234801561001057600080fd5b50604051602080610114833981016040525160005560e1806100336000396000f30060806040526004361060525763ffffffff7c01000000000000000000000000000000000000000000000000000000006000350416632a1afcd98114605757806360fe47b114607b5780636d4ce63c146092575b600080fd5b348015606257600080fd5b50606960a4565b60408051918252519081900360200190f35b348015608657600080fd5b50609060043560aa565b005b348015609d57600080fd5b50606960af565b60005481565b600055565b600054905600a165627a7a72305820ade758a90b7d6841e99ca64c339eda0498d86ec9a97d5dcdeb3f12e3500079130029000000000000000000000000000000000000000000000000000000000000000a");
  }

  public static Builder defaultTransaction() {
    return new Builder()
        .withFrom(UNLOCKED_ACCOUNT)
        .withNonce("0xe04d296d2460cfb8472af2c5fd05b5a214109c25688d3704aed5484f9a7792f2")
        .withGasPrice("0x9184e72a000")
        .withGas("0x76c0")
        .withTo("0xd46e8dd67c5d32be8058bb8eb970870f07244567")
        .withValue("0x9184e72a")
        .withData(
            "0xd46e8dd67c5d32be8d46e8dd67c5d32be8058bb8eb970870f072445675058bb8eb970870f072445675");
  }

  public static class Builder {
    private Optional<ValueHolder<String>> from = Optional.empty();
    private Optional<ValueHolder<String>> nonce = Optional.empty();
    private Optional<ValueHolder<String>> gasPrice = Optional.empty();
    private Optional<ValueHolder<String>> gas = Optional.empty();
    private Optional<ValueHolder<String>> to = Optional.empty();
    private Optional<ValueHolder<String>> value = Optional.empty();
    private Optional<ValueHolder<String>> data = Optional.empty();

    public Builder withFrom(final String from) {
      this.from = createValue(from);
      return this;
    }

    public Builder missingFrom() {
      this.from = Optional.empty();
      return this;
    }

    public Builder withNonce(final String nonce) {
      this.nonce = createValue(nonce);
      return this;
    }

    public Builder missingNonce() {
      this.nonce = Optional.empty();
      return this;
    }

    public Builder withGasPrice(final String gasPrice) {
      this.gasPrice = createValue(gasPrice);
      return this;
    }

    public Builder missingGasPrice() {
      this.gasPrice = Optional.empty();
      return this;
    }

    public Builder withGas(final String gas) {
      this.gas = createValue(gas);
      return this;
    }

    public Builder missingGas() {
      this.gas = Optional.empty();
      return this;
    }

    public Builder withTo(final String to) {
      this.to = createValue(to);
      return this;
    }

    public Builder missingTo() {
      this.to = Optional.empty();
      return this;
    }

    public Builder withValue(final String value) {
      this.value = createValue(value);
      return this;
    }

    public Builder missingValue() {
      this.value = Optional.empty();
      return this;
    }

    public Builder withData(final String data) {
      this.data = createValue(data);
      return this;
    }

    public Builder missingData() {
      this.data = Optional.empty();
      return this;
    }

    public Transaction build() {
      return new Transaction(from, nonce, gasPrice, gas, to, value, data);
    }

    private <T> Optional<ValueHolder<T>> createValue(final T from) {
      return Optional.of(new ValueHolder<>(from));
    }
  }
}
