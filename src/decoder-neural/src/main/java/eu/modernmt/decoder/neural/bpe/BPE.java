package eu.modernmt.decoder.neural.bpe;

import org.apache.commons.lang.StringUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by andrea on 07/12/17.
 * A BPE stores information on how to split words of the source language into BPE valid subwords for that language.
 * Each of these rules has a priority value.
 */
public class BPE {

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

    // --------------------- TRAINING METHODS ---------------------

    public static BPE train(HashMap<String, Integer> stringDictionary, int maxRules, float minFrequency, String separator) {
        HashMap<Rule, Integer> rule2priority = new HashMap<>();

        /* The training makes use of four main data structures.
         *   - SORTED DICTIONARY: main data structure in training.
         *           It is a list of entries "word as a sequence of symbols -> original term frequency".
         *           Note: it has an entry for each word in the original corpora: not subwords, but whole terms.
         *           The sorted dictionary is sorted by decreasing frequency.
         *
         *   - DICTIONARY STATS: secondary data structure depending on the sorted dictionary.
         *           It is a map with entries "symbolPair -> corresponding overall frequency in sortedDictionary"
         *           The overall frequency is computed as the sum of the frequencies of all terms where the pair occurs.
         *
         *   - DICTIONARY BIGSTATS: secondary data structure depending on the sorted dictionary.
         *           It is a map with same structure as stats. When stats is pruned, big stats is not
         *           (it is just updated differently, never "forgetting" symbol pairs)
         *           so it keeps full statistics for when we need to access pruned items.
         *
         *   - DICTIONARY INDEX: secondary data structure for accessing the sorted dictionary by symbol pair.
         *           It contains entries "symbolPair -> corresponding positions where the symbolPair occurs in dictionary".
         *           For each position, it also stores the occurrences of that symbolPair in the dictionary term.
         *   */
        ArrayList<Map.Entry<Symbol[], Integer>> sortedDictionary = new ArrayList<>();
        Map<Symbol.Pair, Integer> stats = new HashMap<>();
        Map<Symbol.Pair, Map<Integer, Integer>> indices = new HashMap<>();
        Map<Symbol.Pair, Integer> bigStats = new HashMap<>();


        // ------------ Data Structures Initialization ------------

        stringDictionary.entrySet().stream().sorted(Map.Entry.comparingByValue()).forEach(entry -> {
            Symbol[] termSymbols = splitIntoSymbols(entry.getKey(), separator, ""); //split in mono-char symbols
            int frequency = entry.getValue();
            sortedDictionary.add(new AbstractMap.SimpleEntry<>(termSymbols, frequency));
            int positionInDict = sortedDictionary.size() - 1;

            for (Symbol.Pair pair : getPairs(termSymbols)) {
                //put pair into stats or update its value anyway
                stats.put(pair, stats.getOrDefault(pair, 0) + frequency);

                /* create a new nestedMap under indices(pair) if this pair was still unseen before;
                increment by one the nestedMap value under positionInDict
                */
                if (!indices.containsKey(pair))
                    indices.put(pair, new HashMap<>());
                indices.get(pair).put(positionInDict, indices.get(pair).getOrDefault(positionInDict, 0) + 1);
            }
        });
        bigStats.putAll(stats);

        // ------------ BPE rules definition ------------

        /*threshold is inspired by Zipfian assumption, but should only affect speed*/
        float threshold = Collections.max(stats.values()) / 10F;

        int ruleCount = 0;
        while (ruleCount < maxRules) {

            // find the most frequent pair and make it the new rule, using its frequency for computing the rule priority
            Symbol.Pair mostFrequentPair = null;
            int frequency = 0;

            if (!stats.isEmpty()) {
                mostFrequentPair = Collections.max(stats.keySet(), Comparator.comparing(stats::get));
                frequency = stats.get(mostFrequentPair);
            }
            /* if this is not the first iteration and the frequency of the mostFrequentPair is lesser than threshold,
            * it is possible that we have missed the best pair.
            * This is possible because we have looked for it in stats, that has been already pruned.
            * It is also possible that after pruning stats has become empty.
            * In both cases, re-prune (why?) and look in bigStats for the mostFrequentPair*/
            if (stats.isEmpty() || (ruleCount > 0 && stats.get(mostFrequentPair) < threshold)) {
                /*re-prune (why?) and overwrite stats with bigStats*/
                prunePairStatistics(stats, bigStats, threshold);
                stats.clear();
                stats.putAll(bigStats);

                /* recompute the mostFrequentPair and its frequency on the new stats (which are the previous bigStats */
                mostFrequentPair = Collections.max(stats.keySet(), Comparator.comparing(stats::get));
                frequency = stats.get(mostFrequentPair);

                /* recompute the threshold and prune again statistics*/
                threshold = stats.get(mostFrequentPair) * ruleCount / (ruleCount * 10000F);
                prunePairStatistics(stats, bigStats, threshold);
            }

            /* if the mostFrequentPair is lesser than the threshold, then the training is over */
            if (frequency < threshold)
                break;

            /* now that the pair has been found, put a new rule for it in rule2priority and update stats, dictionary and indices*/
            rule2priority.putIfAbsent(new Rule(mostFrequentPair, separator), frequency);
            List<PairChange> changes = BPE.mergePair(mostFrequentPair, separator, sortedDictionary, indices);
            updatePairStatistics(mostFrequentPair, separator, changes, stats, indices);
            stats.put(mostFrequentPair, 0);

            /* once in 100 rules, prune statistics again */
            if (ruleCount % 100 == 0)
                BPE.prunePairStatistics(stats, bigStats, threshold);

            ruleCount++;
        }

        return new BPE(rule2priority, separator);
    }

