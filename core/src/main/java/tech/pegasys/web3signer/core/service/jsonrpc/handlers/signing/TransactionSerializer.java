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
package tech.pegasys.web3signer.core.service.jsonrpc.handlers.signing;

import static org.web3j.utils.Numeric.toHexString;
import static tech.pegasys.web3signer.core.service.jsonrpc.handlers.sendtransaction.transaction.Transaction.longToBytes;
import static tech.pegasys.web3signer.core.service.jsonrpc.response.JsonRpcError.SIGNING_FROM_IS_NOT_AN_UNLOCKED_ACCOUNT;
import static tech.pegasys.web3signer.signing.util.IdentifierUtils.normaliseIdentifier;

import tech.pegasys.web3signer.core.service.http.handlers.signing.SignerForIdentifier;
import tech.pegasys.web3signer.core.service.jsonrpc.exceptions.JsonRpcException;
import tech.pegasys.web3signer.core.service.jsonrpc.handlers.sendtransaction.transaction.Transaction;
import tech.pegasys.web3signer.signing.SecpArtifactSignature;
import tech.pegasys.web3signer.signing.secp256k1.Signature;

import java.nio.ByteBuffer;
import java.util.Base64;

import org.apache.tuweni.bytes.Bytes;
import org.web3j.crypto.Sign.SignatureData;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.crypto.transaction.type.TransactionType;

import javax.crypto.spec.PSource;

public class TransactionSerializer {

  protected final SignerForIdentifier<SecpArtifactSignature> secpSigner;
  protected final long chainId;

  public TransactionSerializer(
      final SignerForIdentifier<SecpArtifactSignature> secpSigner, final long chainId) {
    this.secpSigner = secpSigner;
    this.chainId = chainId;
  }

  public String serialize(final Transaction transaction) {
    if (transaction.isEip4844()) {
      return toHexString(serializeEip4844(transaction));
    } else if (transaction.isEip1559()) {
      return toHexString(serializeEip1559(transaction));
    } else {
      return toHexString(serializeFrontier(transaction));
    }
  }

  private byte[] serializeFrontier(final Transaction transaction) {
    final SignatureData preSigningSignatureData =
        new SignatureData(longToBytes(chainId), new byte[] {}, new byte[] {});
    byte[] bytesToSign = transaction.rlpEncode(preSigningSignatureData);

    SignatureData signatureData = sign(transaction.sender(), bytesToSign);

    signatureData = TransactionEncoder.createEip155SignatureData(signatureData, chainId);
    return transaction.rlpEncode(signatureData);
  }

  private byte[] serializeEip1559(final Transaction transaction) {
    byte[] bytesToSign = transaction.rlpEncode(null);
    bytesToSign = prependEip1559TransactionType(bytesToSign);

    final SignatureData signatureData = sign(transaction.sender(), bytesToSign);

    byte[] serializedBytes = transaction.rlpEncode(signatureData);
    return prependEip1559TransactionType(serializedBytes);
  }

  private static byte[] prependEip1559TransactionType(byte[] bytesToSign) {
    return ByteBuffer.allocate(bytesToSign.length + 1)
        .put(TransactionType.EIP1559.getRlpType())
        .put(bytesToSign)
        .array();
  }

  private byte[] serializeEip4844(final Transaction transaction) {
    byte[] bytesToSign = transaction.rlpEncodeToSign();
    bytesToSign = prependEip4844TransactionType(bytesToSign);

    final SignatureData signatureData = sign(transaction.sender(), bytesToSign);

    byte[] serializedBytes = transaction.rlpEncode(signatureData);
    return prependEip4844TransactionType(serializedBytes);
  }

  private static byte[] prependEip4844TransactionType(byte[] bytesToSign) {
    return ByteBuffer.allocate(bytesToSign.length + 1)
        .put(TransactionType.EIP4844.getRlpType())
        .put(bytesToSign)
        .array();
  }

  private SignatureData sign(final String eth1Address, final byte[] bytesToSign) {
    final SecpArtifactSignature artifactSignature =
        secpSigner
            .signAndGetArtifactSignature(normaliseIdentifier(eth1Address), Bytes.of(bytesToSign))
            .orElseThrow(() -> new JsonRpcException(SIGNING_FROM_IS_NOT_AN_UNLOCKED_ACCOUNT));

    final Signature signature = artifactSignature.getSignatureData();

    return new SignatureData(
        signature.getV().toByteArray(),
        signature.getR().toByteArray(),
        signature.getS().toByteArray());
  }
}
