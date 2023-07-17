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
package tech.pegasys.web3signer.core.jsonrpcproxy;

import static io.netty.handler.codec.http.HttpResponseStatus.GATEWAY_TIMEOUT;
import static java.math.BigInteger.ONE;
import static java.util.Collections.singletonList;
import static tech.pegasys.web3signer.core.jsonrpcproxy.model.jsonrpc.EeaSendTransaction.UNLOCKED_ACCOUNT;
import static tech.pegasys.web3signer.core.jsonrpcproxy.model.jsonrpc.SendTransaction.FIELD_DATA_DEFAULT;
import static tech.pegasys.web3signer.core.jsonrpcproxy.model.jsonrpc.SendTransaction.FIELD_GAS_DEFAULT;
import static tech.pegasys.web3signer.core.jsonrpcproxy.model.jsonrpc.SendTransaction.FIELD_GAS_PRICE_DEFAULT;
import static tech.pegasys.web3signer.core.jsonrpcproxy.model.jsonrpc.SendTransaction.FIELD_VALUE_DEFAULT;
import static tech.pegasys.web3signer.core.jsonrpcproxy.support.TransactionCountResponder.TRANSACTION_COUNT_METHOD.ETH_GET_TRANSACTION_COUNT;
import static tech.pegasys.web3signer.core.service.jsonrpc.response.JsonRpcError.CONNECTION_TO_DOWNSTREAM_NODE_TIMED_OUT;
import static tech.pegasys.web3signer.core.service.jsonrpc.response.JsonRpcError.ETH_SEND_TX_REPLACEMENT_UNDERPRICED;
import static tech.pegasys.web3signer.core.service.jsonrpc.response.JsonRpcError.INTERNAL_ERROR;
import static tech.pegasys.web3signer.core.service.jsonrpc.response.JsonRpcError.INVALID_PARAMS;
import static tech.pegasys.web3signer.core.service.jsonrpc.response.JsonRpcError.NONCE_TOO_LOW;
import static tech.pegasys.web3signer.core.service.jsonrpc.response.JsonRpcError.SIGNING_FROM_IS_NOT_AN_UNLOCKED_ACCOUNT;

import tech.pegasys.web3signer.core.jsonrpcproxy.model.jsonrpc.SendRawTransaction;
import tech.pegasys.web3signer.core.jsonrpcproxy.model.jsonrpc.SendTransaction;
import tech.pegasys.web3signer.core.jsonrpcproxy.model.jsonrpc.Transaction;
import tech.pegasys.web3signer.core.jsonrpcproxy.support.TransactionCountResponder;

import java.util.Optional;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.json.Json;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthSendTransaction;

/** Signing is a step during proxying a sendTransaction() JSON-RPC request to an Ethereum node. */
class SigningEthSendTransactionIntegrationTest extends DefaultTestBase {

  private static final String VALID_BODY_RESPONSE =
      "{\"jsonrpc\" : \"2.0\",\"id\" : 1,\"result\" : \"VALID\"}";
  private static final String INVALID_PARAMS_BODY =
      "{\"jsonrpc\":\"2.0\",\"id\":77,\"error\":{\"code\":-32602,\"message\":\"Invalid params\"}}";

  private SendTransaction sendTransaction;
  private SendRawTransaction sendRawTransaction;
  private final Transaction.Builder transactionBuilder = Transaction.defaultTransaction();

  @BeforeEach
  void setUp() {
    sendTransaction = new SendTransaction();
    sendRawTransaction = new SendRawTransaction(jsonRpc(), credentials);
    final TransactionCountResponder getTransactionResponse =
        new TransactionCountResponder(nonce -> nonce.add(ONE), ETH_GET_TRANSACTION_COUNT);
    clientAndServer.when(getTransactionResponse.request()).respond(getTransactionResponse);
  }

  @Test
  void proxyMalformedJsonResponseFromNode() {
    final String rawTransaction = sendRawTransaction.request();
    setUpEthNodeResponse(request.ethNode(rawTransaction), response.ethNode(MALFORMED_JSON));

    sendPostRequestAndVerifyResponse(
        request.web3Signer(rawTransaction), response.web3Signer(MALFORMED_JSON));
  }