    /**
     * This private method prunes the passed statistics map to maximize efficiency;
     * it accordingly updates the bigStats map
     *
     * @param stats     map containing entries like [symbolPair -> frequency in sortedDictionary]
     * @param bigStats  map identical to stats, but it gets updated differently during pruning
     * @param threshold threshold to use for pruning the stats
     */
    private static void prunePairStatistics(Map<Symbol.Pair, Integer> stats, Map<Symbol.Pair, Integer> bigStats, float threshold) {
        for (Map.Entry<Symbol.Pair, Integer> entry : stats.entrySet()) {
            Symbol.Pair pair = entry.getKey();
            Integer frequency = entry.getValue();
            if (frequency < threshold) {
                stats.remove(pair);
                //what? when can frequency be < 0?
                bigStats.put(pair, frequency < 0 ? bigStats.get(pair) + frequency : frequency);
            }
        }
    }


    /**
     * This private class is used to store the data on an updated symbol pair
     */
    private static class PairChange {
        public final int positionInDictionary;
        public final int frequencyInDictionary;
        public final Symbol[] oldWord;
        public final Symbol[] newWord;

        private PairChange(int positionInDictionary, int frequencyInDictionary, Symbol[] oldWord, Symbol[] newWord) {
            this.positionInDictionary = positionInDictionary;
            this.frequencyInDictionary = frequencyInDictionary;
            this.oldWord = oldWord;
            this.newWord = newWord;
        }
    }

