
package at.jku.anttracks.gui.utils;

import java.io.*;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Stream;

public class FileUtil {

    private static File TMP_DIR;
    private static String TMP_PREFIX = "anttracks";
    private static String TMP_POSTFIX = ".tmp";

    public static boolean deleteTree(File file) {
        boolean ok;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                ok = Arrays.stream(children).map(FileUtil::deleteTree).allMatch(k -> k) && file.delete();
            } else {
                ok = false;
            }
        } else {
            ok = file.delete();
        }
        if (!ok) {
            System.err.println("Not all files could be deleted");
        }
        return ok;
    }

    public static File createTemporaryDirectory() throws IOException {
        File dir = createTemporaryFile();
        if (!dir.delete()) {
            throw new IOException();
        }
        if (!dir.mkdirs()) {
            throw new IOException();
        }
        return dir;
    }

    public static File createTemporaryFile() throws IOException {
        return File.createTempFile(TMP_PREFIX, TMP_POSTFIX, TMP_DIR).getAbsoluteFile();
    }

    public static File relativize(File file, File base) {
        return new File(base.toURI().relativize(file.toURI()).getPath());
    }

    public static String readSilently(File file) {
        try {
            return read(file);
        } catch (IOException e) {
            return null;
        }
    }

    public static String read(File file) throws IOException {
        try (BufferedReader in = new BufferedReader(new FileReader(file))) {
            StringBuilder string = new StringBuilder();
            for (String line = in.readLine(); line != null; line = in.readLine()) {
                string.append(line);
                string.append('\n');
            }
            return string.toString().substring(0, string.length() - (string.length() > 0 ? 1 : 0));
        } catch (FileNotFoundException fnfe) {
            return null;
        }
    }

    public static void write(File file, String content) throws IOException {
        try (Writer out = new FileWriter(file)) {
            out.write(content);
        }
    }

    public static <T> T readPropertyValueSilently(File file, String key, boolean trim, Function<String, T> parser, T backup) {
        try {
            return readPropertyValue(file, key, trim, parser, backup);
        } catch (IOException e) {
            return backup;
        }
    }

    public static <T> T readPropertyValue(File haystack, String needle, boolean trim, Function<String, T> parser, T backup) throws IOException {
        try (BufferedReader in = new BufferedReader(new FileReader(haystack))) {
            for (String line = in.readLine(); line != null; line = in.readLine()) {
                if (trim) {
                    line = line.trim();
                }
                if (line.length() > 0 && !line.startsWith("%") && line.contains("=")) {
                    int splitter = line.indexOf("=");
                    String straw = line.substring(0, splitter);
                    if (trim) {
                        straw = straw.trim();
                    }
                    if (straw.equals(needle)) {
                        String value = line.substring(splitter + 1);
                        if (trim) {
                            value = value.trim();
                        }
                        return parser.apply(value);
                    }
                }
            }
        }
        return backup;
    }

    private FileUtil() {
        throw new Error();
    }

    public static boolean deleteFilesInTree(File file, String[] keepFileEndings) {
        try {
            deleteFilesInTree0(file, keepFileEndings);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static void deleteFilesInTree0(File file, String[] keepFileEndings) throws RuntimeException {
        try {
            if (file.isDirectory()) {
                Files.newDirectoryStream(file.toPath()).forEach(x -> deleteFilesInTree0(x.toFile(), keepFileEndings));
                if (file.listFiles().length == 0) {
                    if (!file.delete()) {
                        throw new IOException(file.toString());
                    }
                }
            } else {
                if (!Stream.of(keepFileEndings).anyMatch(ending -> file.getAbsolutePath().endsWith(ending))) {
                    if (!file.delete()) {
                        throw new IOException(file.toString());
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
