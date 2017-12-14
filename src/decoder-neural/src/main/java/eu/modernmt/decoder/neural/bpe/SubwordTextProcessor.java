package eu.modernmt.decoder.neural.bpe;

import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by andrea on 07/12/17.
 */
public class SubwordTextProcessor {

    /**
     * The separator used between two consecutive BPE subwords obtained by splitting the original word.
     * It is necessary to allow re-merging the translations of such subowords after the machine translation.
     * It is currently "@@"
     */
    private final String separator;

    /**
     * BPE model with rules for the source language.
     */
    private final BPE sourceBPE;

    /**
     * Set of BPE valid subwords for the source language
     */
    private final Set<String> sourceTerms;

    /**
     * BPE model with rules for the target language.
     */
    private final BPE targetBPE;

    /**
     * Set of BPE valid subwords for the target language
     */
    private final Set<String> targetTerms;

    /**
     * Static method that creates a SubwordTextProcessor from an UTF-8 BPE model file
     *
     * @param bpeModel BPE model file
     * @return the SubwordTextProcessor that makes use of the passed BPE model file
     * @throws IOException if there is an error while reading the BPE model file
     */
    public static SubwordTextProcessor loadFromFile(File bpeModel) throws IOException {
        BufferedReader inp = new BufferedReader(new InputStreamReader(new FileInputStream(bpeModel), "UTF8"));

        /*read the separator from the model file*/
        String separator = StringUtils.strip(inp.readLine(), "\n");

        /*read the source codes (rules with priority) from the model file*/
        int sourceCodesSize = Integer.parseInt(StringUtils.strip(inp.readLine(), "\n"));
        Map<BPE.Rule, Integer> sourceCodes = new HashMap<>(sourceCodesSize);
        for (int i = 0; i < sourceCodesSize; i++) {
            String[] linefields = StringUtils.strip(inp.readLine(), "\n").split(" ");
            sourceCodes.put(new BPE.Rule(linefields[0], linefields[1]), Integer.parseInt(linefields[2]));
        }

        /*read the source terms from the model file*/
        int sourceTermsSize = Integer.parseInt(StringUtils.strip(inp.readLine(), "\n"));
        Set<String> sourceSubwords = new HashSet<>(sourceTermsSize);
        for (int i = 0; i < sourceTermsSize; i++)
            sourceSubwords.add(StringUtils.strip(inp.readLine(), "\n"));

        /*read the target codes (rules with priority) from the model file*/
        int targetCodesSize = Integer.parseInt(StringUtils.strip(inp.readLine(), "\n"));
        Map<BPE.Rule, Integer> targetCodes = new HashMap<>(targetCodesSize);
        for (int i = 0; i < targetCodesSize; i++) {
            String[] linefields = StringUtils.strip(inp.readLine(), "\n").split(" ");
            targetCodes.put(new BPE.Rule(linefields[0], linefields[1]), Integer.parseInt(linefields[2]));
        }

        /*read the target terms from the model file*/
        int targetTermsSize = Integer.parseInt(StringUtils.strip(inp.readLine(), "\n"));
        Set<String> targetSubwords = new HashSet<>(targetTermsSize);
        for (int i = 0; i < targetTermsSize; i++)
            targetSubwords.add(StringUtils.strip(inp.readLine(), "\n"));

        return new SubwordTextProcessor(sourceCodes, sourceSubwords, targetCodes, targetSubwords, separator);
    }

    public SubwordTextProcessor(Map<BPE.Rule, Integer> sourceCodes, Set<String> sourceTerms, Map<BPE.Rule, Integer> targetCodes, Set<String> targetTerms, String separator) {
        this.separator = separator;
        this.sourceBPE = new BPE(sourceCodes, separator);
        this.sourceTerms = sourceTerms;
        this.targetBPE = (!targetCodes.isEmpty()) ? new BPE(targetCodes, separator) : null;
        this.targetTerms = targetTerms;
    }

    /**
     * This method encodes a passed String with the BPE encoding implemented by this SubwordTextProcessor
     *
     * @param words    the Words of the line to encode
     * @param isSource true if the line is in the source language of this engine, false otherwise
     * @return the encoded string
     */
    public String[] encode(String[] words, boolean isSource) {
        BPE bpe = (isSource || this.targetBPE == null) ? this.sourceBPE : this.targetBPE;
        return bpe.apply(words, isSource ? this.sourceTerms : this.targetTerms);
    }
}