  @Test
  void invalidParamsResponseWhenNonceIsNaN() {
    sendPostRequestAndVerifyResponse(
        request.web3Signer(
            sendTransaction.request(transactionBuilder.withNonce("I'm an invalid nonce format!"))),
        response.web3Signer(INVALID_PARAMS));
  }

  @Test
  void missingNonceResultsInEthNodeRespondingSuccessfully() {
    final String ethNodeResponseBody = VALID_BODY_RESPONSE;
    final String requestBody =
        sendRawTransaction.request(sendTransaction.request(transactionBuilder.withNonce("0x1")));

    setUpEthNodeResponse(request.ethNode(requestBody), response.ethNode(ethNodeResponseBody));

    sendPostRequestAndVerifyResponse(
        request.web3Signer(sendTransaction.request(transactionBuilder.missingNonce())),
        response.web3Signer(ethNodeResponseBody));
  }

  @Test
  void invalidParamsResponseWhenFromAddressIsTooShort() {
    sendPostRequestAndVerifyResponse(
        request.web3Signer(
            sendTransaction.request(
                transactionBuilder.withFrom("0x577919ae5df4941180eac211965f275CDCE314D"))),
        response.web3Signer(SIGNING_FROM_IS_NOT_AN_UNLOCKED_ACCOUNT));
  }

  @Test
  void invalidParamsResponseWhenFromAddressIsTooLong() {
    sendPostRequestAndVerifyResponse(
        request.web3Signer(
            sendTransaction.request(
                transactionBuilder.withFrom("0x1577919ae5df4941180eac211965f275CDCE314D"))),
        response.web3Signer(SIGNING_FROM_IS_NOT_AN_UNLOCKED_ACCOUNT));
  }

  @Test
  void invalidParamsResponseWhenFromAddressIsMalformedHex() {
    sendPostRequestAndVerifyResponse(
        request.web3Signer(
            sendTransaction.request(
                transactionBuilder.withFrom("0xb60e8dd61c5d32be8058bb8eb970870f07233XXX"))),
        response.web3Signer(SIGNING_FROM_IS_NOT_AN_UNLOCKED_ACCOUNT));
  }

  @Test
  void invalidParamsWhenFromAddressIsEmpty() {
    sendPostRequestAndVerifyResponse(
        request.web3Signer(sendTransaction.request(transactionBuilder.withFrom(""))),
        response.web3Signer(INVALID_PARAMS));
  }

  @Test
  void signTransactionWhenFromAddressCaseMismatchesUnlockedAccount() {
    final Request<?, EthSendTransaction> sendTransactionRequest =
        sendTransaction.request(
            transactionBuilder.withFrom("0x7577919ae5df4941180eac211965f275CDCE314D"));
    final String sendRawTransactionRequest =
        sendRawTransaction.request(
            sendTransaction.request(
                transactionBuilder.withFrom("0x7577919ae5df4941180eac211965f275cdce314d")));
    final String sendRawTransactionResponse =
        sendRawTransaction.response(
            "0xe670ec64341771606e55d6b4ca35a1a6b75ee3d5145a99d05921026d1666666");
    setUpEthNodeResponse(
        request.ethNode(sendRawTransactionRequest), response.ethNode(sendRawTransactionResponse));

    sendPostRequestAndVerifyResponse(
        request.web3Signer(sendTransactionRequest),
        response.web3Signer(sendRawTransactionResponse));

    verifyEthNodeReceived(sendRawTransactionRequest);
  }

  @Test
  void invalidParamsResponseWhenMissingFromAddress() {
    sendPostRequestAndVerifyResponse(
        request.web3Signer(sendTransaction.request(transactionBuilder.missingFrom())),
        response.web3Signer(INVALID_PARAMS));
  }

  @Test
  void signTransactionWhenEmptyToAddress() {
    final Request<?, EthSendTransaction> sendTransactionRequest =
        sendTransaction.request(transactionBuilder.withTo(""));
    final String sendRawTransactionRequest =
        sendRawTransaction.request(sendTransaction.request(transactionBuilder.missingTo()));
    final String sendRawTransactionResponse =
        sendRawTransaction.response(
            "0xe670ec64341771606e55d6b4ca35a1a6b75ee3d5145a99d05921026d1355555");
    setUpEthNodeResponse(
        request.ethNode(sendRawTransactionRequest), response.ethNode(sendRawTransactionResponse));

    sendPostRequestAndVerifyResponse(
        request.web3Signer(sendTransactionRequest),
        response.web3Signer(sendRawTransactionResponse));

    verifyEthNodeReceived(sendRawTransactionRequest);
  }

