package eu.modernmt.decoder.neural.bpe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by andrea on 07/12/17.
 * A BPE stores information on how to split words of the source language into BPE valid subwords for that language.
 * Each of these rules has a priority value.
 */
public class BPE {
    private final Map<Rule, Integer> rule2priority;
    private final Map<String, Rule> string2rule;
    private final String separator;

    /**
     * A BPE.Rule models a BPE rule on how to split a specific word into two subwords.
     * The Rule is thus implemented as a simple subwords pair.
     */
    public static class Rule {
        private final String leftSubword;
        private final String rightSubword;

        public Rule(String leftSubword, String rightSubword) {
            this.leftSubword = leftSubword;
            this.rightSubword = rightSubword;
        }

        public String getLeftSubword() {
            return leftSubword;
        }

        public String getRightSubword() {
            return rightSubword;
        }
    }

    public BPE(Map<Rule, Integer> rule2priority, String separator) {
        this.rule2priority = rule2priority;
        this.separator = separator;
        this.string2rule = new HashMap<>();
        for (Map.Entry<Rule, Integer> entry : rule2priority.entrySet())
            string2rule.put(entry.getKey().getLeftSubword() + entry.getKey().getRightSubword(), entry.getKey());
    }

    /**
     * Encode the passed strings and return the resulting subwords
     *
     * @param words       the strings to BPE encode
     * @param subwordsSet the vocabulary to use for the BPE encoding
     * @return the resulting BPE subwords, in the form of a String array
     */
    public String[] apply(String[] words, Set<String> subwordsSet) {
        ArrayList<String> resultWords = new ArrayList<>();

        for (String currentWord : words) {
            String[] currentSubwords = this.encode(currentWord, subwordsSet);

            for (int i = 0; i < currentSubwords.length - 1; i++)
                resultWords.add(currentSubwords[i] + this.separator);

            resultWords.add(currentSubwords[currentSubwords.length - 1]);
        }
        String[] resultWordsArray = new String[resultWords.size()];
        return resultWords.toArray(resultWordsArray);
    }


    private String[] encode(String wordString, Set<String> vocabulary) {

        StringBuilder word = new StringBuilder(wordString);


        //TODO: CACHE
        // if this.cache.contains(word)
        //       return cache.get(word)


        String endOfWord = "</w>";

        String lastWord = String[]
        return null;
    }
}
