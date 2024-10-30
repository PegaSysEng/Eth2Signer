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
package tech.pegasys.web3signer.signing.secp256k1;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.EllipticCurve;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;

import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;

public class EthPublicKeyUtils {
  private static final BouncyCastleProvider BC_PROVIDER = new BouncyCastleProvider();
  private static final ECDomainParameters SECP256K1_DOMAIN;
  private static final ECParameterSpec BC_SECP256K1_SPEC;
  private static final java.security.spec.ECParameterSpec JAVA_SECP256K1_SPEC;
  private static final String SECP256K1_CURVE = "secp256k1";
  private static final String EC_ALGORITHM = "EC";

  static {
    final X9ECParameters params = CustomNamedCurves.getByName(SECP256K1_CURVE);
    SECP256K1_DOMAIN =
        new ECDomainParameters(params.getCurve(), params.getG(), params.getN(), params.getH());
    BC_SECP256K1_SPEC =
        new ECParameterSpec(params.getCurve(), params.getG(), params.getN(), params.getH());
    final ECCurve bcCurve = BC_SECP256K1_SPEC.getCurve();
    JAVA_SECP256K1_SPEC =
        new java.security.spec.ECParameterSpec(
            new EllipticCurve(
                new java.security.spec.ECFieldFp(bcCurve.getField().getCharacteristic()),
                bcCurve.getA().toBigInteger(),
                bcCurve.getB().toBigInteger()),
            new java.security.spec.ECPoint(
                BC_SECP256K1_SPEC.getG().getAffineXCoord().toBigInteger(),
                BC_SECP256K1_SPEC.getG().getAffineYCoord().toBigInteger()),
            BC_SECP256K1_SPEC.getN(),
            BC_SECP256K1_SPEC.getH().intValue());
  }

  /**
   * Create a new secp256k1 key pair.
   *
   * @param random The random number generator to use
   * @return The generated key pair
   * @throws GeneralSecurityException If there is an issue generating the key pair
   */
  public static KeyPair createSecp256k1KeyPair(final SecureRandom random)
      throws GeneralSecurityException {
    final KeyPairGenerator keyPairGenerator =
        KeyPairGenerator.getInstance(EC_ALGORITHM, BC_PROVIDER);
    final ECGenParameterSpec ecGenParameterSpec = new ECGenParameterSpec(SECP256K1_CURVE);
    if (random != null) {
      keyPairGenerator.initialize(ecGenParameterSpec, random);
    } else {
      keyPairGenerator.initialize(ecGenParameterSpec);
    }

    return keyPairGenerator.generateKeyPair();
  }

  /**
   * Convert a public key in bytes format to an ECPublicKey.
   *
   * @param value The public key in bytes format
   * @return The ECPublicKey
   */
  public static ECPublicKey bytesToECPublicKey(final Bytes value) {
    if (value.size() != 33 && value.size() != 65 && value.size() != 64) {
      throw new IllegalArgumentException(
          "Invalid public key length. Expected 33, 64, or 65 bytes.");
    }

    final ECPoint point;
    if (value.size() == 64) {
      // For 64-byte input, we need to prepend the 0x04 prefix for uncompressed format
      byte[] fullKey = new byte[65];
      fullKey[0] = 0x04;
      System.arraycopy(value.toArrayUnsafe(), 0, fullKey, 1, 64);
      point = SECP256K1_DOMAIN.getCurve().decodePoint(fullKey);
    } else {
      point = SECP256K1_DOMAIN.getCurve().decodePoint(value.toArrayUnsafe());
    }

    return bcECPointToECPublicKey(point);
  }

