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

import static java.util.Collections.singletonList;
import static tech.pegasys.web3signer.core.jsonrpcproxy.model.jsonrpc.EeaSendTransaction.DEFAULT_VALUE;
import static tech.pegasys.web3signer.core.jsonrpcproxy.model.jsonrpc.EeaSendTransaction.PRIVACY_GROUP_ID;
import static tech.pegasys.web3signer.core.jsonrpcproxy.model.jsonrpc.EeaSendTransaction.PRIVATE_FOR;
import static tech.pegasys.web3signer.core.jsonrpcproxy.model.jsonrpc.EeaSendTransaction.PRIVATE_FROM;
import static tech.pegasys.web3signer.core.jsonrpcproxy.model.jsonrpc.EeaSendTransaction.UNLOCKED_ACCOUNT;

import java.util.List;
import java.util.Optional;

public class PrivateTransaction {
  // Values are held using a value holder as an Optional cannot contain a null value and we want to
  // represent missing values using Optional.empty, null values and non-null values
  private final Optional<ValueHolder<String>> from;
  private final Optional<ValueHolder<String>> nonce;
  private final Optional<ValueHolder<String>> gasPrice;
  private final Optional<ValueHolder<String>> gas;
  private final Optional<ValueHolder<String>> to;
  private final Optional<ValueHolder<String>> value;
  private final Optional<ValueHolder<String>> data;
  private final Optional<ValueHolder<String>> privateFrom;
  private final Optional<ValueHolder<List<String>>> privateFor;
  private final Optional<ValueHolder<String>> restriction;
  private final Optional<ValueHolder<String>> privacyGroupId;

  public PrivateTransaction(
      final Optional<ValueHolder<String>> from,
      final Optional<ValueHolder<String>> nonce,
      final Optional<ValueHolder<String>> gasPrice,
      final Optional<ValueHolder<String>> gas,
      final Optional<ValueHolder<String>> to,
      final Optional<ValueHolder<String>> value,
      final Optional<ValueHolder<String>> data,
      final Optional<ValueHolder<String>> privateFrom,
      final Optional<ValueHolder<List<String>>> privateFor,
      final Optional<ValueHolder<String>> restriction,
      final Optional<ValueHolder<String>> privacyGroupId) {
    this.from = from;
    this.nonce = nonce;
    this.gasPrice = gasPrice;
    this.gas = gas;
    this.to = to;
    this.value = value;
    this.data = data;
    this.privateFrom = privateFrom;
    this.privateFor = privateFor;
    this.restriction = restriction;
    this.privacyGroupId = privacyGroupId;
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

  public Optional<ValueHolder<String>> getPrivateFrom() {
    return privateFrom;
  }

  public Optional<ValueHolder<List<String>>> getPrivateFor() {
    return privateFor;
  }

  public Optional<ValueHolder<String>> getRestriction() {
    return restriction;
  }

  public Optional<ValueHolder<String>> getPrivacyGroupId() {
    return privacyGroupId;
  }

  public static Builder defaultTransaction() {
    return new Builder()
        .withFrom(UNLOCKED_ACCOUNT)
        .withNonce("0xe04d296d2460cfb8472af2c5fd05b5a214109c25688d3704aed5484f9a7792f2")
        .withGasPrice("0x9184e72a000")
        .withGas("0x76c0")
        .withTo("0xd46e8dd67c5d32be8058bb8eb970870f07244567")
        .withValue(DEFAULT_VALUE)
        .withData(
            "0xd46e8dd67c5d32be8d46e8dd67c5d32be8058bb8eb970870f072445675058bb8eb970870f072445675")
        .withPrivateFrom(PRIVATE_FROM)
        .withPrivateFor(singletonList(PRIVATE_FOR))
        .withRestriction("restricted");
  }

  public static Builder privacyGroupIdTransaction() {
    return new Builder()
        .withFrom(UNLOCKED_ACCOUNT)
        .withNonce("0xe04d296d2460cfb8472af2c5fd05b5a214109c25688d3704aed5484f9a7792f2")
        .withGasPrice("0x9184e72a000")
        .withGas("0x76c0")
        .withTo("0xd46e8dd67c5d32be8058bb8eb970870f07244567")
        .withValue(DEFAULT_VALUE)
        .withData(
            "0xd46e8dd67c5d32be8d46e8dd67c5d32be8058bb8eb970870f072445675058bb8eb970870f072445675")
        .withPrivateFrom(PRIVATE_FROM)
        .withPrivacyGroupId(PRIVACY_GROUP_ID)
        .withRestriction("restricted");
  }

  public static class Builder {
    private Optional<ValueHolder<String>> from = Optional.empty();
    private Optional<ValueHolder<String>> nonce = Optional.empty();
    private Optional<ValueHolder<String>> gasPrice = Optional.empty();
    private Optional<ValueHolder<String>> gas = Optional.empty();
    private Optional<ValueHolder<String>> to = Optional.empty();
    private Optional<ValueHolder<String>> value = Optional.empty();
    private Optional<ValueHolder<String>> data = Optional.empty();
    private Optional<ValueHolder<String>> privateFrom = Optional.empty();
    private Optional<ValueHolder<List<String>>> privateFor = Optional.empty();
    private Optional<ValueHolder<String>> restriction = Optional.empty();
    private Optional<ValueHolder<String>> privacyGroupId = Optional.empty();

    public Builder withFrom(final String from) {
      this.from = createValue(from);
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

    public Builder withPrivateFrom(final String privateFrom) {
      this.privateFrom = createValue(privateFrom);
      return this;
    }

    public Builder missingPrivateFrom() {
      this.privateFrom = Optional.empty();
      return this;
    }

    public Builder withPrivateFor(final List<String> privateFor) {
      this.privateFor = createValue(privateFor);
      return this;
    }

    public Builder missingPrivateFor() {
      this.privateFor = Optional.empty();
      return this;
    }

    public Builder withPrivacyGroupId(final String privacyGroupId) {
      this.privacyGroupId = createValue(privacyGroupId);
      return this;
    }

    public Builder missingPrivacyGroupId() {
      this.privacyGroupId = Optional.empty();
      return this;
    }

    public Builder withRestriction(final String restriction) {
      this.restriction = createValue(restriction);
      return this;
    }

    public Builder missingRestriction() {
      this.restriction = Optional.empty();
      return this;
    }

    public PrivateTransaction build() {
      return new PrivateTransaction(
          from,
          nonce,
          gasPrice,
          gas,
          to,
          value,
          data,
          privateFrom,
          privateFor,
          restriction,
          privacyGroupId);
    }

    private <T> Optional<ValueHolder<T>> createValue(final T from) {
      return Optional.of(new ValueHolder<>(from));
    }
  }
}
