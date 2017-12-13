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
    private int startIndex;     //the position of the first char of the original String that this Symbol contains
    private int endIndex;       //the position of the first char of the original String that this Symbol does not contain
    private String original;    //a reference to the original string that this Symbol refers to
    private String appendedContent; //content that may be appended to this Symbol (e.g. separator, END_OF_WORD).

    public Symbol(String original, int start, int end, String appendedContent) {
        this.original = original;
        this.startIndex = start;
        this.endIndex = end;
        this.appendedContent = appendedContent;
    }

    public Symbol(String original, int start, int end) {
        this(original, start, end, "");
    }


    public int length() {
        return endIndex - startIndex;
    }

    public char charAt(int i) {
        return original.charAt(i + startIndex);
    }

    public void append(String append) {
        this.appendedContent += append;
    }


    public int getStartIndex() {
        return startIndex;
    }

    public void setStartIndex(int startIndex) {
        this.startIndex = startIndex;
    }

    public int getEndIndex() {
        return endIndex;
    }

    public void setEndIndex(int endIndex) {
        this.endIndex = endIndex;
    }

    public String getOriginal() {
        return original;
    }

    public void setOriginal(String original) {
        this.original = original;
    }

    public String getAppendedContent() {
        return appendedContent;
    }

    public void setAppendedContent(String appendedContent) {
        this.appendedContent = appendedContent;
    }

    @Override
    public boolean equals(Object that) {
        return (that instanceof Symbol && this.toString().equals(that.toString()));
    }

    @Override
    public int hashCode() {
        int result = startIndex;
        result = 31 * result + endIndex;
        result = 31 * result + (original != null ? original.hashCode() : 0);
        result = 31 * result + (appendedContent != null ? appendedContent.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return (original.substring(this.startIndex, this.endIndex) + this.appendedContent);
    }

    /**
     * Merge two consecutive symbols into a new one.
     * The returned symbol is a new symbol with the start of the leftmost symbol
     * and the end and appended content of the rightmost one.
     * (since this method is only used when the leftmost symbol has no appended content).
     */
    public static Symbol merge(Symbol prev, Symbol next) {
        return new Symbol(prev.original, prev.startIndex, next.endIndex, next.appendedContent);
    }

}
