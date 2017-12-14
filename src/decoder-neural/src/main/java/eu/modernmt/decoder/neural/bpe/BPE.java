package eu.modernmt.decoder.neural.bpe;

import org.apache.commons.lang.StringUtils;

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

            Collections.addAll(resultWords, currentSubwords);
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
        word[word.length - 1].setAppendedContent(END_OF_WORD);

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
            if (!this.hasRuleFor(bigram))
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

        /* add the separators to each symbol that the word was split in so far (except the last one of course) */
        for (Symbol symbol : word)
            if (!symbol.isFinal())
                symbol.setAppendedContent(separator);

        /*split again if some symbols have been merged too much */
        if (vocabulary != null)
            word = this.splitUsingVocabulary(word, vocabulary);

        /* return the processed word as an array of strings instead of symbols.
         * On creating the string for the last symbol remove the '</w>' from it. */
        String[] result = new String[word.length];
        for (int i = 0; i < word.length - 1; i++)
            result[i] = word[i].toString();
        Symbol lastSymbol = word[word.length - 1];
        if (lastSymbol.getAppendedContent().equals(END_OF_WORD))
            lastSymbol.setAppendedContent("");
        if (!lastSymbol.toString().isEmpty())
            result[word.length - 1] = lastSymbol.toString();

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
     * This private method checks for each symbol in word if it is in-vocabulary.
     * If it is, it is not changed.
     * Otherwise, it means that the previous merging step has generated an out-of-vocabulary subword,
     * so it must be splitted again until the generated subwords belong to the vocabulary.
     *
     * @param word       the word the symbols of which must be checked
     * @param vocabulary the vocabulary to use to check symbols
     * @return the resulting array of Symbols
     */
    private Symbol[] splitUsingVocabulary(Symbol[] word, Set<String> vocabulary) {
        /*remove all ENDOFWORD*/
        /*handle the last word separately*/


        ArrayList<Symbol> result = new ArrayList<>();

        for (Symbol symbol : word) {
            if (this.vocabularyContains(vocabulary, symbol))
                result.add(symbol);
            else
                Collections.addAll(result, this.recursiveSplit(symbol, vocabulary));
        }

        return result.toArray(new Symbol[result.size()]);
    }

    /* TODO: this should be made easier by moving the vocabulary check in splitUsingVocabulary into recursiveSplit.
    This way it is possible to make recursively this way:
        - check if in vocab
            - if yes, return
            - if no split using rule and call recursivesplit on children
    */

    /**
     * This private method recursively splits into smaller units a symbol that is not in vocabulary
     * by reversing BPE merges until all the obtained sub-symbols are either in-vocabulary, or cannot be split further.
     *
     * @param symbol     the symbol to check (and possibly split)
     * @param vocabulary the vocabulary to use to check the symbol
     * @return the resulting array of split sub-symbols
     */
    private Symbol[] recursiveSplit(Symbol symbol, Set<String> vocabulary) {
        ArrayList<Symbol> result = new ArrayList<>();

        Rule rule = this.getReverseRuleFor(symbol);

        /* BASE CASE 1: if no rule was found, it means that the symbol cannot be split further, so return it*/
        if (rule == null) {
            return new Symbol[]{symbol};
        }

        /* else split the symbol using the retrieved rule */
        SymbolPair split = this.splitByRule(symbol, rule);

        /* BASE CASE 2(for left): if the symbol is in vocabulary, add it to list; else
        *  RECURSIVE CASE: if the symbol is not in vocabulary, split it again recursively */
        if (this.vocabularyContains(vocabulary, split.leftSymbol)) {
            result.add(split.leftSymbol);
        } else {
            Collections.addAll(result, recursiveSplit(split.leftSymbol, vocabulary));
        }

        /* BASE CASE 2 (for right): if the symbol is in vocabulary, add it to list; else
        *  RECURSIVE CASE: if the symbol is not in vocabulary, split it again recursively */
        if (this.vocabularyContains(vocabulary, split.rightSymbol))
            result.add(split.rightSymbol);
        else
            Collections.addAll(result, recursiveSplit(split.rightSymbol, vocabulary));

        return result.toArray(new Symbol[result.size()]);
    }

    /**
     * This private method splits a Symbol in a SymbolPair using a Rule.
     * This is typically during the recursive split using vocabulary.
     * <p>
     * It must be already verified that the symbol matches the rule.
     *
     * @return a SymbolPair obtained by splitting the passed Symbol using the passed BPE Rule
     */
    private SymbolPair splitByRule(Symbol symbol, Rule rule) {

        /* the appended content for the last symbol  */
        String leftSubword = rule.leftSubword;
        String rightSubword = rule.rightSubword;

        String rightAppendedContent = symbol.getAppendedContent();

        /* if the right subword from the rule ends with the end of word, then
        *   - it must not be considered as part of the string for the right subsymbol
        *   - but it must be considered as part of the appended content for the right subsymbol
        * because in BPE the separator only appears in vocabulary, and END OF WORD only appears in rules*/
        if (rightSubword.endsWith(END_OF_WORD)) {
            rightSubword = StringUtils.chomp(rightSubword, END_OF_WORD);
            rightAppendedContent = END_OF_WORD;
        }

        Symbol left = new Symbol(
                symbol.getOriginal(),
                symbol.getStartIndex(),
                symbol.getStartIndex() + leftSubword.length(),
                separator);     // the left symbol will always have a separator because it is created from splitting


        Symbol right = new Symbol(
                symbol.getOriginal(),
                symbol.getStartIndex() + leftSubword.length(),
                symbol.getStartIndex() + leftSubword.length() + rightSubword.length(),
                rightAppendedContent);

        return new SymbolPair(left, right);
    }


    /* ---------------------- ACCESS METHODS ---------------------------
    * These methods are used to access the BPE data structures (rule maps, vocabularies, ecc).
    * One should always use these methods instead of accessing the structures directly
    * because they take into account the fact how symbols and symbol pairs are contained in the data structures.
    * For instance, END_OF_WORD is only used in rules, but not in vocabularies.
    * On the other hand, the separator is only used in vocabularies, but not in
    *
    * These access methods
    *
    * */

    /**
     * This method checks whether this BPE model has a Rule matching a specific SymbolPair or not.
     *
     * @return true if there is a Rule matching the SymbolPair; false if there is not.
     */
    public boolean hasRuleFor(SymbolPair bigram) {
        return this.rule2priority.containsKey(new Rule(
                StringUtils.chomp(bigram.leftSymbol.toString(), separator),
                StringUtils.chomp(bigram.rightSymbol.toString(), separator)));
    }

    /**
     * This method checks whether this BPE model, in its reverse rules map (string to rule map)
     * has an entry with the same string as the symbol content.
     * <p>
     * If the symbol has the separator as appended content, the separator is ignored because it is not employed in rule maps.
     *
     * @return true if there is a reverse rule matching the Symbol; false if there is not.
     */
    public boolean hasReverseRuleFor(Symbol symbol) {
        return this.string2rule.containsKey(StringUtils.chomp(symbol.toString(), separator));
    }

    /**
     * This method checks whether this BPE model, in its reverse rules map (string to rule map)
     * has an entry with the same string as the symbol content.
     * <p>
     * If the symbol has the separator as appended content, the separator is ignored because it is not employed in rule maps.
     *
     * @return true if there is a reverse rule matching the Symbol; false if there is not.
     */
    public Rule getReverseRuleFor(Symbol symbol) {
        return this.string2rule.get(StringUtils.chomp(symbol.toString(), separator));
    }

    /**
     * This method checks whether a SymbolPair matches a Rule in this BPE model or not.
     * It returns True if it has, false if it hasn't.
     * <p>
     * If the symbol has the END_OF_WORD as appended content, END_OF_WORD is ignored because it is not employed in vocabularies.
     *
     * @return the set containing the pairs of consecutive symbols obtained from the initial symbol list
     */
    public boolean vocabularyContains(Set<String> vocabulary, Symbol symbol) {
        return vocabulary.contains(StringUtils.chomp(symbol.toString(), END_OF_WORD));
    }

    /**
     * This method gets the priority of a pair of symbols in this BPE model.
     *
     * @return the set containing the pairs of consecutive symbols obtained from the initial symbol list
     */
    public Integer getPriorityFor(SymbolPair pair) {
        return this.rule2priority.getOrDefault(
                new Rule(StringUtils.chomp(pair.leftSymbol.toString(), separator), StringUtils.chomp(pair.rightSymbol.toString(), separator)),
                Integer.MAX_VALUE);
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