    /**
     * This private method replaces all occurrences of a symbol pair ('A', 'B') with a new symbol 'AB'
     * and it updates the sortedDictionary
     *
     * @param pair             the SymbolPair with the two symbols to merge
     * @param sortedDictionary list that contains, for each word in data sources (not subword: word) represented as symbol list,
     *                         the corresponding frequency in data sources. It is ordered by decreasing frequency
     * @param indices          map that holds for each symbolpair, the positions in sortedDictionary where it occurs.
     *                         for each position in sortedDictionary, it also stores the amount of times it occurs there.
     */
    private static List<PairChange> mergePair(Symbol.Pair pair, String separator, ArrayList<Map.Entry<Symbol[], Integer>> sortedDictionary, Map<Symbol.Pair, Map<Integer, Integer>> indices) {

        List<PairChange> changes = new ArrayList<>();

        String first = pair.leftSymbol.getContentWithout(separator);
        String second = pair.rightSymbol.getContentWithout(separator);

        //the symbol to merge, as a unique string
        String pairString = (first + second).replace("\\", "\\\\");

        String regex = "(?<!\\S)" + Pattern.quote(first + " " + second) + "(?!\\S)";
        Pattern pattern = Pattern.compile(regex);
        Map<Integer, Integer> pos2freq = indices.get(pair);
        for (Map.Entry<Integer, Integer> indexEntry : pos2freq.entrySet()) {
            int posInDict = indexEntry.getKey();
            int freqInDict = indexEntry.getValue();

            if (freqInDict < 1)
                continue;

            Map.Entry<Symbol[], Integer> dictEntry = sortedDictionary.get(posInDict);

            /*get the word in dict where the the symbolpair to merge occurred,
            rebuild the original old string (using whitespaces as string separators),
            replace the first occurrence of pairString in the rebuilt old string using regex,
            resplit the newly obtained string (with replacement performed) in symbols*/
            Symbol[] oldWord = dictEntry.getKey();
            int wordFrequency = dictEntry.getValue();
            String oldWordString = mergeIntoString(oldWord, separator, " ");
            Matcher matcher = pattern.matcher(pairString);
            String newWordString = matcher.replaceFirst(oldWordString);
            Symbol[] newWord = splitIntoSymbols(newWordString, separator, " ");

            /*update sortedDictionary*/
            sortedDictionary.add(posInDict, new AbstractMap.SimpleEntry<>(newWord, wordFrequency));
            changes.add(new PairChange(posInDict, wordFrequency, oldWord, newWord));
        }

        return changes;
    }