  @Test
  void signTransactionWhenEmpty0xToAddress() {
    final Request<?, EthSendTransaction> sendTransactionRequest =
        sendTransaction.request(transactionBuilder.withTo("0x"));
    final String sendRawTransactionRequest =
        sendRawTransaction.request(sendTransaction.request(transactionBuilder.missingTo()));
    final String sendRawTransactionResponse =
        sendRawTransaction.response(
            "0xe670ec64341771606e55d6b4ca35a1a6b75ee3d5145a99d05921026d1355555");
    setUpEthNodeResponse(
        request.ethNode(sendRawTransactionRequest), response.ethNode(sendRawTransactionResponse));

    sendPostRequestAndVerifyResponse(
        request.web3Signer(sendTransactionRequest),
        response.web3Signer(sendRawTransactionResponse));

    verifyEthNodeReceived(sendRawTransactionRequest);
  }

  @Test
  void signTransactionWhenToHasAddressMissingHexPrefix() {
    final Request<?, EthSendTransaction> sendTransactionRequest =
        sendTransaction.request(
            transactionBuilder.withTo("7577919ae5df4941180eac211965f275CDCE314D"));
    final String sendRawTransactionRequest =
        sendRawTransaction.request(
            sendTransaction.request(
                transactionBuilder.withTo("0x7577919ae5df4941180eac211965f275CDCE314D")));
    final String sendRawTransactionResponse =
        sendRawTransaction.response(
            "0xe670ec64341771606e55d6b4ca35a1a6b75ee3d5145a99d05921026d1355555");
    setUpEthNodeResponse(
        request.ethNode(sendRawTransactionRequest), response.ethNode(sendRawTransactionResponse));

    sendPostRequestAndVerifyResponse(
        request.web3Signer(sendTransactionRequest),
        response.web3Signer(sendRawTransactionResponse));

    verifyEthNodeReceived(sendRawTransactionRequest);
  }

  @Test
  void signTransactionWhenFromHasAddressMissingHexPrefix() {
    final Request<?, EthSendTransaction> sendTransactionRequest =
        sendTransaction.request(transactionBuilder.withFrom(UNLOCKED_ACCOUNT.substring(2)));
    final String sendRawTransactionRequest =
        sendRawTransaction.request(
            sendTransaction.request(transactionBuilder.withFrom(UNLOCKED_ACCOUNT.substring(2))));
    final String sendRawTransactionResponse =
        sendRawTransaction.response(
            "0xe670ec64341771606e55d6b4ca35a1a6b75ee3d5145a99d05921026d1355555");
    setUpEthNodeResponse(
        request.ethNode(sendRawTransactionRequest), response.ethNode(sendRawTransactionResponse));

    sendPostRequestAndVerifyResponse(
        request.web3Signer(sendTransactionRequest),
        response.web3Signer(sendRawTransactionResponse));

    verifyEthNodeReceived(sendRawTransactionRequest);
  }

  @Test
  void signTransactionWhenMissingToAddress() {
    final Request<?, EthSendTransaction> sendTransactionRequest =
        sendTransaction.request(transactionBuilder.missingTo());
    final String sendRawTransactionRequest =
        sendRawTransaction.request(sendTransaction.request(transactionBuilder.missingTo()));
    final String sendRawTransactionResponse =
        sendRawTransaction.response(
            "0xe670ec64341771606e55d6b4ca35a1a6b75ee3d5145a99d05921026d1355555");
    setUpEthNodeResponse(
        request.ethNode(sendRawTransactionRequest), response.ethNode(sendRawTransactionResponse));

    sendPostRequestAndVerifyResponse(
        request.web3Signer(sendTransactionRequest),
        response.web3Signer(sendRawTransactionResponse));

    verifyEthNodeReceived(sendRawTransactionRequest);
  }

