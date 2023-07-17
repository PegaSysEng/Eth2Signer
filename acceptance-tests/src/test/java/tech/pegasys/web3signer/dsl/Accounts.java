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
package tech.pegasys.web3signer.dsl;

import static tech.pegasys.web3signer.dsl.utils.ExceptionUtils.failOnIOException;

import java.math.BigInteger;
import java.util.List;

public class Accounts {

  /** Private key: 8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63 */
  private static final String GENESIS_ACCOUNT_ONE_PUBLIC_KEY =
      "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73";

  public static final String GENESIS_ACCOUNT_ONE_PASSWORD = "pass";

  private final Account benefactor;
  private final Eth eth;

  public Accounts(final Eth eth) {
    this.eth = eth;
    this.benefactor = new Account(GENESIS_ACCOUNT_ONE_PUBLIC_KEY);
  }

  public Account richBenefactor() {
    return benefactor;
  }

  public BigInteger balance(final Account account) {
    return balance(account.address());
  }

  public BigInteger balance(final String address) {
    return failOnIOException(() -> eth.getBalance(address));
  }

  public List<String> list() {
    return failOnIOException(eth::getAccounts);
  }
}