    /**
     * This private method updates the stats map and indices map based on a list of changes performed on a Symbol Pair.
     * <p>
     * This method is typically called immediately after the symbol pair has been merged,
     * so only pairs that overlap with occurrences of this pair are affected, and need to be updated,
     * in the stats and indices data structures
     * <p>
     *
     * @param pair
     * @param changes
     * @param stats
     * @param indices
     */
    private static void updatePairStatistics(Symbol.Pair pair, String separator, List<PairChange> changes, Map<Symbol.Pair, Integer> stats, Map<Symbol.Pair, Map<Integer, Integer>> indices) {
        stats.put(pair, 0);
        indices.put(pair, new HashMap<>());
        Symbol mergedSymbol = new Symbol(pair.leftSymbol.original, pair.leftSymbol.startIndex, pair.rightSymbol.endIndex, pair.rightSymbol.suffix);

        for (PairChange change : changes) {
            //find all instances of pair, and update frequency/indices around it
            while (true) {
                for (int i = 0; i < change.oldWord.length - 1; i++) {

                    // if the leftSymbol and rightSymbol are contained consecutively in the old word
                    if (pair.leftSymbol.equals(change.oldWord[i]) && pair.rightSymbol.equals(change.oldWord[i + 1])) {

                        /* If there was at least another symbol before the leftSymbol occurrence,
                        it means that the oldWord is like "A B C" and we are merging "(B, C)" into BC.
                        In this case A is not be followed by B anymore, but by BC, so:
                            - the frequency of (A, B) in stats must be reduced by the frequency of the old word in sortedDictionary.
                            - in the indices entry for (A, B), the amount of occurrences in oldWord must be reduced by 1.
                            - the frequency of (A, BC) in stats must be increased by frequency of the old word in sortedDictionary.
                            - in the indices entry for (A, BC), the amount of occurrences in oldWord must be increased by 1.*/
                        if (i > 0) {
                            Symbol.Pair oldPrevPair = new Symbol.Pair(change.oldWord[i - 1], change.oldWord[i]);
                            Symbol.Pair newPrevPair = new Symbol.Pair(change.oldWord[i - 1], mergedSymbol);

                            stats.put(oldPrevPair, stats.getOrDefault(oldPrevPair, 0) - change.frequencyInDictionary);
                            Map<Integer, Integer> oldPrevPairIndexEntry = indices.getOrDefault(oldPrevPair, new HashMap<>());
                            oldPrevPairIndexEntry.put(change.positionInDictionary,
                                    oldPrevPairIndexEntry.getOrDefault(change.positionInDictionary, 0) - 1);

                            stats.put(newPrevPair, stats.getOrDefault(newPrevPair, 0) + change.frequencyInDictionary);
                            Map<Integer, Integer> newPrevPairIndexEntry = indices.getOrDefault(newPrevPair, new HashMap<>());
                            newPrevPairIndexEntry.put(change.positionInDictionary,
                                    newPrevPairIndexEntry.getOrDefault(change.positionInDictionary, 0) + 1);
                        }

                        /* If there was at least another symbol after the leftSymbol occurrence,
                        it means that the oldWord is like "B C D" and we are merging "(B, C)" into BC.
                        In this case D is not be preceded by C anymore, but by BC, so:
                            - the frequency of (C, D) in stats must be reduced by the frequency of the old word in sortedDictionary.
                            - in the indices entry for (C, D), the amount of occurrences in oldWord must be reduced by 1.
                            - the frequency of (BC, D) in stats must be increased by the frequency of the old word in sortedDictionary.
                            - in the indices entry for (BC, D), the amount of occurrences in oldWord must be increased by 1.

                        However, SKIP the reducing and increasing for the old and new nextpair
                        if the sequence is like A B C B C, because the frequency of "C B" will be reduced by the previous code block*/
                        if (i + 2 < change.oldWord.length) {

                            boolean skip = (i + 3 < change.oldWord.length) && (change.oldWord[i + 3].equals(pair.leftSymbol));

                            if (!skip) {
                                Symbol.Pair oldNextPair = new Symbol.Pair(change.oldWord[i + 1], change.oldWord[i + 2]);
                                Symbol.Pair newNextPair = new Symbol.Pair(mergedSymbol, change.oldWord[i + 2]);

                                stats.put(oldNextPair, stats.get(oldNextPair) - change.frequencyInDictionary);
                                Map<Integer, Integer> oldNextPairIndexEntry = indices.getOrDefault(oldNextPair, new HashMap<>());
                                oldNextPairIndexEntry.put(change.positionInDictionary,
                                        oldNextPairIndexEntry.getOrDefault(change.positionInDictionary, 0) - 1);

                                stats.put(newNextPair, stats.getOrDefault(newNextPair, 0) + change.frequencyInDictionary);
                                Map<Integer, Integer> newNextPairIndexEntry = indices.getOrDefault(newNextPair, new HashMap<>());
                                newNextPairIndexEntry.put(change.positionInDictionary,
                                        newNextPairIndexEntry.getOrDefault(change.positionInDictionary, 0) + 1);
                            }
                        }

                        /*since the i+1 symbol was merged with the i symbol, in next iteration go to the following one */
                        i += 1;
                    }
                }
            }
        }
    }


    // --------------------- APPLICATION METHODS ---------------------

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
        Symbol[] word = splitIntoSymbols(wordString, this.separator, "");

        Set<Symbol.Pair> symbolPairs = getPairs(word);

        /* if the initial string is empty or contains one char only, it can not be split
           so the BPE encoding is the string itself (in an array) */
        if (symbolPairs.isEmpty())
            return new String[]{wordString};

        while (true) {
            /* get the SymbolPair with minimum priority in the BPE rules */
            Symbol.Pair bigram = Collections.min(symbolPairs, Comparator.comparing(this::getPriorityFor));

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

        /*split again if some symbols have been merged too much */
        if (vocabulary != null)
            word = this.splitUsingVocabulary(word, vocabulary);

        /* return the processed word as an array of strings instead of symbols.
         * On creating the string for the last symbol ignore its END OF WORD (and check that it is not empty). */
        String[] result = new String[word.length];
        for (int i = 0; i < word.length - 1; i++)
            result[i] = word[i].getFullContent();
        String lastString = word[word.length - 1].getContentWithout(END_OF_WORD);
        if (!lastString.isEmpty())
            result[word.length - 1] = lastString;

        /* put the computed result in cache */
        this.cache.put(wordString, result);

        return result;
    }

