import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TxHandler {
    
    public UTXOPool ledger;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        this.ledger = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        // check all tests
        return (allInputsInPool(tx) && //valid inputs
                allInputSignaturesValid(tx) && //valid signatures
                noInputsClaimedMultiple(tx) && //no double spending of inputs
                allOutputsNonNegative(tx) && //valid outputs
                (transactionFee(tx) >= 0.0)); //proper transaction
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        return Arrays.stream(possibleTxs)
                .filter( tx -> isValidTx(tx))
                .peek(this::updatePoolLedger)
                .toArray(Transaction[]::new);
    }
    
    private void updatePoolLedger(Transaction tx) {
        //update UTXOPool ledger
        //remove all inputs from the ledger, since they are not 'spent'
        tx.getInputs().forEach(inp -> ledger.removeUTXO(new UTXO(inp.prevTxHash, inp.outputIndex)));

        //add all outputs to the ledger, since they are 'unspent'
        IntStream.range(0, tx.numOutputs())
                .forEach(idx -> ledger.addUTXO(new UTXO(tx.getHash(), idx), tx.getOutput(idx)));
        
    }

    private boolean allInputsInPool(Transaction tx) {
        // (1) all outputs claimed by {@code tx} are in the current UTXO pool        
        // basically check all inputs must be in the UTXOPool
        // so that they can be claimed in this tx
        return tx.getInputs().stream() //get inputs
                .map(inp ->  new UTXO(inp.prevTxHash,inp.outputIndex)) // create UTXO temporaily with tx hash and index
                .allMatch( utxo -> ledger.contains(utxo)); //check if UTXO exist in pool, else return false
     
    }

    private boolean allInputSignaturesValid(Transaction tx) {
        // (2) the signatures on each input of {@code tx} are valid
        return IntStream.range(0, tx.numInputs())
                .allMatch(idx -> {
                    //get input at idx
                    Transaction.Input inp = tx.getInput(idx);
                    //since it exists check if the signature is valid
                    //get raw data
                    byte[] msg = tx.getRawDataToSign(idx);
                    //get signature
                    byte[] sig = inp.signature;
                    //get public key
                    //go into UTXOPool and get the output
                    PublicKey pk = ledger.getTxOutput(new UTXO(inp.prevTxHash, inp.outputIndex)).address;
                    //verify signatures, if not verified return false
                    return Crypto.verifySignature(pk, msg, sig);
                });
    }

    private boolean noInputsClaimedMultiple(Transaction tx) {
        // (3) no UTXO is claimed multiple times by {@code tx}
        return tx.getInputs().stream()
                .map(inp -> new UTXO(inp.prevTxHash,inp.outputIndex)) // create UTXO temporaily with tx hash and index
                .collect(Collectors.toSet()) // store the utxo in a set
                //compare set size with actual size, should be same
                //if different then inputs claimed multiple times
                .size() == tx.numInputs();
    }

    private boolean allOutputsNonNegative(Transaction tx) {
        // (4) all of {@code tx}s output values are non-negative
        return tx.getOutputs().stream()
                .allMatch(o -> o.value>=0); //check if value non-nagtive
    }

    private double transactionFee(Transaction tx) {
        // (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
        // sum all the inputSum
        double inputSum = tx.getInputs().stream()
                .mapToDouble(inp -> ledger.getTxOutput(new UTXO(inp.prevTxHash,inp.outputIndex)).value)
                .sum();
        double outputSum = tx.getOutputs().stream()
                .mapToDouble(o -> o.value)
                .sum();
        return inputSum - outputSum;
    }

    

}
