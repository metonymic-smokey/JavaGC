
package at.jku.anttracks.parser.symbols;

import at.jku.anttracks.parser.TraceFile;
import at.jku.anttracks.parser.TraceFileInfo;
import at.jku.anttracks.parser.io.BaseFile;
import at.jku.anttracks.util.RunOnCloseInputStream;
import at.jku.anttracks.util.ZipFileUtil;

import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static at.jku.anttracks.util.ZipFileUtil.isZipFile;

public class SymbolsFile extends BaseFile {

    public static final int SYMBOL_FILE_ID = 0;

    public static SymbolsFileInfo readSymbolsFileInfo(InputStream in) throws IOException {
        return new SymbolsFileInfo(readFileInfo(in));
    }

    public static InputStream open(File container) throws IOException {
        return open(container, true);
    }

    public static InputStream open(File container, boolean tryRepair) throws IOException {
        if (!container.exists()) {
            throw new FileNotFoundException(container.toString());
        } else if (container.isDirectory()) {
            return openSymbolsFileInDirectoryContainer(container);
        } else if (isZipFile(container)) {
            return openSymbolsFileInZipContainer(container, tryRepair);
        } else if (isPlainFile(SYMBOL_FILE_ID, container)) {
            return new FileInputStream(container);
        } else {
            throw new IOException("File '" + container + "' is not a symbols file / does not contain a symbols file!");
        }
    }

    private static InputStream openSymbolsFileInDirectoryContainer(File container) throws IOException {
        assert container.isDirectory();
        List<File> entries = Arrays.stream(container.listFiles())
                                   .filter(entry -> !entry.isDirectory())
                                   .filter(entry -> isPlainFile(SYMBOL_FILE_ID, entry))
                                   .collect(Collectors.<File>toList());
        if (entries.size() == 0) {
            throw new IOException("No symbol file found in '" + container + "'!");
        } else if (entries.size() > 1) {
            throw new IOException("Multiple symbol files found in '" + container + "'!");
        } else {
            assert entries.size() == 1;
            return new FileInputStream(entries.get(0));
        }
    }

    private static InputStream openSymbolsFileInZipContainer(File container, boolean tryRepair) throws IOException {
        assert isZipFile(container);
        ZipFile zip = ZipFileUtil.getFile(container);
        List<ZipEntry> entries = zip.stream()
                                    .filter(entry -> !entry.isDirectory())
                                    .filter(entry -> isPlainFile(SYMBOL_FILE_ID, zip, entry))
                                    .collect(Collectors.<ZipEntry>toList());
        if (entries.size() == 0) {
            throw new IOException("No symbol file found in '" + container + "'!");
        } else if (entries.size() > 1) {
            throw new IOException("Multiple symbol files found in '" + container + "'!");
        } else {
            assert entries.size() == 1;
            return new RunOnCloseInputStream(zip.getInputStream(entries.get(0)), () -> zip.close());
        }
    }

    public static File findSymbolsFileToTrace(File trace) throws IOException {
        TraceFileInfo[] info = TraceFile.readTraceFileInfo(trace);
        if (info.length == 0) {
            throw new IOException("File \"" + trace + "\" is not a trace file, therefore, cannot find a matching symbols file");
        }
        if (Stream.of(info).map(i -> i.getHeader()).reduce((a, b) -> Arrays.equals(a, b) ? a : new int[0]).get().length == 0) {
            throw new IOException("Multiple symbol files found for trace file location '" + trace + "'!");
        }
        int[] header = info[0].getHeader();

        // Check if trace is contained in a zip file
        File container = (BaseFile.isPlainFile(TraceFile.TRACE_FILE_ID, trace) ? trace.getParentFile() : trace);

        if (!container.exists()) {
            throw new FileNotFoundException(container.toString());
        } else if (container.isDirectory()) {
            return findSymbolsFileInDirectoryContainer(container, header);
        } else if (isZipFile(container)) {
            return findSymbolsFileInZipContainer(container, header, true);
        } else if (isPlainFile(SYMBOL_FILE_ID, container)) {
            throw new IOException("The given trace file '" + container + "' is not a trace file but already a symbols file!");
        } else {
            throw new IOException("File '" + container + "' is not a symbols file / does not contain a symbols file!");
        }
    }

    private static File findSymbolsFileInDirectoryContainer(File container, int[] header) throws IOException {
        assert container.isDirectory();
        List<File> entries = Arrays.stream(container.listFiles())
                                   .filter(entry -> !entry.isDirectory())
                                   .filter(entry -> isPlainFile(SYMBOL_FILE_ID, entry))
                                   .filter(entry -> {
                                       try {
                                           return Arrays.equals(readSymbolsFileInfo(open(entry)).getHeader(), header);
                                       } catch (IOException ex) {
                                           // IOExceptions occurs if the file cannot be opened as symbols
                                           // file (non-matching file id)
                                           return false;
                                       }
                                   })
                                   .collect(Collectors.toList());

        if (entries.size() == 0) {
            throw new IOException("No symbol file with matching header found in '" + container + "'!");
        } else if (entries.size() > 1) {
            throw new IOException("Multiple symbol files with matching header found in '" + container + "'!");
        } else {
            assert entries.size() == 1;
            return entries.get(0);
        }
    }

    private static File findSymbolsFileInZipContainer(File container, int[] header, boolean tryRepair) throws IOException {
        assert isZipFile(container);
        final List<ZipEntry> entries;
        try (final ZipFile zip = ZipFileUtil.getFile(container)) {
            entries = zip.stream().filter(entry -> !entry.isDirectory()).filter(entry -> isPlainFile(SYMBOL_FILE_ID, zip, entry)).filter(entry -> {
                try {
                    return Arrays.equals(readSymbolsFileInfo(zip.getInputStream(entry)).getHeader(), header);
                } catch (Exception ex) {
                    // IOExceptions occurs if the file cannot be opened as symbols
                    // file (non-matching file id)
                    return false;
                }
            }).collect(Collectors.<ZipEntry>toList());
        }
        if (entries.size() == 0) {
            throw new IOException("No symbol file with matching header found in '" + container + "'!");
        } else if (entries.size() > 1) {
            throw new IOException("Multiple symbol files with matching header found in '" + container + "'!");
        } else {
            assert entries.size() == 1;
            return container;
        }
    }
}
