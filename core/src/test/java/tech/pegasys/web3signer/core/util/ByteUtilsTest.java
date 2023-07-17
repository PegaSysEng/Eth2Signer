/*
 * Copyright 2020 ConsenSys AG.
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
package tech.pegasys.web3signer.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.web3signer.signing.util.ByteUtils;

import java.math.BigInteger;

import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

class ByteUtilsTest {

  @Test
  public void omitsSignIndicationByteProperly() {
    final BigInteger a = new BigInteger(1, Hex.decode("ff12345678"));
    final byte[] a5 = ByteUtils.bigIntegerToBytes(a);
    assertThat(a5.length).isEqualTo(5);
    assertThat(a5).containsExactly(Hex.decode("ff12345678"));

    final BigInteger b = new BigInteger(1, Hex.decode("0f12345678"));
    final byte[] b5 = ByteUtils.bigIntegerToBytes(b);
    assertThat(b5.length).isEqualTo(5);
    assertThat(b5).containsExactly(Hex.decode("0f12345678"));
  }

  @Test
  public void ifParameterIsNullReturnsNull() {
    final byte[] a = ByteUtils.bigIntegerToBytes(null);
    assertThat(a).isNull();
  }

  @Test
  public void ifBigIntegerZeroReturnsZeroValueArray() {
    final byte[] a = ByteUtils.bigIntegerToBytes(BigInteger.ZERO);
    assertThat(a).containsExactly(0);
  }
}
