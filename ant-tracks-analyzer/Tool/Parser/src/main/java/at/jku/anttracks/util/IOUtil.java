package at.jku.anttracks.util;

import java.io.InputStream;
import java.util.Scanner;

public class IOUtil {
    public static String readInputStreamIntoString(InputStream is) {
        try (Scanner scanner = new Scanner(is)) {
            scanner.useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        }
    }
}
