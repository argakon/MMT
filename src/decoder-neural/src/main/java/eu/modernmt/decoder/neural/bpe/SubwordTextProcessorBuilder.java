package eu.modernmt.decoder.neural.bpe;

import eu.modernmt.model.corpus.MultilingualCorpus;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.util.*;

/**
 * A SubwordTextProcessorBuilder has the responsibility to
 * scan a series of data sources and use them to create a SubwordTextProcessor
 * .
 * Created by andrea on 20/12/17.
 */
public class SubwordTextProcessorBuilder {

    private List<Symbol> symbols = null;
    private int maxVocabularySize = -1;
    private float vocabPruningThreshold = -1;
    private int minFrequency = 2;
    private float similarityThreshold = 0.5F;
    private String separator = "@@";

    /* internal models of the builder */
    private HashMap<String, Integer> sourceDictionary;  // counter for word occurrences in training source data (words, not subwords!)
    private HashMap<String, Integer> targetDictionary;  // counter for word occurrences in training target data (words, not subwords!)
    private HashMap<Character, Integer> sourceAlphabet; // counter char occurrences in training source data
    private HashMap<Character, Integer> targetAlphabet; // counter char occurrences in training target data

    public SubwordTextProcessorBuilder(List<Symbol> symbols, int maxVocabularySize, float vocabPruningThreshold, int minFrequency, float similarityThreshold, String separator) {
        this.symbols = symbols;
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

        MultilingualCorpus.MultilingualLineReader reader = null;

        // STEP 1: compute dictionaries
        /*for each corpus in list, scan it all and add all of its StringPairs in model.
        * This way we will have counts for source and target dictionaries and alphabets.*/
        for (MultilingualCorpus dataSource : dataSources) {
            try {
                reader = dataSource.getContentReader();
                MultilingualCorpus.StringPair pair = reader.read();
                while (pair != null) {
                    this.addStringPair(pair);
                    pair = reader.read();
                }
            } finally {
                IOUtils.closeQuietly(reader);
            }
        }

        // STEP 2: compute BPE models and rules

        /* if the alphabets are too similar then use a unique set of BPE rules,
        otherwise compute the rules for source and target languages separately */
        BPE sourceBPE;
        BPE targetBPE;
        if (this.cosineSimilarity(sourceAlphabet, targetAlphabet) > this.similarityThreshold) {
            sourceBPE = BPE.learnFromTerms(this.merge(sourceDictionary, targetDictionary), sourceDictionary, symbols, minFrequency, separator);
            targetBPE = null;
        } else {
            sourceBPE = BPE.learnFromTerms(sourceDictionary, symbols, minFrequency, separator);
            targetBPE = BPE.learnFromTerms(targetDictionary, symbols, minFrequency, separator);
        }


        // STEP 3: create vocabularies containing the valid subwords for the SubwordsTextProcessor to build
        /* vocabularies are computed by applying the BPE rules found in step2 to the dictionaries computed in step 1*/
        Set<String> sourceTerms = this.collectSubwords(sourceDictionary, sourceBPE);
        Set<String> targetTerms = this.collectSUbwords(targetDictionary, targetBPE);

        return new

                SubwordTextProcessor(sourceCodes, targetCodes, sourceVocabulary, targetVocabulary);

    }

    /**
     * This method adds the words and chars of the passed source and target strings of a stringpair
     * to the counters source and target dictionaries and alphabets respectively
     *
     * @param pair the stringpair to add in the model
     */
    private void addStringPair(MultilingualCorpus.StringPair pair) {

        /*add the source words and chars to sourceDictionary and sourceAlphabet counters*/
        for (String word : pair.source.trim().split(" ")) {
            if (sourceDictionary.containsKey(word))
                sourceDictionary.put(word, sourceDictionary.get(word) + 1);
            else
                sourceDictionary.put(word, 1);

            for (char character : word.toCharArray()) {
                if (sourceAlphabet.containsKey(character))
                    sourceAlphabet.put(character, sourceAlphabet.get(character) + 1);
                else
                    sourceAlphabet.put(character, 1);
            }
        }

        /*add the target words and chars to targetDictionary and targetAlphabet counters*/
        for (String word : pair.target.trim().split(" ")) {
            if (targetDictionary.containsKey(word))
                targetDictionary.put(word, targetDictionary.get(word) + 1);
            else
                targetDictionary.put(word, 1);

            for (char character : word.toCharArray()) {
                if (targetAlphabet.containsKey(character))
                    targetAlphabet.put(character, targetAlphabet.get(character) + 1);
                else
                    targetAlphabet.put(character, 1);
            }
        }

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
        subwordCounts = this.prune(subwordCounts);
        subwordCounts = this.reduce(subwordCounts);

        return subwordCounts.keySet();
    }

    /**
     * This pruning method removes the least frequent subwords from a passed subwordCounts map
     * until the result only contains vocabPruningThreshold % of its initial overall value
     * @param subwordCounts
     */
    private void prune(HashMap<String, Integer> subwordCounts) {

        /* if no vocabPruningThreshold was set, return immediately without pruning */
        if (this.vocabPruningThreshold == -1)
            return;

        LinkedHashMap<String, Integer> sorted = /* ... sort */;

        /* compute the overall sum of the occurrences of all subwords */
        int total = 0;
        for (Map.Entry<String, Integer> entry : subwordCounts.entrySet())
            total += entry.getValue();

        for (Map.Entry<String, Integer> entry : subwordCounts.entrySet()) {

        }

        /* lui calcola una soglia di occorrenze oltre le quali una subword non deve essere usata */
        /* per farlo, ordina l'entryset in senso decrescente, quindi per ogni subword
            - aggiorna un counter parziale con la somma dei counts visti finora
            - se il counter aggiornato supera total * vocabthreshold, allora segna che la threshold di frequenza da superare è la frequenza di quell'elemento
                    (essendo gli elementi
                    ma segna
        */
         */
        counter = 0
        threshold = 0

        for w, c in terms.most_common():        #?????
        counter += c
        if counter >= total * self._vocab_pruning_threshold:
        threshold = c
        break

        for w, c in terms.items():
        if c<threshold:
        del terms[ w]

        return terms

    }
}