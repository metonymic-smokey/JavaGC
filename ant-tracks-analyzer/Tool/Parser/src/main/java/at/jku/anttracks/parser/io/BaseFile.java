
package at.jku.anttracks.parser.io;

import at.jku.anttracks.util.FileUtil;
import at.jku.anttracks.util.ZipFileUtil;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class BaseFile {

    private static final int MAGIC_START = 0xC0FFEE;

    public static FileInfo readFileInfo(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return readFileInfo(in);
        }
    }

    public static FileInfo readFileInfo(InputStream in) throws IOException {
        if (getInt(in) != MAGIC_START) {
            throw new IOException("Illegal magic start!");
        }
        int[] header = new int[getInt(in)];
        for (int i = 0; i < header.length; i++) {
            header[i] = getInt(in);
        }
        int fileType = getInt(in);
        return new FileInfo(header, fileType);
    }

    public static boolean exists(String path) {
        File file = new File(path);
        if (ZipFileUtil.isZipFilePath(path)) {
            return ZipFileUtil.exists(file);
        } else {
            return file.exists();
        }
    }

    public static InputStream openR(String path) throws IOException {
        File file = new File(path);
        if (ZipFileUtil.isZipFilePath(path)) {
            return ZipFileUtil.openR(file);
        } else {
            return FileUtil.openR(file);
        }
    }

    public static OutputStream openW(String path) throws IOException {
        File file = new File(path);
        if (ZipFileUtil.isZipFilePath(path)) {
            return ZipFileUtil.openW(file);
        } else {
            return FileUtil.openW(file);
        }
    }

    protected static int getInt(InputStream in) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        assert buffer.hasArray();
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        int bytes = in.read(buffer.array());
        if (bytes < 4) {
            throw new IOException("Could not read next 4 bytes!");
        }
        return buffer.getInt();
    }

    public static boolean isPlainFile(int fileType, File file) {
        try (InputStream in = new FileInputStream(file)) {
            return isPlainFile(fileType, in);
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean isPlainFile(int fileType, InputStream in) {
        try {
            return fileType == readFileInfo(in).getFileType();
        } catch (IOException e) {
            return false;
        }
    }

    protected static boolean isPlainFile(int fileType, ZipFile zip, ZipEntry entry) {
        try (InputStream in = zip.getInputStream(entry)) {
            return isPlainFile(fileType, in);
        } catch (IOException e) {
            return false;
        }
    }

    public static void copy(String from, String to) throws IOException {
        try (InputStream in = BaseFile.openR(from)) {
            try (OutputStream out = BaseFile.openW(to)) {
                int data = -1;
                while ((data = in.read()) != -1) {
                    out.write(data);
                }
            }
        }
    }
}
