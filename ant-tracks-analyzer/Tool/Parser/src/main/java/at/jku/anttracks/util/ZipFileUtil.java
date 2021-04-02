
package at.jku.anttracks.util;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ZipFileUtil {

    private static final Object LOCK = new Object();
    private static final Map<String, ZipInfo> INFOS = new HashMap<>();

    public static ZipFile getFile(File file) throws IOException {
        try {
            return new ZipFile(file);
        } catch (ZipException ze) {
            Logger logger = Logger.getLogger("Repair Zip " + file);
            logger.log(Level.INFO, "opening zip " + file + " failed, fixing ...");
            File tmp = FileUtil.createTempFile(file.getParentFile(), file.getName(), true);
            try {
                tmp.mkdirs();
                int exit = Runtime.getRuntime().exec(new String[]{"unzip", file.getAbsolutePath(), "-d", tmp.getAbsolutePath()}).waitFor();
                if (exit != 0) {
                    logger.log(Level.SEVERE, "OS unzip failed, rethrow original exception");
                    throw new IOException(ze);
                }
                logger.log(Level.INFO, "OS unzip succeeded, finished unzipping");
                FileUtil.move(tmp, file);
                zip(tmp, file, logger);
                FileUtil.move(tmp, file);
                return getFile(file);
            } catch (IOException ioe) {
                throw new IOException(ze);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException();
            } finally {
                FileUtil.deleteRecursively(tmp);
            }
        }
    }

    private static final class ZipInfo {
        public final String path;
        private volatile State state;
        private volatile int accesses, accessesToTmps;
        private final Map<String, File> tmpFiles = new HashMap<>();

        public static enum State {
            ZIPPED,
            UNZIPPED,
            UNZIPPING,
            ZIPPING
        }

        public ZipInfo(File file) {
            path = file.getAbsolutePath();
            state = file.isDirectory() ? State.UNZIPPED : State.ZIPPED;
            accesses = 0;
            accessesToTmps = 0;
        }

        public State getState() {
            synchronized (LOCK) {
                return state;
            }
        }

        public void setState(State state) {
            synchronized (LOCK) {
                this.state = state;
            }
        }

        public boolean isOpen(boolean considerTmps) {
            assert accesses >= 0;
            assert accessesToTmps >= 0;
            return accesses + (considerTmps ? accessesToTmps : 0) > 0;
        }

        public void open() {
            synchronized (LOCK) {
                assert accesses >= 0;
                assert accessesToTmps >= 0;
                accesses++;
                LOCK.notifyAll();
            }
        }

        public void openTmp() {
            synchronized (LOCK) {
                assert accesses >= 0;
                assert accessesToTmps >= 0;
                accessesToTmps++;
                LOCK.notifyAll();
            }
        }

        public void close() throws IOException {
            synchronized (LOCK) {
                assert accesses > 0;
                assert accessesToTmps >= 0;
                accesses--;
                LOCK.notifyAll();
                checkRezip(this);
            }
        }

        public void closeTmp() throws IOException {
            synchronized (LOCK) {
                assert accesses >= 0;
                assert accessesToTmps > 0;
                accessesToTmps--;
                LOCK.notifyAll();
                checkRezip(this);
            }
        }

        public void waitForStableState() throws IOException {
            waitForState(State.ZIPPED, State.UNZIPPED);
        }

        public void waitForState(State... states) throws IOException {
            synchronized (LOCK) {
                while (!hasState(states)) {
                    try {
                        LOCK.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException(e);
                    }
                }
            }
        }

        private boolean hasState(State... states) {
            for (State state : states) {
                if (state == this.state) {
                    return true;
                }
            }
            return false;
        }
    }

    private static ZipInfo getInfo(File file) {
        synchronized (LOCK) {
            ZipInfo info = INFOS.get(file.getAbsolutePath());
            if (info == null) {
                info = new ZipInfo(file);
                INFOS.put(file.getAbsolutePath(), info);
            }
            return info;
        }
    }

    public static boolean isZipFilePath(String path) {
        synchronized (LOCK) {
            for (File file = new File(path); file != null; file = file.getParentFile()) {
                if (isZipFile(file) || wasZipFile(file)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static boolean isZipFile(File file) {
        synchronized (LOCK) {
            return file.isFile() && file.getName().endsWith(".zip");
        }
    }

    public static boolean wasZipFile(File file) {
        synchronized (LOCK) {
            return file.isDirectory() && file.getName().endsWith(".zip");
        }
    }

    public static ZipEntry[] getZipEntries(ZipFile file) {
        List<ZipEntry> result = new ArrayList<ZipEntry>();
        Enumeration<? extends ZipEntry> entries = file.entries();
        while (entries.hasMoreElements()) {
            result.add(entries.nextElement());
        }
        return result.toArray(new ZipEntry[result.size()]);
    }

    private static Logger getNullLogger() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        return logger;
    }

    public static void zip(File dest, File src) throws IOException {
        zip(dest, src, getNullLogger());
    }

    private static void zip(File dest, File src, Logger logger) throws IOException {
        logger.log(Level.INFO, "zipping {0}", src);
        dest.getParentFile().mkdirs();
        List<String> childrenPaths = FileUtil.getAllChildren(src).stream()
                                             .map(c -> c.getAbsolutePath())
                                             .map(p -> p.substring(src.getAbsolutePath().length() + 1))
                                             .collect(Collectors.<String>toList());
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(dest))) {
            out.setLevel(9);
            for (String path : childrenPaths) {
                logger.log(Level.FINE, "zipping {0}", path);
                out.putNextEntry(new ZipEntry(path));
                try (InputStream in = new BufferedInputStream(new FileInputStream(src + File.separator + path))) {
                    StreamUtil.copy(in, out);
                }
                out.closeEntry();
            }
        }
        logger.log(Level.FINE, "zipped {0}", src);
    }

    public static void unzip(File dest, File src) throws IOException, ZipException {
        unzip(dest, src, getNullLogger());
    }

    private static void unzip(File dest, File src, Logger logger) throws IOException, ZipException {
        logger.log(Level.INFO, "unzipping {0}", src);
        try (ZipFile zip = getFile(src)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            List<Thread> threads = new ArrayList<>();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    // nothing to do!
                } else {
                    String path = dest.getAbsolutePath() + File.separator + entry.getName();
                    Thread thread = new Thread(() -> unzipEntry(zip, entry, path, logger), "Unzip Worker " + path);
                    threads.add(thread);
                }
            }
            threads.forEach(t -> t.start());
            threads.forEach(t -> join(t));
            logger.log(Level.INFO, "unzipped {0}", src);
        }
    }

    private static void unzipEntry(ZipFile zip, ZipEntry from, String to, Logger logger) {
        logger.log(Level.FINE, "unzipping {0}", from);
        new File(to).getParentFile().mkdirs();
        try (OutputStream out = new FileOutputStream(to)) {
            long size = 0;
            try (InputStream in = new BufferedInputStream(zip.getInputStream(from))) {
                size += StreamUtil.copy(in, out);
            }
            logger.log(Level.FINE, "unzipped {0} ({1}b -> {2}b)", new Object[]{from, from.getCompressedSize(), size});
        } catch (IOException e) {
            logger.log(Level.SEVERE, "error occoured", e);
            assert false;
        }
    }

    private static void join(Thread thread) {
        try {
            thread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean exists(File file) {
        try {
            synchronized (LOCK) {
                String outerPath = getOuterPath(file.getAbsolutePath()), innerPath = getInnerPath(file.getAbsolutePath(), outerPath);
                ZipInfo info = getInfo(new File(outerPath));
                switch (info.getState()) {
                    case ZIPPED:
                    case UNZIPPING:
                        try (ZipFile zip = getFile(new File(outerPath))) {
                            if (zip.getEntry(innerPath) != null) {
                                return true;
                            }
                            if (zip.stream().anyMatch(e -> e.getName().startsWith(innerPath))) {
                                return true;
                            }
                            return false;
                        }
                    case UNZIPPED:
                    case ZIPPING:
                        return FileUtil.getLinkAwareFile(file.getAbsolutePath()).exists();
                    default:
                        assert false;
                        return false;
                }
            }
        } catch (FileNotFoundException e) {
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    public static long size(File file) throws IOException {
        synchronized (LOCK) {
            String outerPath = getOuterPath(file.getAbsolutePath()), innerPath = getInnerPath(file.getAbsolutePath(), outerPath);
            ZipInfo info = getInfo(new File(outerPath));
            switch (info.getState()) {
                case ZIPPED:
                case UNZIPPING:
                    try (ZipFile zip = getFile(new File(outerPath))) {
                        return zip.getEntry(innerPath).getSize();
                    }
                case UNZIPPED:
                case ZIPPING:
                    return file.length();
                default:
                    assert false;
                    return 0;
            }
        }
    }

    @SuppressWarnings("fallthrough")
    public static InputStream openR(File file) throws IOException {
        synchronized (LOCK) {
            ZipInfo info = getInfo(new File(getOuterPath(file.getAbsolutePath())));
            switch (info.getState()) {
                case ZIPPED: {
                    return getInputStream(info, file);
                }
                case ZIPPING:
                case UNZIPPED: {
                    InputStream in = FileUtil.openR(file);
                    info.open();
                    return new RunOnCloseInputStream(in, () -> info.close());
                }
                case UNZIPPING: {
                    try {
                        return getInputStream(info, file);
                    } catch (IOException ioe) {
                        while (info.getState() == ZipInfo.State.UNZIPPING) {
                            try {
                                InputStream in = FileUtil.openR(file); // there may be a link
                                info.openTmp();
                                return new RunOnCloseInputStream(in, () -> info.closeTmp());
                            } catch (IOException ioe0) {
                                try {
                                    LOCK.wait(100);
                                } catch (InterruptedException e) {
                                    throw new IOException(e);
                                }
                            }
                        }
                    }
                }
                default: {
                    assert false;
                    return null;
                }
            }
        }
    }

    private static InputStream getInputStream(ZipInfo info, File file) throws IOException, FileNotFoundException {
        String outerPath = info.path, innerPath = getInnerPath(file.getAbsolutePath(), info.path);
        ZipFile zip = getFile(new File(outerPath));
        ZipEntry entry = zip.getEntry(innerPath);
        if (entry != null) {
            try {
                InputStream in = zip.getInputStream(entry);
                info.open();
                return new RunOnCloseInputStream(in, () -> info.close(), () -> zip.close());
            } catch (IOException ioe) {
                zip.close();
                throw new IOException(ioe);
            }
        } else {
            zip.close();
            throw new FileNotFoundException(file.getAbsolutePath());
        }
    }

    @SuppressWarnings("fallthrough")
    public static OutputStream openW(File file) throws IOException {
        synchronized (LOCK) {
            String outerPath = getOuterPath(file.getAbsolutePath());
            File container = new File(outerPath);
            ZipInfo info = getInfo(container);
            switch (info.getState()) {
                case ZIPPED: {
                    Thread thread = new Thread(() -> runUnzip(info), "Unzipper " + info.path);
                    thread.setDaemon(false);
                    thread.start();
                }
                case UNZIPPING: {
                    File tmp = FileUtil.createTempFile(new File(info.path).getParentFile(), file.getName(), false);
                    FileUtil.createLink(file.getAbsolutePath(), tmp.getAbsolutePath());
                    OutputStream out = FileUtil.openW(tmp);
                    info.openTmp();
                    return new RunOnCloseOutputStream(out, () -> handleClosedTmpFile(info, getInnerPath(file.getAbsolutePath(), outerPath), tmp), () -> info.closeTmp());
                }
                case UNZIPPED: {
                    OutputStream out = FileUtil.openW(file);
                    info.open();
                    return new RunOnCloseOutputStream(out, () -> info.close());
                }
                case ZIPPING: {
                    info.waitForStableState();
                    return openW(file);
                }
                default: {
                    assert false;
                    return null;
                }
            }
        }
    }

    @SuppressWarnings("fallthrough")
    private static void handleClosedTmpFile(ZipInfo info, String path, File tmp) throws IOException {
        synchronized (LOCK) {
            switch (info.getState()) {
                case ZIPPING:
                case ZIPPED:
                    // these states should not occur because a file has been open until now
                    assert false;
                case UNZIPPING: {
                    info.tmpFiles.put(path, tmp);
                    break;
                }
                case UNZIPPED: {
                    FileUtil.move(tmp, new File(info.path + File.separator + path));
                    break;
                }
                default:
                    assert false;
            }
        }
    }

    private static void checkRezip(ZipInfo info) {
        synchronized (LOCK) {
            if (info.getState() == ZipInfo.State.UNZIPPED && !info.isOpen(true)) {
                Thread thread = new Thread(() -> runZip(info), "Rezipper " + info.path);
                thread.setDaemon(false);
                thread.start();
            }
        }
    }

    private static void runZip(ZipInfo info) {
        Logger logger = Logger.getLogger(Thread.currentThread().getName());
        File container = new File(info.path);
        File tmp = null;
        try {
            synchronized (LOCK) {
                logger.log(Level.INFO, "waiting for all streams to be closed");
                do {
                    // wait a little, maybe someone wants to re-open a stream
                    LOCK.wait(1000 * 10);
                } while (info.isOpen(true));
                if (info.getState() == ZipInfo.State.UNZIPPED) {
                    info.setState(ZipInfo.State.ZIPPING);
                } else {
                    // someone beat us to it, abort
                    assert info.getState() == ZipInfo.State.ZIPPING;
                    return;
                }
            }
            tmp = FileUtil.createTempFile(container.getParentFile(), container.getName(), false);
            logger.log(Level.INFO, "using temporary file " + tmp);

            zip(tmp, container, logger);

            synchronized (LOCK) {
                logger.log(Level.INFO, "waiting for all streams to be closed (read-only)");
                while (info.isOpen(false)) {
                    LOCK.wait();
                }
                logger.log(Level.INFO, "replacing directory with zip file");
                FileUtil.move(tmp, container);
                info.setState(ZipInfo.State.ZIPPED);
            }
        } catch (IOException | InterruptedException e) {
            logger.log(Level.SEVERE, "error occured", e);
            synchronized (LOCK) {
                info.setState(ZipInfo.State.UNZIPPED);
            }
        } finally {
            if (tmp != null) {
                FileUtil.deleteRecursively(tmp);
            }
        }
    }

    private static void runUnzip(ZipInfo info) {
        Logger logger = Logger.getLogger(Thread.currentThread().getName());
        File container = new File(info.path);
        File tmp = null;
        try {
            synchronized (LOCK) {
                if (info.getState() == ZipInfo.State.ZIPPED) {
                    info.setState(ZipInfo.State.UNZIPPING);
                } else {
                    assert info.getState() == ZipInfo.State.UNZIPPING;
                    return;
                }
            }
            tmp = FileUtil.createTempFile(container.getParentFile(), container.getName(), true);
            logger.log(Level.INFO, "using temporary file " + tmp);

            unzip(tmp, container, logger);

            synchronized (LOCK) {
                logger.log(Level.INFO, "waiting for all streams to be closed (read-only)");
                while (info.isOpen(false)) {
                    LOCK.wait();
                }
                logger.log(Level.INFO, "replacing directory with zip file");
                FileUtil.move(tmp, container);
                info.setState(ZipInfo.State.UNZIPPED);
                logger.log(Level.INFO, "moving already written files into directory");
                for (String path : info.tmpFiles.keySet()) {
                    File src = info.tmpFiles.get(path);
                    File dest = new File(info.path + File.separator + path);
                    FileUtil.move(src, dest);
                }
                info.tmpFiles.clear();
            }
        } catch (IOException | InterruptedException e) {
            logger.log(Level.SEVERE, "error occured", e);
            synchronized (LOCK) {
                if (info.getState() == ZipInfo.State.UNZIPPING) {
                    info.setState(ZipInfo.State.ZIPPED);
                }
            }
        } finally {
            if (tmp != null) {
                FileUtil.deleteRecursively(tmp);
            }
        }

        logger.log(Level.INFO, "checking whether rezip is necessary");
        checkRezip(info);
    }

    private static String getOuterPath(String path) throws FileNotFoundException {
        for (File file = new File(path).getParentFile(); file != null; file = file.getParentFile()) {
            if (isZipFile(file) || wasZipFile(file)) {
                String outerPath = file.toString();
                assert path.startsWith(outerPath);
                return outerPath;
            }
        }
        throw new FileNotFoundException(path);
    }

    private static String getInnerPath(String path, String outerPath) {
        String innerPath = path.substring(outerPath.length());
        while (innerPath.length() > 0 && innerPath.charAt(0) == File.separatorChar) {
            innerPath = innerPath.substring(1);
        }
        return innerPath.replace(File.separator, "/");
    }

    private ZipFileUtil() {
        throw new Error("Do not instantiate!");
    }
}