  @Test
  void signTransactionWhenToAddressIsNull() {
    final Request<?, EthSendTransaction> sendTransactionRequest =
        sendTransaction.request(transactionBuilder.withTo(null));
    final String sendRawTransactionRequest =
        sendRawTransaction.request(sendTransaction.request(transactionBuilder.missingTo()));
    final String sendRawTransactionResponse =
        sendRawTransaction.response(
            "0xe670ec64341771606e55d6b4ca35a1a6b75ee3d5145a99d05921026d1355555");
    setUpEthNodeResponse(
        request.ethNode(sendRawTransactionRequest), response.ethNode(sendRawTransactionResponse));

    sendPostRequestAndVerifyResponse(
        request.web3Signer(sendTransactionRequest),
        response.web3Signer(sendRawTransactionResponse));

    verifyEthNodeReceived(sendRawTransactionRequest);
  }

  @Test
  void signTransactionWhenMissingValue() {
    final Request<?, EthSendTransaction> sendTransactionRequest =
        sendTransaction.request(transactionBuilder.missingValue());
    final String sendRawTransactionRequest =
        sendRawTransaction.request(
            sendTransaction.request(transactionBuilder.withValue(FIELD_VALUE_DEFAULT)));
    final String sendRawTransactionResponse =
        sendRawTransaction.response(
            "0xe670ec64341771606e55d6b4ca35a1a6b75ee3d5145a99d05921026d1666666");
    setUpEthNodeResponse(
        request.ethNode(sendRawTransactionRequest), response.ethNode(sendRawTransactionResponse));

    sendPostRequestAndVerifyResponse(
        request.web3Signer(sendTransactionRequest),
        response.web3Signer(sendRawTransactionResponse));

    verifyEthNodeReceived(sendRawTransactionRequest);
  }

  @Test
  void signTransactionWhenValueIsNull() {
    final Request<?, EthSendTransaction> sendTransactionRequest =
        sendTransaction.request(transactionBuilder.withValue(null));
    final String sendRawTransactionRequest =
        sendRawTransaction.request(
            sendTransaction.request(transactionBuilder.withValue(FIELD_VALUE_DEFAULT)));
    final String sendRawTransactionResponse =
        sendRawTransaction.response(
            "0xe670ec64341771606e55d6b4ca35a1a6b75ee3d5145a99d05921026d1666666");
    setUpEthNodeResponse(
        request.ethNode(sendRawTransactionRequest), response.ethNode(sendRawTransactionResponse));

    sendPostRequestAndVerifyResponse(
        request.web3Signer(sendTransactionRequest),
        response.web3Signer(sendRawTransactionResponse));

    verifyEthNodeReceived(sendRawTransactionRequest);
  }

  @Test
  void invalidParamsResponseWhenValueIsNaN() {
    sendPostRequestAndVerifyResponse(
        request.web3Signer(
            sendTransaction.request(transactionBuilder.withValue("I'm an invalid value format!"))),
        response.web3Signer(INVALID_PARAMS));
  }

  @Test
  void signTransactionWhenMissingGas() {
    final Request<?, EthSendTransaction> sendTransactionRequest =
        sendTransaction.request(transactionBuilder.missingGas());
    final String sendRawTransactionRequest =
        sendRawTransaction.request(
            sendTransaction.request(transactionBuilder.withGas(FIELD_GAS_DEFAULT)));

    final String sendRawTransactionResponse =
        sendRawTransaction.response(
            "0xe670ec64341771606e55d6b4ca35a1a6b75ee3d5145a99d05921026d7777777");
    setUpEthNodeResponse(
        request.ethNode(sendRawTransactionRequest), response.ethNode(sendRawTransactionResponse));

    sendPostRequestAndVerifyResponse(
        request.web3Signer(sendTransactionRequest),
        response.web3Signer(sendRawTransactionResponse));

    verifyEthNodeReceived(sendRawTransactionRequest);
  }

