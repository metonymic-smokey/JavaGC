
package at.jku.anttracks.util;

import java.io.*;
import java.util.*;

public class FileUtil {

    private static final Object LOCK = new Object();
    private static final Set<String> DELETE_ON_EXIT = new HashSet<>(); // File API doesn't work for directories ...
    private static final Map<String, String> LINKS = new HashMap<>();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> DELETE_ON_EXIT.stream().forEach(f -> deleteRecursively(new File(f))), "On-exit-deleter"));
    }

    public static File getLinkAwareFile(String path) {
        path = new File(path).getAbsolutePath();
        synchronized (LOCK) {
            String linkedSrc = null;
            for (String candidate : LINKS.keySet()) {
                if (path.startsWith(candidate) && (linkedSrc == null || linkedSrc.length() > candidate.length())) {
                    linkedSrc = candidate;
                }
            }
            if (linkedSrc == null) {
                return new File(path);
            } else {
                String linkTarget = LINKS.get(linkedSrc);
                path = path.replace(path.substring(0, linkedSrc.length()), linkTarget);
                return new File(path);
            }
        }
    }

    public static void createLink(String src, String target) throws IOException {
        src = new File(src).getAbsolutePath();
        target = new File(target).getAbsolutePath();
        synchronized (LOCK) {
            if (LINKS.containsKey(src)) {
                throw new IOException("Duplicate link!");
            } else {
                LINKS.put(src, target);
            }
        }
    }

    public static void deleteLink(String src, String target) throws IOException {
        src = new File(src).getAbsolutePath();
        target = new File(target).getAbsolutePath();
        synchronized (LOCK) {
            if (!LINKS.containsKey(src)) {
                throw new IOException("No such link!");
            } else {
                String oldTarget = LINKS.remove(src);
                if (!oldTarget.equals(target)) {
                    throw new IOException("Unexpected link target!");
                }
            }
        }
    }

    public static void move(File src, File dest) throws IOException {
        boolean ok = true;
        File tmp = null;

        try {
            if (!dest.getParentFile().exists()) {
                ok = dest.getParentFile().mkdirs();
                if (!ok) {
                    throw new IOException("Could not create parent directories of " + dest + "!");
                }
            }

            if (dest.exists()) {
                tmp = FileUtil.createTempFile(dest.getParentFile(), dest.getName(), dest.isDirectory());
                tmp.delete();
                ok = dest.renameTo(tmp);
                if (!ok) {
                    tmp.delete();
                    throw new IOException("Could not backup " + dest + " to " + tmp + "! (fail early)");
                }
            }

            ok = src.renameTo(dest);
            if (!ok) {
                throw new IOException("Could not rename " + src + " to " + dest + "!");
            }

            synchronized (LOCK) {
                String target = LINKS.get(dest.getAbsolutePath());
                if (target != null && target.equals(src.getAbsolutePath())) {
                    LINKS.remove(dest.getAbsolutePath());
                }
            }
        } finally {
            if (tmp != null) {
                if (!ok) {
                    tmp.renameTo(dest);
                }
                deleteRecursively(tmp);
            }
        }
        // FileSystem fs = FileSystems.getDefault();
        // Path srcPath = fs.getPath(src.getAbsolutePath());
        // Path destPath = fs.getPath(dest.getAbsolutePath());
        // Files.move(srcPath, destPath, StandardCopyOption.REPLACE_EXISTING);
    }

    public static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    public static File createTempFile(File location, String extension, boolean isDirectory) throws IOException {
        assert location.isDirectory();
        File tmp = File.createTempFile("." + FileUtil.class.getName() + ".", "." + extension, location);
        if (isDirectory) {
            tmp.delete();
            tmp.mkdir();
            synchronized (LOCK) {
                DELETE_ON_EXIT.add(tmp.getAbsolutePath());
            }
        } else {
            tmp.deleteOnExit();
        }
        return tmp;
    }

    public static List<File> getAllChildren(File container) {
        return Arrays.stream(container.listFiles())
                     .map(f -> f.isDirectory() ? f.listFiles() : new File[]{f})
                     .collect(() -> new ArrayList<File>(), (list, files) -> list.addAll(Arrays.asList(files)), (list1, list2) -> list1.addAll(list2));
    }

    public static FileInputStream openR(File file) throws FileNotFoundException {
        return new FileInputStream(getLinkAwareFile(file.getAbsolutePath()));
    }

    public static FileOutputStream openW(File file) throws FileNotFoundException {
        file.getParentFile().mkdirs();
        return new FileOutputStream(getLinkAwareFile(file.getAbsolutePath()));
    }

    public static byte[] readAllBytes(InputStream in) throws IOException {
        try {
            byte[] buffer = new byte[1024];
            int top = 0;
            for (int read = in.read(buffer, 0, buffer.length); read > 0; read = in.read(buffer, top, buffer.length - top)) {
                top += read;
                if (buffer.length - top == 0) {
                    byte[] newbuffer = new byte[buffer.length * 2];
                    System.arraycopy(buffer, 0, newbuffer, 0, buffer.length);
                    buffer = newbuffer;
                }
            }
            if (top < buffer.length) {
                byte[] trimmed = new byte[top];
                System.arraycopy(buffer, 0, trimmed, 0, top);
                buffer = trimmed;
            }
            return buffer;
        } finally {
            in.close();
        }
    }

    private FileUtil() {
        throw new Error("Do not instantiate!");
    }
}