    /**
     * This private method checks for each symbol in word if it is in-vocabulary.
     * If it is, it is not changed.
     * Otherwise, it means that the previous merging step has generated an out-of-vocabulary subword,
     * so it must be split again until the generated subwords belong to the vocabulary.
     *
     * @param word       the word the symbols of which must be checked
     * @param vocabulary the vocabulary to use to check symbols
     * @return the resulting array of Symbols
     */
    private Symbol[] splitUsingVocabulary(Symbol[] word, Set<String> vocabulary) {
        ArrayList<Symbol> result = new ArrayList<>();

        for (Symbol symbol : word)
            Collections.addAll(result, this.recursiveSplit(symbol, vocabulary));

        return result.toArray(new Symbol[result.size()]);
    }

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

        /*BASE CASE 1: the symbol is in vocabulary: if so, return an array containing just the symbol itself*/
        if (this.vocabularyContains(vocabulary, symbol))
            return new Symbol[]{symbol};

        /*if the symbol is not in vocabulary, find a rule to split it
        * BASE CASE 2: if no rule was found, it means that the symbol cannot be split further, so return it*/
        Rule rule = this.getReverseRuleFor(symbol);
        if (rule == null)
            return new Symbol[]{symbol};

        /* RECURSIVE CASE: if a rule was found, use it to split the symbol and recursively call this method to the children */
        Symbol.Pair split = this.splitByRule(symbol, rule);
        Collections.addAll(result, recursiveSplit(split.leftSymbol, vocabulary));
        Collections.addAll(result, recursiveSplit(split.rightSymbol, vocabulary));