  @Test
  void signTransactionWhenGasIsNull() {
    final Request<?, EthSendTransaction> sendTransactionRequest =
        sendTransaction.request(transactionBuilder.withGas(null));
    final String sendRawTransactionRequest =
        sendRawTransaction.request(
            sendTransaction.request(transactionBuilder.withGas(FIELD_GAS_DEFAULT)));

    final String sendRawTransactionResponse =
        sendRawTransaction.response(
            "0xe670ec64341771606e55d6b4ca35a1a6b75ee3d5145a99d05921026d7777777");
    setUpEthNodeResponse(
        request.ethNode(sendRawTransactionRequest), response.ethNode(sendRawTransactionResponse));

    sendPostRequestAndVerifyResponse(
        request.web3Signer(sendTransactionRequest),
        response.web3Signer(sendRawTransactionResponse));

    verifyEthNodeReceived(sendRawTransactionRequest);
  }

  @Test
  void invalidParamsResponseWhenGasIsNaN() {
    sendPostRequestAndVerifyResponse(
        request.web3Signer(
            sendTransaction.request(transactionBuilder.withGas("I'm an invalid gas format!"))),
        response.web3Signer(INVALID_PARAMS));
  }

  @Test
  void signTransactionWhenMissingGasPrice() {
    final Request<?, EthSendTransaction> sendTransactionRequest =
        sendTransaction.request(transactionBuilder.missingGasPrice());
    final String sendRawTransactionRequest =
        sendRawTransaction.request(
            sendTransaction.request(transactionBuilder.withGasPrice(FIELD_GAS_PRICE_DEFAULT)));
    final String sendRawTransactionResponse =
        sendRawTransaction.response(
            "0xe670ec64341771606e55d6b4ca35a1a6b75ee3d5145a99d0592102688888888");
    setUpEthNodeResponse(
        request.ethNode(sendRawTransactionRequest), response.ethNode(sendRawTransactionResponse));

    sendPostRequestAndVerifyResponse(
        request.web3Signer(sendTransactionRequest),
        response.web3Signer(sendRawTransactionResponse));

    verifyEthNodeReceived(sendRawTransactionRequest);
  }

  @Test
  void signTransactionWhenGasPriceIsNull() {
    final Request<?, EthSendTransaction> sendTransactionRequest =
        sendTransaction.request(transactionBuilder.withGasPrice(null));
    final String sendRawTransactionRequest =
        sendRawTransaction.request(
            sendTransaction.request(transactionBuilder.withGasPrice(FIELD_GAS_PRICE_DEFAULT)));
    final String sendRawTransactionResponse =
        sendRawTransaction.response(
            "0xe670ec64341771606e55d6b4ca35a1a6b75ee3d5145a99d0592102688888888");
    setUpEthNodeResponse(
        request.ethNode(sendRawTransactionRequest), response.ethNode(sendRawTransactionResponse));

    sendPostRequestAndVerifyResponse(
        request.web3Signer(sendTransactionRequest),
        response.web3Signer(sendRawTransactionResponse));

    verifyEthNodeReceived(sendRawTransactionRequest);
  }

  @Test
  void invalidParamsResponseWhenGasPriceIsNaN() {
    sendPostRequestAndVerifyResponse(
        request.web3Signer(
            sendTransaction.request(
                transactionBuilder.withGasPrice("I'm an invalid gas price format!"))),
        response.web3Signer(INVALID_PARAMS));
  }

  @Test
  void signSendTransactionWhenMissingData() {
    final Request<?, EthSendTransaction> sendTransactionRequest =
        sendTransaction.request(transactionBuilder.missingData());
    final String sendRawTransactionRequest =
        sendRawTransaction.request(
            sendTransaction.request(transactionBuilder.withData(FIELD_DATA_DEFAULT)));
    final String sendRawTransactionResponse =
        sendRawTransaction.response(
            "0xe670ec64341771606e55d6b4ca35a1a6b75ee3d5145a99d0592102999999999");
    setUpEthNodeResponse(
        request.ethNode(sendRawTransactionRequest), response.ethNode(sendRawTransactionResponse));

    sendPostRequestAndVerifyResponse(
        request.web3Signer(sendTransactionRequest),
        response.web3Signer(sendRawTransactionResponse));

    verifyEthNodeReceived(sendRawTransactionRequest);
  }

