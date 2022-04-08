
package at.jku.anttracks.features.io;

import at.jku.anttracks.features.Feature;
import at.jku.anttracks.features.FeatureMap;

import java.io.IOException;
import java.io.InputStream;

public class FeaturesParser implements AutoCloseable {

    private final FeaturesScanner scanner;

    private FeaturesToken la;
    private FeaturesToken cur;

    public FeaturesParser(InputStream in) throws IOException {
        scanner = new FeaturesScanner(in);
        scan();
    }

    private void scan() throws IOException {
        cur = la;
        la = scanner.read();
    }

    private void check(FeaturesToken.Kind expected) throws IOException {
        if (la.kind != expected) {
            throw new IOException("Expected " + expected + " but is " + la);
        }
        scan();
    }

    public FeatureMap parse(FeatureMap map) throws IOException {
        parseFile(map);
        check(FeaturesToken.Kind.EOF);
        return map;
    }

    private void parseFile(FeatureMap map) throws IOException {
        while (la.kind == FeaturesToken.Kind.IDENTIFIER) {
            Feature feature = parseFeature();
            map.add(feature);
        }
    }

    private Feature parseFeature() throws IOException {
        check(FeaturesToken.Kind.IDENTIFIER);
        String name = cur.value;
        int r, g, b;
        if (la.kind == FeaturesToken.Kind.LPARANTESIS) {
            scan();
            r = parseNumber();
            g = parseNumber();
            b = parseNumber();
            check(FeaturesToken.Kind.RPARANTESIS);
        } else {
            r = g = b = 0;
        }
        if (r < 0 || g < 0 || b < 0 || r > 255 | g > 255 || b > 255) {
            throw new IOException("Invalid rgb (" + r + "," + g + "," + b + ")");
        }
        Feature feature = new Feature(name, r, g, b);
        check(FeaturesToken.Kind.LBRACE);
        while (la.kind == FeaturesToken.Kind.IDENTIFIER) {
            parseMapping(feature);
        }
        check(FeaturesToken.Kind.RBRACE);
        return feature;
    }

    // ident ( | "*" |  "::" ident "(" [ ident ] ")" [ "[" number number "]" ]
    private void parseMapping(Feature feature) throws IOException {
        check(FeaturesToken.Kind.IDENTIFIER);
        String type = cur.value;
        if (la.kind == FeaturesToken.Kind.STAR) {
            scan();
            feature.addPackage(type);
        } else if (la.kind == FeaturesToken.Kind.DOUBLE_COLON) {
            scan();
            check(FeaturesToken.Kind.IDENTIFIER);
            String method = cur.value;
            check(FeaturesToken.Kind.LPARANTESIS);
            String signature;
            if (la.kind == FeaturesToken.Kind.IDENTIFIER) {
                scan();
                signature = cur.value;
            } else {
                signature = "";
            }
            check(FeaturesToken.Kind.RPARANTESIS);
            if (la.kind == FeaturesToken.Kind.LBRACKET) {
                scan();
                int from = parseNumber();
                int to = parseNumber();
                if (to < 0 || from < 0 || from > to) {
                    throw new IOException("To and from are inconsistent (" + to + ", " + from + ")");
                }
                check(FeaturesToken.Kind.RBRACKET);
                feature.addInstructions(type, method, signature, from, to);
            } else {
                feature.addMethod(type, method, signature);
            }
        } else {
            feature.addType(type);
        }
    }

    private int parseNumber() throws IOException {
        check(FeaturesToken.Kind.NUMBER);
        return Integer.parseInt(cur.value);
    }

    public void close() throws IOException {
        scanner.close();
    }

}