        return result.toArray(new Symbol[result.size()]);
    }

    /* ------------- UTILITY METHODS -------------- */

    /**
     * This private utility method transforms a string into an array of symbols
     * splitting the string in correspondence of stringSeparator.
     * Each symbol in array is given a symbolSeparator as a suffix, except for the last one, that is given END_OF_WORD.
     * It is the inverse method of mergeIntoString,
     *
     * @param string          the string to split into symbols
     * @param symbolSeparator the separator that symbols use in the BPE model
     * @param stringSeparator the separator in the string in base of which to split the string into symbols
     * @return the array of obtained Symbols
     */
    private static Symbol[] splitIntoSymbols(String string, String symbolSeparator, String stringSeparator) {
        String[] splittedString = string.split(stringSeparator);

        Symbol[] symbols = new Symbol[splittedString.length];

        int position = 0;

        /*for all substring obtained except the last one, create a new Symbol with appended content separator;
        * for the last char of the string, create a new Symbol with appended content END OF WORD*/
        for (int i = 0; i < splittedString.length - 1; i++) {
            String subString = splittedString[i];
            symbols[position] = new Symbol(string, position, position + subString.length(), symbolSeparator);
            position += splittedString.length + stringSeparator.length();
        }

        if (symbols.length > 0)
            symbols[symbols.length - 1] = new Symbol(string, position, string.length(), END_OF_WORD);

        return symbols;
    }

    /**
     * This private utility method merges a list of symbols into a unique string.
     * For the passed symbols, the separator is ignored whereas the END_OF_WORD is kept.
     * Consecutive symbols are merged with a stringSeparator between them.
     * It is the inverse method of splitIntoSymbols, and is typically used during training to update the stats data structures.
     *
     * @param symbols         an array containing the symbols to merge
     * @param symbolSeparator the separator that symbols use in the BPE model
     * @param stringSeparator the separator to use between consecutive symbols in the result string
     * @return the string obtained merging the passed symbols.
     */
    private static String mergeIntoString(Symbol[] symbols, String symbolSeparator, String stringSeparator) {
        StringBuilder builder = new StringBuilder();
        for (Symbol symbol : symbols)
            builder.append(symbol.getContentWithout(symbolSeparator)).append(stringSeparator);
        return builder.toString();
    }


    /**
     * This private utility method gets all pairs of consecutive symbols in a symbol array.
     * It returns a set containing all the found pairs
     * (which means that returned pairs are not ordered and duplicates are skipped).
     *
     * @return the set containing the pairs of consecutive symbols obtained from the initial symbol array
     */
    private static Set<Symbol.Pair> getPairs(Symbol[] symbols) {
        Set<Symbol.Pair> pairs = new HashSet<>();

        for (int i = 0; i < symbols.length - 1; i++)
            pairs.add(new Symbol.Pair(symbols[i], symbols[i + 1]));

        return pairs;
    }


    /**
     * This private utility method splits a Symbol in a SymbolPair using a Rule.
     * This is typically during the recursive split using vocabulary.
     * <p>
     * It must be already verified that the symbol matches the rule.
     *
     * @return a SymbolPair obtained by splitting the passed Symbol using the passed BPE Rule
     */
    private Symbol.Pair splitByRule(Symbol symbol, Rule rule) {

        /* the appended content for the last symbol  */
        String leftSubword = rule.leftSubword;
        String rightSubword = rule.rightSubword;

        String rightAppendedContent = symbol.suffix;

        /* if the right subword from the rule ends with the end of word, then
        *   - it must not be considered as part of the string for the right subsymbol
        *   - but it must be considered as part of the appended content for the right subsymbol
        * because in BPE the separator only appears in vocabulary, and END OF WORD only appears in rules*/
        if (rightSubword.endsWith(END_OF_WORD)) {
            rightSubword = StringUtils.chomp(rightSubword, END_OF_WORD);
            rightAppendedContent = END_OF_WORD;
        }

        Symbol left = new Symbol(
                symbol.original,
                symbol.startIndex,
                symbol.startIndex + leftSubword.length(),
                separator);     // the left symbol will always have a separator because it is created from splitting

        Symbol right = new Symbol(
                symbol.original,
                symbol.startIndex + leftSubword.length(),
                symbol.startIndex + leftSubword.length() + rightSubword.length(),
                rightAppendedContent);

        return new Symbol.Pair(left, right);
    }


    /* ---------------------- ACCESS METHODS ---------------------------
    * These methods are used to access the BPE data structures (rule maps, vocabularies, ecc).
    * One should always use these methods instead of accessing the structures directly
    * because they take into account the fact how symbols and symbol pairs are contained in the data structures.
    * More in detail:
    *   - END_OF_WORD is only used in rules, but not in vocabularies.
    *   - the separator is only used in vocabularies, but not in rules
    * */

    /**
     * This method checks whether this BPE model has a Rule matching a specific SymbolPair or not.
     *
     * @return true if there is a Rule matching the SymbolPair; false if there is not.
     */
    public boolean hasRuleFor(Symbol.Pair bigram) {
        return this.rule2priority.containsKey(new Rule(
                bigram.leftSymbol.getContentWithout(separator),
                bigram.rightSymbol.getContentWithout(separator)));
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
        return this.string2rule.containsKey(symbol.getContentWithout(separator));
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
        return this.string2rule.get(symbol.getContentWithout(separator));
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
        return vocabulary.contains(symbol.getContentWithout(END_OF_WORD));
    }

    /**
     * This method gets the priority of a pair of symbols in this BPE model.
     *
     * @return the set containing the pairs of consecutive symbols obtained from the initial symbol list
     */
    public Integer getPriorityFor(Symbol.Pair pair) {
        return this.getPriorityFor(new Rule(pair.leftSymbol.getContentWithout(separator), pair.rightSymbol.getContentWithout(separator)));
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


    public static void main(String[] args) {
        HashMap<Integer, Integer> m1 = new HashMap<>();
        m1.put(1, 1);
        HashMap<Integer, Integer> m2 = new HashMap<>();
        m2.putAll(m1);
        m2.put(1, 2);
        System.out.println(m1.get(1));
    }

}