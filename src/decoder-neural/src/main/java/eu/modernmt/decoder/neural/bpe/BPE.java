package eu.modernmt.decoder.neural.bpe;

import java.util.*;

/**
 * Created by andrea on 07/12/17.
 * A BPE stores information on how to split words of the source language into BPE valid subwords for that language.
 * Each of these rules has a priority value.
 */
public class BPE {

    /*------------------------------------------------------------------------------------------*/

    /**
     * A BPE.Rule models a couple of consecutive Subwords (aka BPE termps) included in this BPE model.
     * It thus represents how a string should be splitted in two terms to make it compliant to this BPE model.
     * <p>
     * Since a rule is immutable, it is implemented using Strings.
     */
    public static class Rule {
        public final String leftSubword;
        public final String rightSubword;

        public Rule(String leftSubword, String rightSubword) {
            this.leftSubword = leftSubword;
            this.rightSubword = rightSubword;
        }

        //equals and hashcode are necessary because Rules will be put in an HashSet

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Rule rule = (Rule) o;

            if (leftSubword != null ? !leftSubword.equals(rule.leftSubword) : rule.leftSubword != null) return false;
            return rightSubword != null ? rightSubword.equals(rule.rightSubword) : rule.rightSubword == null;
        }

        @Override
        public int hashCode() {
            int result = leftSubword != null ? leftSubword.hashCode() : 0;
            result = 31 * result + (rightSubword != null ? rightSubword.hashCode() : 0);
            return result;
        }
    }


    /*------------------------------------------------------------------------------------------*/

    /**
     * A SymbolPair represents a couple of consecutive symbols that appear in a string to encode using this BPE model.
     * <p>
     * A Symbol is a text that may or may not correspond to a BPE Subword.
     * Symbols are typically used by a BPE model while progressively splitting and re-merging a string to encode.
     * <p>
     * Since symbols are often subject to changes (i.e. replace and concat), they are implemented as Symbols.
     */
    protected static class SymbolPair {
        public final Symbol leftSymbol;
        public final Symbol rightSymbol;

        public SymbolPair(Symbol leftSymbol, Symbol rightSymbol) {
            this.leftSymbol = leftSymbol;
            this.rightSymbol = rightSymbol;
        }

        /**
         * This method generates a Rule based on this SymbolPair.
         * This is typically used when the BPE is encoding a string and must check
         * if a SymbolPair occurring in the String matches a BPE rule or not.
         *
         * @return a Rule based on this SymbolPair
         */
        public Rule asRule() {
            return new Rule(leftSymbol.toString(), rightSymbol.toString());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            SymbolPair that = (SymbolPair) o;

            if (leftSymbol != null ? !leftSymbol.equals(that.leftSymbol) : that.leftSymbol != null) return false;
            return rightSymbol != null ? rightSymbol.equals(that.rightSymbol) : that.rightSymbol == null;
        }

        @Override
        public int hashCode() {
            int result = leftSymbol != null ? leftSymbol.hashCode() : 0;
            result = 31 * result + (rightSymbol != null ? rightSymbol.hashCode() : 0);
            return result;
        }
    }


    // ------------------------------     LRU CACHE    ------------------------------

    /**
     * A BPE.LRUCache is a LRU cache working for BPE encoded strings.
     * <p>
     * It is typically used to prevent encoding the same string multiple times.
     * When a string is passed for encoding, the BPE object first check if it is already in cache;
     * if it is, the cached result will be returned immediately.
     */
    private static class LRUCache extends LinkedHashMap<String, String[]> {
        private static final int DEFAULT_SIZE = 1000;
        private final int maxSize;

        public LRUCache(int maxSize) {
            super(16, .75f, true);
            this.maxSize = maxSize;
        }

        public LRUCache() {
            this(DEFAULT_SIZE);
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String[]> entry) {
            return this.size() > maxSize;
        }
    }


    // ------------------------------     BPE    ------------------------------

    public static final String END_OF_WORD = "</w>";
    private final Map<Rule, Integer> rule2priority;
    private final Map<String, Rule> string2rule;
    private final String separator;
    private final LRUCache cache;

    public BPE(Map<Rule, Integer> rule2priority, String separator) {
        this.rule2priority = rule2priority;
        this.separator = separator;
        this.string2rule = new HashMap<>();
        for (Map.Entry<Rule, Integer> entry : rule2priority.entrySet())
            string2rule.put(entry.getKey().leftSubword + entry.getKey().rightSubword, entry.getKey());
        this.cache = new LRUCache();
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

    /**
     * This method encodes a String using BPE using a specific vocabulary of allowed subwords.
     * <p>
     * More in detail, this method first splits the passed String into a list of mono-character symbols,
     * and then re-merges such symbols in larger subwords when they match a BPE rule.
     * <p>
     * Finally, if uses the vocabulary (if one is passed) to check if it has merged the subwords too much
     * (and so if there is the possibility to split them obtaining still valid subwords).
     *
     * @param wordString the word to encode using this BPE model
     * @param vocabulary the vocabulary to use for this encoding
     * @return the BPE-encoded string as a list of subwords
     */
    private String[] encode(String wordString, Set<String> vocabulary) {

        /* search for the word in cache, and if it is found return the cached result */
        if (this.cache.containsKey(wordString))
            return this.cache.get(wordString);

        /* get the word as a list of symbols and append the end of word sequence to the last symbol of the word */
        Symbol[] word = this.getSymbols(wordString);
        word[word.length - 1].append(END_OF_WORD);

        Set<SymbolPair> symbolPairs = this.getPairs(word);

        /* if the initial string is empty or contains one char only, it can not be split
           so the BPE encoding is the string itself (in an array) */
        if (symbolPairs.isEmpty())
            return new String[]{wordString};

        while (true) {
            /* get the SymbolPair with minimum priority in the BPE rules */
            SymbolPair bigram = Collections.min(symbolPairs, Comparator.comparing(this::getPriorityFor));

            /* if bigram is not in model, it means that its priority was Integer.MAX_VALUE.
            Since it was the symbol pair with minimum priority, it means that no pair in symbolPairs is in model.
            So the splitting phase is over. */
            if (!this.rule2priority.containsKey(bigram.asRule()))
                break;

            /*create the updatedWord as an arrayList instead of array initially to allow extension */
            ArrayList<Symbol> updatedWord = new ArrayList<>();

            /* inner loop: search for consecutive occurrences bigram.leftSymbol and bigram.rightSymbol.
             If found, merge them and put their merge in updatedWord
             Else just put the current symbol in updatedWord*/
            int index = 0;
            while (index < word.length) {
                if ((word[index].equals(bigram.leftSymbol)) && (index < (word.length - 1)) && (word[index + 1].equals(bigram.rightSymbol))) {
                    updatedWord.add(Symbol.merge(word[index], word[index + 1]));
                    index += 2;
                } else {
                    updatedWord.add(word[index]);
                    index += 1;
                }
            }

            /*overwrite word with the updated word*/
            word = updatedWord.toArray(new Symbol[updatedWord.size()]);

            /*if the word was merged back into one Symbol, it means that the whole word was a BPE valid term, so break here*/
            if (word.length == 1)
                break;
            else
                symbolPairs = getPairs(word);
        }

        /* delete END_OF_WORD from the last symbol */
        Symbol last = word[word.length - 1];
        if (last.getAppendedContent().equals("</w>"))
            last.setAppendedContent("");

        if (last.toString().isEmpty())
            Arrays.copyOf(word, word.length - 1);


        /* split again if some symbols have been merged too much */
        if (vocabulary != null)
            word = this.splitWithVocabulary(word, vocabulary);

        /* return the processed word as an array of strings instead of symbols */
        String[] result = new String[word.length];
        for (int i = 0; i < word.length; i++)
            result[i] = word[i].toString();

        /* put the computed result in cache */
        this.cache.put(wordString, result);

        return result;
    }


    /**
     * This private utility method transforms a string into an array of mono-character symbols.
     * Therefore, each symbol in array is a Symbol only containing one character.
     *
     * @return the array of obtained Symbols
     */
    private Symbol[] getSymbols(String string) {

        /*list, not array, because even if we know the initial size this list will be updated in the future
        * (removing elements) so its size will change!*/
        Symbol[] symbols = new Symbol[string.length()];

        for (int i = 0; i < string.length(); i++)
            symbols[i] = (new Symbol(string, i, i + 1));

        return symbols;
    }

    /**
     * This private method gets all pairs of consecutive symbols in a symbol array.
     * It returns a set containing all the found pairs
     * (which means that returned pairs are not ordered and duplicates are skipped).
     *
     * @return the set containing the pairs of consecutive symbols obtained from the initial symbol array
     */
    private Set<SymbolPair> getPairs(Symbol[] symbols) {
        Set<SymbolPair> pairs = new HashSet<>();

        for (int i = 0; i < symbols.length - 1; i++)
            pairs.add(new SymbolPair(symbols[i], symbols[i + 1]));

        return pairs;
    }

    /**
     * This private method...
     *
     * @param word
     * @param vocabulary
     * @return
     */
    private Symbol[] splitWithVocabulary(Symbol[] word, Set<String> vocabulary) {
        return word;
    }

    /**
     * This method gets the priority of a pair of symbols in this BPE model.
     *
     * @return the set containing the pairs of consecutive symbols obtained from the initial symbol list
     */

    public Integer getPriorityFor(SymbolPair pair) {
        return this.rule2priority.getOrDefault(pair.asRule(), Integer.MAX_VALUE);
    }

    /**
     * This method gets the priority of a Rule in this BPE model.
     * It returns Integer.MAX_VALUE if the value
     *
     * @return the set containing the pairs of consecutive symbols obtained from the initial symbol list
     */
    public Integer getPriorityFor(Rule rule) {
        return this.rule2priority.getOrDefault(rule, Integer.MAX_VALUE);
    }

}