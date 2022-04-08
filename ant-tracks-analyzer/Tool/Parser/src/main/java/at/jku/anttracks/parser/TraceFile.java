
package at.jku.anttracks.parser;

import at.jku.anttracks.parser.io.BaseFile;
import at.jku.anttracks.parser.io.InputStreamScanner;
import at.jku.anttracks.parser.io.MappedFileScanner;
import at.jku.anttracks.parser.io.BaseFile;
import at.jku.anttracks.parser.io.InputStreamScanner;
import at.jku.anttracks.parser.io.MappedFileScanner;
import at.jku.anttracks.util.ZipFileUtil;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import static at.jku.anttracks.util.ZipFileUtil.isZipFile;

public class TraceFile extends BaseFile {

    public final static int TRACE_FILE_ID = 1;
    private static Logger LOGGER = Logger.getLogger(TraceFile.class.getSimpleName());

    // Multiple infos possible if reading a zip file
    public static TraceFileInfo[] readTraceFileInfo(File file) throws IOException {
        if (file.exists() && file.isFile() && isZipFile(file)) {
            try (ZipFile zip = ZipFileUtil.getFile(file)) {
                return zip.stream().filter(entry -> !entry.isDirectory()).filter(entry -> isPlainFile(TRACE_FILE_ID, zip, entry)).map(entry -> {
                    try (InputStream in = zip.getInputStream(entry)) {
                        return readTraceFileInfo(in);
                    } catch (Exception ex) {
                        return null;
                    }
                }).toArray(TraceFileInfo[]::new);
            }
        } else if (file.exists()) {
            if (file.isDirectory()) {
                return Arrays.stream(file.listFiles()).filter(entry -> entry.isFile()).filter(entry -> isPlainFile(TRACE_FILE_ID, entry)).map(entry -> {
                    try (InputStream in = new FileInputStream(entry)) {
                        return readTraceFileInfo(in);
                    } catch (Exception ex) {
                        return null;
                    }
                }).toArray(TraceFileInfo[]::new);
            } else {
                return new TraceFileInfo[]{readTraceFileInfo(new FileInputStream(file))};
            }
        }
        return new TraceFileInfo[0];
    }

    public static TraceFileInfo readTraceFileInfo(InputStream in) throws IOException {
        return new TraceFileInfo(readFileInfo(in), getInt(in));
    }

    public static Access[] open(File container, int[] header, File example) throws IOException {
        Collection<Access> files = collectTraceFileCandidates(container, example);
        Map<Integer, Access> traceFiles = filterTraceFileCandidates(header, example, files);
        return traceFiles.values().toArray(new Access[traceFiles.size()]);
    }

    public static boolean hasValidMetaData(File traceFile, String metaHeaderPath) {
        boolean validMetaData = false;
        try (DataInputStream in = new DataInputStream(BaseFile.openR(metaHeaderPath))) {
            LOGGER.log(Level.INFO, "found meta data");
            int[] header = new int[in.readInt()];
            for (int i = 0; i < header.length; i++) {
                header[i] = in.readInt();
            }

            TraceFileInfo[] tfis = TraceFile.readTraceFileInfo(traceFile);
            int[] traceHeader = tfis.length > 0 ? tfis[0].getHeader() : new int[0];

            validMetaData = Arrays.equals(header, traceHeader);
            if (validMetaData) {
                LOGGER.log(Level.INFO, "meta data matches");
            } else {
                LOGGER.log(Level.INFO, "meta data does not match");
            }
        } catch (FileNotFoundException fnfe) {
            validMetaData = false;
            LOGGER.log(Level.INFO, "no meta data found");
        } catch (IOException ioe) {
            validMetaData = false;
            LOGGER.log(Level.SEVERE, "error occurred while checking if reparse is necessary", ioe);
        }
        return validMetaData;
    }

