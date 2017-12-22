package eu.modernmt.decoder.neural.bpe;

import eu.modernmt.model.corpus.MultilingualCorpus;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A SubwordTextProcessorBuilder has the responsibility to
 * scan a series of data sources and use them to create a SubwordTextProcessor
 * .
 * Created by andrea on 20/12/17.
 */
public class SubwordTextProcessorBuilder {

    private int maxRules = 32000;
    private int maxVocabularySize = -1;
    private float vocabPruningThreshold = -1;
    private int minFrequency = 2;
    private float similarityThreshold = 0.5F;
    private String separator = "@@";


    public SubwordTextProcessorBuilder(int maxRules, int maxVocabularySize, float vocabPruningThreshold, int minFrequency, float similarityThreshold, String separator) {
        this.maxRules = maxRules;
        this.maxVocabularySize = maxVocabularySize;
        this.vocabPruningThreshold = vocabPruningThreshold;
        this.minFrequency = minFrequency;
        this.similarityThreshold = similarityThreshold;
        this.separator = separator;
    }

    /**
     * This method builds a new SubwordTextProcessor from a collection of data sources.
     * The data sources are typically the multilingual corpora used for training the engine.
     * <p>
     * This method scans the data sources and uses all of their stringPairs to
     * compute the vocabularies and train the BPE models.
     *
     * @param dataSources the list of multilingual corpora to use to train the SubwordTextProcessor
     */
    public SubwordTextProcessor build(List<MultilingualCorpus> dataSources) throws IOException {

        // ----- STEP 1: COMPUTE DICTIONARIES -----
        /* A dictionary is a counter for word occurrences (NOTE: **words**, not BPE subwords!)
        To fill the dictionaries, scan all training data and add all the read StringPairs in model.*/
        HashMap<String, Integer> srcDictionary = new HashMap<>();
        HashMap<String, Integer> trgDictionary = new HashMap<>();
        MultilingualCorpus.MultilingualLineReader reader = null;
        for (MultilingualCorpus dataSource : dataSources) {
            try {
                reader = dataSource.getContentReader();
                MultilingualCorpus.StringPair pair = reader.read();
                while (pair != null) {
                    this.addStringPair(pair, srcDictionary, trgDictionary);
                    pair = reader.read();
                }
            } finally {
                IOUtils.closeQuietly(reader);
            }
        }

        // ----- STEP 2: compute BPE models and rules -----
        //TODO: parallelizable in separate threads?
        BPE sourceBPE = BPE.train(srcDictionary, maxRules, minFrequency, separator);
        BPE targetBPE = BPE.train(trgDictionary, maxRules, minFrequency, separator);

        // ----- STEP 3: create vocabularies ------
        /* vocabularies are computed by applying the BPE rules found in step2 to the dictionaries computed in step 1 */
        //TODO: parallelizable in separate threads?
        Set<String> sourceVocabulary = this.collectSubwords(srcDictionary, sourceBPE);
        Set<String> targetVocabulary = this.collectSubwords(trgDictionary, targetBPE);

        //return new SubwordTextProcessor(sourceCodes, targetCodes, sourceVocabulary, targetVocabulary);
        return null;
    }

    /**
     * This method adds the words and chars of the passed source and target strings of a stringpair
     * to the counters source and target dictionaries and alphabets respectively
     *
     * @param pair the stringpair to add in the model
     */
    private void addStringPair(MultilingualCorpus.StringPair pair, HashMap<String, Integer> srcDictionary, HashMap<String, Integer> trgDictionary) {

        /*add the source words and chars to sourceDictionary and sourceAlphabet counters*/
        for (String word : pair.source.trim().split("\\s+"))    //split by any whitespace char appearing 1 or more times
            srcDictionary.put(word, srcDictionary.getOrDefault(word, 0) + 1);

        /*add the target words and chars to targetDictionary and targetAlphabet counters*/
        for (String word : pair.target.trim().split("\\s+"))    //split by any whitespace char appearing 1 or more times
            trgDictionary.put(word, trgDictionary.getOrDefault(word, 0) + 1);
    }


    /**
     * This private method obtains a vocabulary in the form of a string set
     * from the dictionary containing the amount of occurrences for each String in data source
     * and from the BPE obtained from that dictionary.
     *
     * @param dictionary
     * @param bpe
     * @return
     */
    private Set<String> collectSubwords(HashMap<String, Integer> dictionary, BPE bpe) {
        HashMap<String, Integer> subwordCounts = new HashMap<>();

        /* for each word in dictionary, split it using the rules in the BPE model
        and add the corresponding subwords and their frequencies in subwordCounts */
        for (Map.Entry<String, Integer> entry : dictionary.entrySet()) {
            for (String subword : bpe.apply(new String[]{entry.getKey()}, null)) {
                if (subwordCounts.containsKey(subword))
                    subwordCounts.put(subword, subwordCounts.get(subword) + entry.getValue());
                else
                    subwordCounts.put(subword, entry.getValue());
            }
        }

        /* prune the vocabulary and reduce its size if necessary */
        //subwordCounts = this.prune(subwordCounts);
        //subwordCounts = this.reduce(subwordCounts);

        return subwordCounts.keySet();
    }

    /**
     * This pruning method removes the least frequent subwords from a passed subwordCounts map
     * until the result only contains vocabPruningThreshold % of its initial overall value
     *
     * @param subwordCounts
     */
    private void prune(HashMap<String, Integer> subwordCounts) {

        /* if no vocabPruningThreshold was set, return immediately without pruning */
        if (this.vocabPruningThreshold == -1)
            return;

        //LinkedHashMap<String, Integer> sorted = /* ... sort */;

        /* compute the overall sum of the occurrences of all subwords */
        int total = 0;
        for (Map.Entry<String, Integer> entry : subwordCounts.entrySet())
            total += entry.getValue();

        for (Map.Entry<String, Integer> entry : subwordCounts.entrySet()) {

        }

    }
}