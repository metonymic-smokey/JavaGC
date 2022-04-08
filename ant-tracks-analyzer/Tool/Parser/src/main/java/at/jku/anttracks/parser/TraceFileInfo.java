
package at.jku.anttracks.parser;

import at.jku.anttracks.parser.io.FileInfo;
import at.jku.anttracks.parser.io.FileInfo;

import java.io.IOException;

public class TraceFileInfo extends FileInfo {

    private final int epoch;

    public TraceFileInfo(FileInfo info, int epoch) throws IOException {
        super(info.getHeader(), info.getFileType());
        if (info.getFileType() != TraceFile.TRACE_FILE_ID) {
            throw new IOException("Illegal file type (expected trace file)");
        }
        this.epoch = epoch;
    }

    public int getEpoch() {
        return epoch;
    }

}
