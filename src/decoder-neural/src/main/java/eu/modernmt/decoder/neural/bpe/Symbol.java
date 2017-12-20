package eu.modernmt.decoder.neural.bpe;

/**
 * Created by andrea on 13/12/17.
 * <p>
 * A Symbol represents a component of the original passed String to encode.
 * <p>
 * A Symbol is a text that may or may not correspond to a BPE Subword.
 * Symbols are typically used by a BPE model while progressively splitting and re-merging a string to encode.
 */
public class Symbol {

    /*A Symbol is implemented as a couple of indexes on the original String that the Symbol is part of.*/
    public final int startIndex;     //the position of the first char of the original String that this Symbol contains
    public final int endIndex;       //the position of the first char of the original String that this Symbol does not contain
    public final String original;    //a reference to the original string that this Symbol refers to
    public final String suffix;   //content that may be appended to this Symbol (e.g. separator, END_OF_WORD).

    /* a symbol should store its string forms at the beginning
    instead of recomputing them every time using original, start, end and suffix*/
    private final String innerContent;  // a string with the content of the symbol, without its suffix
    private final String fullContent;   // a string with the content of the symbol, including its suffix


    public Symbol(String original, int start, int end, String suffix) {
        this.original = original;
        this.startIndex = start;
        this.endIndex = end;
        this.suffix = suffix;

        this.innerContent = original.substring(startIndex, endIndex);
        this.fullContent = innerContent + suffix;

    }

    // public Symbol(String original, int start, int end) { this(original,start,end,"");}


    public int length() {
        return endIndex - startIndex;
    }

    @Override
    public boolean equals(Object that) {
        return (that instanceof Symbol && this.getFullContent().equals(((Symbol) that).getFullContent()));
    }

    @Override
    public int hashCode() {
        int result = startIndex;
        result = 31 * result + endIndex;
        result = 31 * result + (original != null ? original.hashCode() : 0);
        result = 31 * result + (suffix != null ? suffix.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return fullContent;
    }

    /**
     * This method returns the symbol content including its suffix as a String.
     *
     * @return the symbol content including its suffix as a String
     */
    public String getFullContent() {
        return fullContent;
    }

    /**
     * This method returns the symbol content ignoring its suffix as a String.
     *
     * @return the symbol content ignoring its suffix as a String
     */
    public String getInnerContent() {
        return innerContent;
    }

    /**
     * This method checks whether the symbol suffix matches a specific string.
     * If it does, it returns the Symbol inner content, this without the suffix; otherwise it returns symbol full content.
     *
     * @return the symbol content without the passed suffix
     */
    public String getContentWithout(String suffix) {
        return this.suffix.equals(suffix) ? innerContent : fullContent;
    }


    /**
     * Check if this Symbol has the BPE END OF WORD as a suffix;
     * if it has, it means that it is the last symbol in its word
     *
     * @return true if this symbol suffix is END OF WORD; false otherwise
     */
    public boolean isFinal() {
        return this.suffix.equals(BPE.END_OF_WORD);
    }

    /**
     * Merge two consecutive symbols into a new one.
     * The returned symbol is a new symbol with the start of the leftmost symbol
     * and the end and appended content of the rightmost one.
     * (since this method is only used when the leftmost symbol has no appended content).
     */
    public static Symbol merge(Symbol prev, Symbol next) {
        return new Symbol(prev.original, prev.startIndex, next.endIndex, next.suffix);
    }

}
