
package at.jku.anttracks.features.io;

public class FeaturesToken {

    public static enum Kind {
        IDENTIFIER,
        NUMBER,
        LBRACE,
        RBRACE,
        LPARANTESIS,
        RPARANTESIS,
        LBRACKET,
        RBRACKET,
        DOUBLE_COLON,
        STAR,
        EOF
    }

    public final Kind kind;
    public final String value;
    public final int row, col;

    public FeaturesToken(Kind kind, String value, int row, int col) {
        this.kind = kind;
        this.value = value;
        this.row = row;
        this.col = col;
    }

    public String toString() {
        return kind + " \"" + value + "\" @ (" + row + "," + col + ")";
    }

}
