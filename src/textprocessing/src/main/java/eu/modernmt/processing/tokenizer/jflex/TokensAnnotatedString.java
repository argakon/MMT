package eu.modernmt.processing.tokenizer.jflex;

import eu.modernmt.processing.string.SentenceBuilder;

import java.io.CharArrayReader;
import java.io.Reader;

/**
 * Created by davide on 01/02/16.
 */
public class TokensAnnotatedString {

    public static final byte SPLIT_FLAG = (byte) (1);
    public static final byte PROTECTED_FLAG = (byte) (1 << 1);

    private static final int WHITESPACE = 1;
    private static final int BREAK = 3;

    private char[] chars;
    private byte[] flags;
    private int length;

    private static boolean isWhitespace(char c) {
        return ((0x0009 <= c && c <= 0x000D) || c == 0x0020 || c == 0x00A0 || c == 0x1680 ||
                (0x2000 <= c && c <= 0x200A) || c == 0x202F || c == 0x205F || c == 0x3000);
    }

    private static boolean isCJK(char c) {
        return (0x4E00 <= c && c <= 0x62FF) || (0x6300 <= c && c <= 0x77FF) ||
                (0x7800 <= c && c <= 0x8CFF) || (0x8D00 <= c && c <= 0x9FFF);
    }

    public TokensAnnotatedString(String string, boolean splitCJK) {
        this(string.toCharArray(), splitCJK);
    }

    public TokensAnnotatedString(char[] source, boolean splitCJK) {
        this.chars = new char[source.length + 2];
        this.flags = new byte[source.length + 3];

        boolean start = true;
        boolean whitespace = false;

        int index = 1;

        for (char c : source) {
            int type = 0;

            if (isWhitespace(c)) {
                type = WHITESPACE;
            } else if ((splitCJK && isCJK(c)) || (c != '-' && !Character.isLetterOrDigit(c))) {
                type = BREAK;
            }

            switch (type) {
                case WHITESPACE:
                    whitespace = true;
                    break;
                default:
                    if (start) {
                        start = false;
                        whitespace = false;
                    } else if (whitespace) {
                        this.chars[index] = ' ';
                        this.flags[index] = this.flags[index + 1] = SPLIT_FLAG;

                        whitespace = false;
                        index++;
                    }

                    this.chars[index] = c;
                    if (type == BREAK) {
                        this.flags[index] = this.flags[index + 1] = SPLIT_FLAG;
                    }

                    index++;
                    break;
            }
        }

        this.length = index + 1;
        this.chars[0] = this.chars[this.length - 1] = ' ';
        this.flags[this.length] = SPLIT_FLAG;
    }

    public void protect(int start, int end) {
        for (int i = start; i < end; i++) {
            this.flags[i] |= PROTECTED_FLAG;
        }
    }

    public void protect(int index) {
        this.protect(index, index + 1);
    }

    public Reader getReader() {
        return new CharArrayReader(chars, 0, length);
    }

    public SentenceBuilder compile(SentenceBuilder builder) {
        SentenceBuilder.Editor editor = builder.edit();

        int tokenStart = 0;
        int tokenEnd = 0;
        boolean foundNonWhitespace = false;

        for (int i = 0; i < length + 1; i++) {
            byte flag = this.flags[i];

            boolean protect = (flag & TokensAnnotatedString.PROTECTED_FLAG) > 0;
            boolean split = (flag & TokensAnnotatedString.SPLIT_FLAG) > 0;

            if (!protect && split) {
                int tokenLength = 1 + tokenEnd - tokenStart;

                if (tokenLength > 0) {
                    editor.setWord(tokenStart - 1, tokenLength, null);
                    tokenStart = tokenEnd = i;
                    foundNonWhitespace = false;
                }
            }

            if (i < chars.length) {
                if (chars[i] == ' ') {
                    if (!foundNonWhitespace)
                        tokenStart++;
                } else {
                    foundNonWhitespace = true;
                    tokenEnd = i;
                }
            }
        }

        return editor.commit();
    }

    @Override
    public String toString() {
        return new String(chars, 0, length);
    }

}