    private static Collection<Access> collectTraceFileCandidates(File container, File example) throws IOException {
        if (container.exists() && container.isFile() && isZipFile(container)) {
            try (ZipFile zip = ZipFileUtil.getFile(container)) {
                return zip.stream()
                          .filter(entry -> !entry.isDirectory())
                          .filter(entry -> isPlainFile(TRACE_FILE_ID, zip, entry))
                          .map(entry -> new ZipEntryAccess(container, entry.getName()))
                          .collect(Collectors.toList());
            }
        } else if (example.exists()) {
            if (example.isDirectory()) {
                return Arrays.stream(example.listFiles())
                             .filter(file -> file.isFile())
                             .filter(entry -> isPlainFile(TRACE_FILE_ID, entry))
                             .map(file -> new FileAccess(file))
                             .collect(Collectors.toList());
            } else {
                if (isPlainFile(TRACE_FILE_ID, example)) {
                    return Collections.singleton(new FileAccess(example));
                }
                return Collections.emptyList();
            }
        } else {
            // this is legacy code and should be removed! (just kept in to be
            // compatible with old traces)
            List<Access> files = new ArrayList<>();
            String template = getTemplatePath(example.getAbsolutePath());
            int index = 0;
            while (index >= 0) {
                String path = template + "_" + index;
                File file = new File(path);
                if (file.exists() && file.isFile()) {
                    files.add(new FileAccess(file));
                    index++;
                } else {
                    index = -1;
                }
            }
            return files;
        }
    }

    private static Map<Integer, Access> filterTraceFileCandidates(int[] header, File example, Collection<Access> files) throws IOException {
        Map<Integer, Access> traceFiles = new TreeMap<>();
        for (Access file : files) {
            try (InputStream in = file.open()) {
                TraceFileInfo info = readTraceFileInfo(in);
                if (Arrays.equals(header, info.getHeader())) {
                    if (traceFiles.containsKey(info.getEpoch())) {
                        throw new IOException("Multiple trace files with same epoch found!");
                    } else {
                        traceFiles.put(info.getEpoch(), file);
                    }
                }
            } catch (IOException ioe) {
                // wrong format, just ignore
            }
        }
        if (traceFiles.size() == 0) {
            throw new IOException("No trace file found! (like '" + example + "')");
        }
        return traceFiles;
    }

    private static String getTemplatePath(String example) {
        String template;
        int separatorIndex = example.lastIndexOf('_');
        if (separatorIndex < 0) {
            template = example;
        } else {
            String indexString = example.substring(separatorIndex + 1);
            try {
                Integer.parseInt(indexString);
                template = example.substring(0, separatorIndex);
            } catch (NumberFormatException nfe) {
                template = example;
            }
        }
        return template;
    }

    public static abstract class Access implements Closeable {
        public abstract InputStream open() throws IOException;

        public at.jku.anttracks.parser.Scanner open(long globalPosition, long from, long to) throws IOException {
            return new InputStreamScanner(globalPosition, open(), from, to);
        }

        public abstract long length();

        @Override
        public void close() throws IOException {}
    }

    private static final class FileAccess extends Access {

        private final File file;

        public FileAccess(File file) {
            this.file = file;
        }

        @Override
        public FileInputStream open() throws IOException {
            return new FileInputStream(file);
        }

        @Override
        public Scanner open(long globalPosition, long from, long to) throws IOException {
            // return new InputStreamScanner(globalPosition, open(), from, to);
            return new MappedFileScanner(globalPosition, open(), from, to);
        }

        @Override
        public long length() {
            return file.length();
        }

    }

    private static final class ZipEntryAccess extends Access {
        private final File file;

        public ZipEntryAccess(File container, String entry) {
            this.file = new File(container.getAbsolutePath() + File.separator + entry);
        }

        @Override
        public InputStream open() throws IOException {
            return ZipFileUtil.openR(file);
        }

        @Override
        public long length() {
            try {
                return ZipFileUtil.size(file);
            } catch (IOException e) {
                assert false;
                throw new RuntimeException(e);
            }
        }
    }

}
