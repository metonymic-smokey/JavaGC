
package at.jku.anttracks.parser.io;

public class FileInfo {

    private final int[] header;
    private final int fileType;

    protected FileInfo(int[] header, int fileType) {
        this.header = header;
        this.fileType = fileType;
    }

    public int[] getHeader() {
        return header.clone();
    }

    public int getFileType() {
        return fileType;
    }

    public boolean matches(FileInfo that) {
        if (that == null) {
            return false;
        } else if (this == that) {
            return true;
        } else if (this.header.length == that.header.length) {
            for (int i = 0; i < header.length; i++) {
                if (this.header[i] != that.header[i]) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

}
