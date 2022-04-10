
package at.jku.anttracks.gui.utils;

import javax.swing.filechooser.FileFilter;
import java.io.File;

public final class FeatureLstFileFilter extends FileFilter {

    @Override
    public boolean accept(File f) {
        if (f.isDirectory()) {
            return true;
        } else {
            String path = f.getAbsolutePath().toLowerCase();
            return path.endsWith(".lst");
        }
    }

    @Override
    public String getDescription() {
        return "Loadable files only (.lst)";
    }
}
