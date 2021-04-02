package at.jku.anttracks.util;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;

public class DirectoryClassLoader extends URLClassLoader {

    public DirectoryClassLoader(String directory) {
        super(createURLs(new File(directory)));
    }

    private static URL[] createURLs(File file) {
        file.mkdirs();
        return Arrays.stream(file.listFiles()).filter(f -> f.getName().endsWith(".jar")).map(f -> {
            try {
                return f.toURI().toURL();
            } catch (Exception e) {
                assert false;
                throw new RuntimeException(e);
            }
        }).toArray(URL[]::new);
    }
}
