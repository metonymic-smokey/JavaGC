
package at.jku.anttracks.features.io;

import java.io.IOException;
import java.io.InputStream;

public class FeaturesScanner implements AutoCloseable {

    private final InputStream in;

    private char la;
    private int row, col;

    public FeaturesScanner(InputStream in) throws IOException {
        this.in = in;
        la = '\0';
        row = 1;
        col = 0;
        advance();
    }

    public FeaturesToken read() throws IOException {
        skipWhitespaces();
        switch (la) {
            case '\0':
                return new FeaturesToken(FeaturesToken.Kind.EOF, "", row, col);
            case '#':
                while (la != '\n') {
                    advance();
                }
                return read();
            case '{':
                advance();
                return new FeaturesToken(FeaturesToken.Kind.LBRACE, "{", row, col);
            case '}':
                advance();
                return new FeaturesToken(FeaturesToken.Kind.RBRACE, "}", row, col);
            case '(':
                advance();
                return new FeaturesToken(FeaturesToken.Kind.LPARANTESIS, "(", row, col);
            case ')':
                advance();
                return new FeaturesToken(FeaturesToken.Kind.RPARANTESIS, ")", row, col);
            case '[':
                advance();
                return new FeaturesToken(FeaturesToken.Kind.LBRACKET, "[", row, col);
            case ']':
                advance();
                return new FeaturesToken(FeaturesToken.Kind.RBRACKET, "]", row, col);
            case ':':
                advance();
                if (la == ':') {
                    advance();
                    return new FeaturesToken(FeaturesToken.Kind.DOUBLE_COLON, "::", row, col);
                } else {
                    throw new IOException("':' must be followed by another ':");
                }
            case '*':
                advance();
                return new FeaturesToken(FeaturesToken.Kind.STAR, "*", row, col);
            default:
                if (Character.isDigit(la)) {
                    StringBuilder ident = new StringBuilder();
                    while (Character.isDigit(la)) {
                        ident.append(la);
                        advance();
                    }
                    return new FeaturesToken(FeaturesToken.Kind.NUMBER, ident.toString(), row, col);
                } else if (Character.isLetter(la)) {
                    StringBuilder ident = new StringBuilder();
                    while (!Character.isWhitespace(la) && la != '*' && la != '(' && la != ')' && la != ':') { // be very gracious here
                        ident.append(la);
                        advance();
                    }
                    return new FeaturesToken(FeaturesToken.Kind.IDENTIFIER, ident.toString(), row, col);
                } else {
                    throw new IOException("Illegal Character: " + la);
                }
        }
    }

    private void skipWhitespaces() throws IOException {
        while (Character.isWhitespace(la)) {
            advance();
        }
    }

    private char advance() throws IOException {
        int c = in.read();
        la = c > 0 ? (char) c : '\0';
        if (la == '\n') {
            row++;
            col = 0;
        }
        col++;
        return la;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

}
