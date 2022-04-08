
package at.jku.anttracks.gui.utils;

import at.jku.anttracks.parser.TraceFile;

import javax.swing.filechooser.FileFilter;
import java.io.File;
import java.io.IOException;

public final class TraceFileFilter extends FileFilter {

    @Override
    public boolean accept(File f) {
        if (f.isDirectory() || f.getName().endsWith(".zip")) {
            return true;
        } else {
            try {
                return TraceFile.readTraceFileInfo(f).length > 0;
            } catch (IOException ex) {
                return false;
            }
        }
    }

    @Override
    public String getDescription() {
        return "Loadable files only";
    }
}
