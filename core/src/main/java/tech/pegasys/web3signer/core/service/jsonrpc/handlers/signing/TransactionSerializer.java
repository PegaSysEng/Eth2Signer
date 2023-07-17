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

import static tech.pegasys.web3signer.core.service.jsonrpc.handlers.sendtransaction.transaction.Transaction.longToBytes;
import static tech.pegasys.web3signer.core.service.jsonrpc.response.JsonRpcError.SIGNING_FROM_IS_NOT_AN_UNLOCKED_ACCOUNT;
import static tech.pegasys.web3signer.core.util.Eth1AddressUtil.signerPublicKeyFromAddress;

import tech.pegasys.web3signer.core.service.jsonrpc.exceptions.JsonRpcException;
import tech.pegasys.web3signer.core.service.jsonrpc.handlers.sendtransaction.transaction.Transaction;
import tech.pegasys.web3signer.signing.ArtifactSignerProvider;
import tech.pegasys.web3signer.signing.SecpArtifactSignature;
import tech.pegasys.web3signer.signing.secp256k1.Signature;

import java.nio.ByteBuffer;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.web3j.crypto.Sign.SignatureData;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.crypto.transaction.type.TransactionType;
import org.web3j.utils.Numeric;

public class TransactionSerializer {
  private static final Logger LOG = LogManager.getLogger();

  protected final ArtifactSignerProvider signer;
  protected final long chainId;

  public TransactionSerializer(final ArtifactSignerProvider signer, final long chainId) {
    this.signer = signer;
    this.chainId = chainId;
  }

  public String serialize(final Transaction transaction) {

    SignatureData signatureData = null;

    if (!transaction.isEip1559()) {
      signatureData = new SignatureData(longToBytes(chainId), new byte[] {}, new byte[] {});
    }

    byte[] bytesToSign = transaction.rlpEncode(signatureData);

    if (transaction.isEip1559()) {
      bytesToSign = prependEip1559TransactionType(bytesToSign);
    }

    final Signature signature = sign(transaction.sender(), bytesToSign);

    SignatureData web3jSignature =
        new SignatureData(
            signature.getV().toByteArray(),
            signature.getR().toByteArray(),
            signature.getS().toByteArray());

    if (!transaction.isEip1559()) {
      web3jSignature = TransactionEncoder.createEip155SignatureData(web3jSignature, chainId);
    }

    byte[] serializedBytes = transaction.rlpEncode(web3jSignature);
    if (transaction.isEip1559()) {
      serializedBytes = prependEip1559TransactionType(serializedBytes);
    }

    return Numeric.toHexString(serializedBytes);
  }

  private Signature sign(final String sender, final byte[] bytesToSign) {
    Optional<String> publicKey = signerPublicKeyFromAddress(signer, sender);

    if (publicKey.isEmpty()) {
      LOG.debug("From address ({}) does not match any available account", sender);
      throw new JsonRpcException(SIGNING_FROM_IS_NOT_AN_UNLOCKED_ACCOUNT);
    }

    final SecpArtifactSignature artifactSignature =
        signer
            .getSigner(publicKey.get())
            .map(
                artifactSigner ->
                    (SecpArtifactSignature) artifactSigner.sign(Bytes.of(bytesToSign)))
            .get();

    final Signature signature = artifactSignature.getSignatureData();
    return signature;
  }

  private static byte[] prependEip1559TransactionType(byte[] bytesToSign) {
    return ByteBuffer.allocate(bytesToSign.length + 1)
        .put(TransactionType.EIP1559.getRlpType())
        .put(bytesToSign)
        .array();
  }
}
