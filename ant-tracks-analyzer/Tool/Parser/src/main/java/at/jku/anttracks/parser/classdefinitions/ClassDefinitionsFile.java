
package at.jku.anttracks.parser.classdefinitions;

import at.jku.anttracks.parser.TraceFile;
import at.jku.anttracks.parser.TraceFileInfo;
import at.jku.anttracks.parser.io.BaseFile;
import at.jku.anttracks.util.RunOnCloseInputStream;
import at.jku.anttracks.util.ZipFileUtil;

import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static at.jku.anttracks.util.ZipFileUtil.isZipFile;

public class ClassDefinitionsFile extends BaseFile {

    public static final int FILE_ID = 2;

    public static ClassDefinitionsFileInfo readClassDefinitionsFileInfo(InputStream in) throws IOException {
        return new ClassDefinitionsFileInfo(readFileInfo(in));
    }

    public static InputStream open(File container) throws IOException {
        return open(container, true);
    }

    public static InputStream open(File container, boolean tryRepair) throws IOException {
        if (!container.exists()) {
            throw new FileNotFoundException(container.toString());
        } else if (container.isDirectory()) {
            return openClassDefinitionsFileInDirectoryContainer(container);
        } else if (isZipFile(container)) {
            return openClassDefinitionsFileInZipContainer(container, tryRepair);
        } else if (isPlainFile(FILE_ID, container)) {
            return new FileInputStream(container);
        }
        throw new IOException("File '" + container + "' is not a class definitions file and does not contain one!");
    }

    private static InputStream openClassDefinitionsFileInDirectoryContainer(File container) throws IOException {
        assert container.isDirectory();
        final List<File> entries = Arrays.stream(container.listFiles())
                                         .filter(entry -> !entry.isDirectory())
                                         .filter(entry -> isPlainFile(FILE_ID, entry))
                                         .collect(Collectors.<File>toList());
        switch (entries.size()) {
            case 1:
                return new FileInputStream(entries.get(0));
            case 0:
                throw new IOException("No class definitions file found in '" + container + "'!");
            default:
                throw new IOException("Multiple class definitions files found in '" + container + "'!");
        }
    }

    private static InputStream openClassDefinitionsFileInZipContainer(File container, boolean tryRepair) throws IOException {
        assert isZipFile(container);
        final ZipFile zip = ZipFileUtil.getFile(container);
        final List<ZipEntry> entries = zip.stream()
                                          .filter(entry -> !entry.isDirectory())
                                          .filter(entry -> isPlainFile(FILE_ID, zip, entry))
                                          .collect(Collectors.<ZipEntry>toList());
        switch (entries.size()) {
            case 1:
                return new RunOnCloseInputStream(zip.getInputStream(entries.get(0)), () -> zip.close());
            case 0:
                throw new IOException("No class definitions file found in '" + container + "'!");
            default:
                throw new IOException("Multiple class definitions files found in '" + container + "'!");
        }
    }

    public static File findClassDefinitionsFileToTrace(File trace) throws IOException {
        final TraceFileInfo[] info = TraceFile.readTraceFileInfo(trace);
        if (info.length == 0) {
            throw new IOException("File \"" + trace + "\" is not a trace file, therefore, cannot find a matching class definitions file");
        }
        if (Arrays.stream(info).map(i -> i.getHeader()).reduce((a, b) -> Arrays.equals(a, b) ? a : new int[0]).get().length == 0) {
            throw new IOException("Multiple class definitions files found for trace file location '" + trace + "'!");
        }
        final int[] header = info[0].getHeader();

        // Check if trace is contained in a zip file
        final File container = (BaseFile.isPlainFile(TraceFile.TRACE_FILE_ID, trace) ? trace.getParentFile() : trace);

        if (!container.exists()) {
            throw new FileNotFoundException(container.toString());
        } else if (container.isDirectory()) {
            return findClassDefinitionsFileInDirectoryContainer(container, header);
        } else if (isZipFile(container)) {
            return findClassDefinitionsFileInZipContainer(container, header, true);
        } else if (isPlainFile(FILE_ID, container)) {
            throw new IOException("The given trace file '" + container + "' is not a trace file but already a class definitions file!");
        }
        return null;
    }

    private static File findClassDefinitionsFileInDirectoryContainer(File container, int[] header) throws IOException {
        assert container.isDirectory();
        final List<File> entries = Arrays.stream(container.listFiles())
                                         .filter(entry -> !entry.isDirectory())
                                         .filter(entry -> isPlainFile(FILE_ID, entry))
                                         .filter(entry -> {
                                             try {
                                                 return Arrays.equals(readClassDefinitionsFileInfo(open(entry)).getHeader(), header);
                                             } catch (IOException ex) {
                                                 // Occurs if the file cannot be opened as a class definitions file (non-matching file ID)
                                                 return false;
                                             }
                                         })
                                         .collect(Collectors.toList());

        switch (entries.size()) {
            case 0:
                return null;
            case 1:
                return entries.get(0);
            default:
                throw new IOException("Multiple class definitions files with matching header found in '" + container + "'!");
        }
    }

    private static File findClassDefinitionsFileInZipContainer(File container, int[] header, boolean tryRepair) throws IOException {
        assert isZipFile(container);
        final List<ZipEntry> entries;
        try (final ZipFile zip = ZipFileUtil.getFile(container)) {
            entries = zip.stream().filter(entry -> !entry.isDirectory()).filter(entry -> isPlainFile(FILE_ID, zip, entry)).filter(entry -> {
                try {
                    return Arrays.equals(readClassDefinitionsFileInfo(zip.getInputStream(entry)).getHeader(), header);
                } catch (Exception ex) {
                    // Occurs if the file cannot be opened as a class definitions file (non-matching file ID)
                    return false;
                }
            }).collect(Collectors.<ZipEntry>toList());
        }

        switch (entries.size()) {
            case 0:
                return null;
            case 1:
                return container;
            default:
                throw new IOException("Multiple class definitions files with matching header found in '" + container + "'!");
        }
    }
}
