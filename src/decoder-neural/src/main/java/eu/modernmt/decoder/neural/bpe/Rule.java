package eu.modernmt.decoder.neural.bpe;

/**
 * A BPE.Rule models a couple of consecutive Subwords (aka BPE termps) included in this BPE model.
 * It thus represents how a string should be splitted in two terms to make it compliant to this BPE model.
 * <p>
 * Since a rule is immutable, it is implemented using Strings.
 */
public class Rule {

    public final String leftSubword;
    public final String rightSubword;

    public Rule(String leftSubword, String rightSubword) {
        this.leftSubword = leftSubword;
        this.rightSubword = rightSubword;
    }

    public Rule(Symbol.Pair pair, String separator) {
        this.leftSubword = pair.leftSymbol.getContentWithout(separator);
        this.rightSubword = pair.rightSymbol.getContentWithout(separator);
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
