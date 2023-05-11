package tech.pegasys.web3signer.core.service.jsonrpc.handlers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.web3signer.core.service.jsonrpc.JsonRpcRequest;
import tech.pegasys.web3signer.core.service.jsonrpc.exceptions.JsonRpcException;
import tech.pegasys.web3signer.core.service.jsonrpc.response.JsonRpcError;
import tech.pegasys.web3signer.core.util.Eth1AddressSignerIdentifier;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class Eth1AccountsHandler implements ResultProvider<List<String>> {
    private static final Logger LOG = LogManager.getLogger();
    private final Supplier<Set<String>> publicKeySupplier;
    public Eth1AccountsHandler(final Supplier<Set<String>> publicKeySupplier){
        this.publicKeySupplier = publicKeySupplier;
    }

    @Override
    public List<String> createResponseResult(final JsonRpcRequest jsonRpcRequest) {

        final Object params = jsonRpcRequest.getParams();

        if (isPopulated(params) && isNotEmptyArray(params)) {
            LOG.info("eth_accounts should have no parameters, but has {}", params);
            throw new JsonRpcException(JsonRpcError.INVALID_PARAMS);
        }

        return publicKeySupplier.get().stream()
                .map(Eth1AddressSignerIdentifier::fromPublicKey)
                .map(signerIdentifier -> "0x" + signerIdentifier.toStringIdentifier())
                .sorted()
                .collect(Collectors.toList());

    }

    private boolean isPopulated(final Object params) {
        return params != null;
    }

    private boolean isNotEmptyArray(final Object params) {
        boolean arrayIsEmpty = false;
        final boolean paramsIsArray = (params instanceof Collection);
        if (paramsIsArray) {
            final Collection<?> collection = (Collection<?>) params;
            arrayIsEmpty = collection.isEmpty();
        }

        return !(paramsIsArray && arrayIsEmpty);
    }

}