  private static ECPublicKey bcECPointToECPublicKey(final ECPoint point) {
    try {
      // Convert Bouncy Castle ECPoint to Java ECPoint
      final java.security.spec.ECPoint ecPoint =
          new java.security.spec.ECPoint(
              point.getAffineXCoord().toBigInteger(), point.getAffineYCoord().toBigInteger());

      final java.security.spec.ECPublicKeySpec pubSpec =
          new java.security.spec.ECPublicKeySpec(ecPoint, JAVA_SECP256K1_SPEC);
      return (ECPublicKey)
          KeyFactory.getInstance(EC_ALGORITHM, BC_PROVIDER).generatePublic(pubSpec);
    } catch (final InvalidKeySpecException | NoSuchAlgorithmException e) {
      throw new IllegalArgumentException("Unable to create EC public key", e);
    }
  }

  /**
   * Create an ECPublicKey from a BigInteger representation of the public key.
   *
   * @param publicKeyValue The BigInteger representation of the public key (64 bytes, without
   *     prefix)
   * @return The created ECPublicKey
   * @throws IllegalArgumentException if the input is invalid
   */
  public static ECPublicKey bigIntegerToECPublicKey(final BigInteger publicKeyValue) {
    if (publicKeyValue == null) {
      throw new IllegalArgumentException("Public key value cannot be null");
    }

    byte[] publicKeyBytes = publicKeyValue.toByteArray();

    // Ensure we have exactly 64 bytes
    if (publicKeyBytes.length < 64) {
      byte[] temp = new byte[64];
      System.arraycopy(publicKeyBytes, 0, temp, 64 - publicKeyBytes.length, publicKeyBytes.length);
      publicKeyBytes = temp;
    } else if (publicKeyBytes.length > 64) {
      publicKeyBytes =
          Arrays.copyOfRange(publicKeyBytes, publicKeyBytes.length - 64, publicKeyBytes.length);
    }

    // Create a new byte array with the uncompressed prefix
    byte[] fullPublicKeyBytes = new byte[65];
    fullPublicKeyBytes[0] = 0x04; // Uncompressed point prefix
    System.arraycopy(publicKeyBytes, 0, fullPublicKeyBytes, 1, 64);

    // Use the existing createPublicKey method
    return bytesToECPublicKey(Bytes.wrap(fullPublicKeyBytes));
  }

  /**
   * Convert a java ECPublicKey to an uncompressed (64 bytes) hex string.
   *
   * @param publicKey The public key to convert
   * @return The public key as a hex string
   */
  public static String toHexString(final ECPublicKey publicKey) {
    return getEncoded(publicKey, false, false).toHexString();
  }

  /**
   * Convert a java ECPublicKey to a BigInteger.
   *
   * @param publicKey The public key to convert
   * @return The public key as a BigInteger
   */
  public static BigInteger ecPublicKeyToBigInteger(final ECPublicKey publicKey) {
    // Get the uncompressed public key without prefix (64 bytes)
    final Bytes publicKeyBytes = EthPublicKeyUtils.getEncoded(publicKey, false, false);

    // Convert to BigInteger
    return new BigInteger(1, publicKeyBytes.toArrayUnsafe());
  }

  /**
   * Convert java ECPublicKey to Bytes.
   *
   * @param publicKey The public key to convert
   * @param compressed Whether to return the compressed form
   * @param withPrefix Used when compressed flag is false. Set to true to include the prefix byte
   *     for uncompressed keys making the size 65 bytes.
   * @return The encoded public key.
   */
  public static Bytes getEncoded(
      final ECPublicKey publicKey, boolean compressed, boolean withPrefix) {
    final ECPoint point;
    if (publicKey instanceof BCECPublicKey) {
      // If it's already a Bouncy Castle key, we can get the ECPoint directly
      point = ((BCECPublicKey) publicKey).getQ();
    } else {
      // If it's not a BC key, we need to create the ECPoint from the coordinates
      final BigInteger x = publicKey.getW().getAffineX();
      final BigInteger y = publicKey.getW().getAffineY();
      point = BC_SECP256K1_SPEC.getCurve().createPoint(x, y);
    }

    if (compressed) {
      return Bytes.wrap(point.getEncoded(true));
    } else if (withPrefix) {
      return Bytes.wrap(point.getEncoded(false));
    } else {
      return Bytes.wrap(point.getEncoded(false), 1, 64);
    }
  }
}
