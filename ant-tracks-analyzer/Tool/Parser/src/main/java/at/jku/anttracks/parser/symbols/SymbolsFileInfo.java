
package at.jku.anttracks.parser.symbols;

import at.jku.anttracks.parser.io.FileInfo;

import java.io.IOException;

public class SymbolsFileInfo extends FileInfo {

    SymbolsFileInfo(FileInfo file) throws IOException {
        super(file.getHeader(), file.getFileType());
        if (file.getFileType() != SymbolsFile.SYMBOL_FILE_ID) {
            throw new IOException("Illegal file type (expected symbol file)");
        }
    }

}
