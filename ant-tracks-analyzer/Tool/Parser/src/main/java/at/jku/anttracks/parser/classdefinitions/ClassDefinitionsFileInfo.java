
package at.jku.anttracks.parser.classdefinitions;

import at.jku.anttracks.parser.io.FileInfo;

import java.io.IOException;

public class ClassDefinitionsFileInfo extends FileInfo {

    protected ClassDefinitionsFileInfo(FileInfo file) throws IOException {
        super(file.getHeader(), file.getFileType());
        if (file.getFileType() != ClassDefinitionsFile.FILE_ID) {
            throw new IOException("Illegal file type (expected class definitions file)");
        }
    }
}