  @Test
  void signSendTransactionWhenContract() {
    final Request<?, EthSendTransaction> sendTransactionRequest =
        sendTransaction.request(Transaction.smartContract());
    final String sendRawTransactionRequest = sendRawTransaction.request(sendTransactionRequest);
    final String sendRawTransactionResponse =
        sendRawTransaction.response(
            "0xe670ec64341771606e55d6b4ca35a1a6b75ee3d5145a99d0592102688888888");
    setUpEthNodeResponse(
        this.request.ethNode(sendRawTransactionRequest),
        response.ethNode(sendRawTransactionResponse));

    sendPostRequestAndVerifyResponse(
        this.request.web3Signer(sendTransactionRequest),
        response.web3Signer(sendRawTransactionResponse));

    verifyEthNodeReceived(sendRawTransactionRequest);
  }

  @Test
  void signSendTransaction() {
    final Request<?, EthSendTransaction> sendTransactionRequest =
        sendTransaction.request(Transaction.smartContract());
    final String sendRawTransactionRequest = sendRawTransaction.request(sendTransactionRequest);
    final String sendRawTransactionResponse =
        sendRawTransaction.response(
            "0xe670ec64341771606e55d6b4ca35a1a6b75ee3d5145a99d0592102999999999");
    setUpEthNodeResponse(
        request.ethNode(sendRawTransactionRequest), response.ethNode(sendRawTransactionResponse));

    sendPostRequestAndVerifyResponse(
        request.web3Signer(sendTransactionRequest),
        response.web3Signer(sendRawTransactionResponse));

    verifyEthNodeReceived(sendRawTransactionRequest);
  }

  @Test
  void missingNonceResultsInNewNonceBeingCreatedAndResent() {
    final String rawTransactionWithInitialNonce =
        sendRawTransaction.request(sendTransaction.request(transactionBuilder.withNonce("0x0")));
    final String rawTransactionWithNextNonce =
        sendRawTransaction.request(sendTransaction.request(transactionBuilder.withNonce("0x1")));
    setUpEthNodeResponse(
        request.ethNode(rawTransactionWithInitialNonce), response.ethNode(NONCE_TOO_LOW));

    final String successResponseFromWeb3Provider = VALID_BODY_RESPONSE;
    setUpEthNodeResponse(
        request.ethNode(rawTransactionWithNextNonce),
        response.ethNode(successResponseFromWeb3Provider));

    sendPostRequestAndVerifyResponse(
        request.web3Signer(sendTransaction.request(transactionBuilder.missingNonce())),
        response.web3Signer(successResponseFromWeb3Provider));
  }

  @Test
  void nullNonceResultsInNewNonceBeingCreatedAndResent() {
    final String rawTransactionWithInitialNonce =
        sendRawTransaction.request(sendTransaction.request(transactionBuilder.withNonce("0x0")));
    final String rawTransactionWithNextNonce =
        sendRawTransaction.request(sendTransaction.request(transactionBuilder.withNonce("0x1")));
    setUpEthNodeResponse(
        request.ethNode(rawTransactionWithInitialNonce), response.ethNode(NONCE_TOO_LOW));

    final String successResponseFromWeb3Provider = VALID_BODY_RESPONSE;
    setUpEthNodeResponse(
        request.ethNode(rawTransactionWithNextNonce),
        response.ethNode(successResponseFromWeb3Provider));

    sendPostRequestAndVerifyResponse(
        request.web3Signer(sendTransaction.request(transactionBuilder.withNonce(null))),
        response.web3Signer(successResponseFromWeb3Provider));
  }

  @Test
  void nullNonceWithUnderpricedResponseResultsInNewNonceBeingCreatedAndResent() {
    final String rawTransactionWithInitialNonce =
        sendRawTransaction.request(sendTransaction.request(transactionBuilder.withNonce("0x0")));
    final String rawTransactionWithNextNonce =
        sendRawTransaction.request(sendTransaction.request(transactionBuilder.withNonce("0x1")));
    setUpEthNodeResponse(
        request.ethNode(rawTransactionWithInitialNonce),
        response.ethNode(ETH_SEND_TX_REPLACEMENT_UNDERPRICED));

    final String successResponseFromWeb3Provider = VALID_BODY_RESPONSE;
    setUpEthNodeResponse(
        request.ethNode(rawTransactionWithNextNonce),
        response.ethNode(successResponseFromWeb3Provider));

    sendPostRequestAndVerifyResponse(
        request.web3Signer(sendTransaction.request(transactionBuilder.withNonce(null))),
        response.web3Signer(successResponseFromWeb3Provider));
  }

  @Test
  void transactionWithMissingNonceReturnsErrorsOtherThanLowNonceToCaller() {
    final String rawTransactionWithInitialNonce =
        sendRawTransaction.request(sendTransaction.request(transactionBuilder.withNonce("0x1")));
    setUpEthNodeResponse(
        request.ethNode(rawTransactionWithInitialNonce),
        response.ethNode(INVALID_PARAMS_BODY, HttpResponseStatus.BAD_REQUEST));

    sendPostRequestAndVerifyResponse(
        request.web3Signer(sendTransaction.request(transactionBuilder.missingNonce())),
        response.web3Signer(INVALID_PARAMS_BODY, HttpResponseStatus.BAD_REQUEST));
  }

  @Test
  void moreThanTenNonceTooLowErrorsReturnsAnErrorToUser() {
    setupEthNodeResponse(".*eth_sendRawTransaction.*", response.ethNode(NONCE_TOO_LOW), 11);

    sendPostRequestAndVerifyResponse(
        request.web3Signer(sendTransaction.request(transactionBuilder.missingNonce())),
        response.web3Signer(INTERNAL_ERROR),
        "/",
        Optional.of(5000));
  }

  @Test
  void moreThanTenUnderpricedErrorsReturnsAnErrorToUser() {
    setupEthNodeResponse(
        ".*eth_sendRawTransaction.*", response.ethNode(ETH_SEND_TX_REPLACEMENT_UNDERPRICED), 11);

    sendPostRequestAndVerifyResponse(
        request.web3Signer(sendTransaction.request(transactionBuilder.missingNonce())),
        response.web3Signer(INTERNAL_ERROR),
        "/",
        Optional.of(5000));
  }

  @Test
  void thirdNonceRetryTimesOutAndGatewayTimeoutIsReturnedToClient() {
    setupEthNodeResponse(".*eth_sendRawTransaction.*", response.ethNode(NONCE_TOO_LOW), 3);
    timeoutRequest(".*eth_sendRawTransaction.*");

    sendPostRequestAndVerifyResponse(
        request.web3Signer(sendTransaction.request(transactionBuilder.missingNonce())),
        response.web3Signer(CONNECTION_TO_DOWNSTREAM_NODE_TIMED_OUT, GATEWAY_TIMEOUT));
  }

  @Test
  void thirdNonceRetryForUnderpricedTimesOutAndGatewayTimeoutIsReturnedToClient() {
    setupEthNodeResponse(
        ".*eth_sendRawTransaction.*", response.ethNode(ETH_SEND_TX_REPLACEMENT_UNDERPRICED), 3);
    timeoutRequest(".*eth_sendRawTransaction.*");

    sendPostRequestAndVerifyResponse(
        request.web3Signer(sendTransaction.request(transactionBuilder.missingNonce())),
        response.web3Signer(CONNECTION_TO_DOWNSTREAM_NODE_TIMED_OUT, GATEWAY_TIMEOUT));
  }

  @Test
  void ensureTranactionResponseContainsCorsHeader() {
    final Request<?, EthSendTransaction> sendTransactionRequest =
        sendTransaction.request(Transaction.smartContract());
    final String sendRawTransactionRequest = sendRawTransaction.request(sendTransactionRequest);
    final String sendRawTransactionResponse =
        sendRawTransaction.response(
            "0xe670ec64341771606e55d6b4ca35a1a6b75ee3d5145a99d0592102999999999");
    setUpEthNodeResponse(
        request.ethNode(sendRawTransactionRequest), response.ethNode(sendRawTransactionResponse));

    final String originDomain = "sample.com";

    sendPostRequestAndVerifyResponse(
        request.web3Signer(
            singletonList(ImmutablePair.of("Origin", originDomain)),
            Json.encode(sendTransactionRequest)),
        response.web3Signer(
            singletonList(
                ImmutablePair.of(
                    HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN.toString(), "sample.com")),
            sendRawTransactionResponse));
  }
}
