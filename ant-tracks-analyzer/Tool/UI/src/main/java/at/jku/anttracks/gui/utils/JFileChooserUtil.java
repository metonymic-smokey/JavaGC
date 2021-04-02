
package at.jku.anttracks.gui.utils;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class JFileChooserUtil {

    public static void chooseFileAndSave(Component parent, boolean directory, String template, Action action) {
        File file = chooseFileForSave(parent, directory, template);
        if (file != null) {
            Thread thread = new Thread(() -> run(action, file, directory), "Save " + file + " Worker");
            thread.start();
        }
    }

    private static void run(Action action, File file, boolean directory) {
        try {
            action.run(file);
            int result = JOptionPane.showConfirmDialog(null,
                                                       "Saving " + file + " finished!\n\nDo you want to " + (directory ?
                                                                                                             "show the directory in " + "explorer?" :
                                                                                                             "open the file?"),
                                                       "Save",
                                                       JOptionPane.YES_NO_OPTION);
            if (result == 0) {
                Desktop.getDesktop().open(file);
            }
        } catch (Exception ioe) {
            ioe.printStackTrace(System.err);
            JOptionPane.showMessageDialog(null, "Saving " + file + " failed!\n\t" + ioe, "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static File chooseFileForSave(Component parent, boolean directory, String template) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(directory ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_ONLY);
        chooser.setSelectedFile(new File(template));
        if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (file.exists()) {
                // Check if given type (file or directory) was selected
                if (directory == file.isDirectory()) {
                    int result = -1;
                    if (file.isDirectory()) {
                        // Directory
                        if (file.list().length > 0) {
                            result = JOptionPane.showConfirmDialog(null,
                                                                   "Directory not empty, do you want to clear it?",
                                                                   "Save File Error",
                                                                   JOptionPane.YES_NO_OPTION,
                                                                   JOptionPane.QUESTION_MESSAGE);
                            if (result == JOptionPane.YES_OPTION) {
                                FileUtil.deleteTree(file);
                            } else {
                                file = null;
                            }
                        }
                    } else {
                        // Single file
                        result = JOptionPane.showConfirmDialog(null,
                                                               "File already exists, do you want to override it?",
                                                               "Save File Error",
                                                               JOptionPane.YES_NO_OPTION,
                                                               JOptionPane.QUESTION_MESSAGE);
                        if (result == JOptionPane.YES_OPTION) {
                            FileUtil.deleteTree(file);
                        } else {
                            file = null;
                        }
                    }
                } else {
                    JOptionPane.showConfirmDialog(null,
                                                  "File already exists, and does not match required type (regular file, directory)!",
                                                  "Save File Error",
                                                  JOptionPane.OK_OPTION,
                                                  JOptionPane.ERROR_MESSAGE);
                    file = null;
                }
            }
            return file;
        } else {
            return null;
        }
    }

    public static String generatePath(String projectName, String id, String type, String sub, String extension) {
        StringBuilder path = new StringBuilder();

        if (projectName != null) {
            path.append(projectName.replace(" ", "-"));
            path.append("_");
        }
        if (id != null) {
            path.append(id.replace(" ", "-"));
            path.append("_");
        }
        if (type != null) {
            path.append(type.replace(" ", "-"));
            path.append("_");
        }
        if (sub != null) {
            path.append(sub.replace(" ", "-"));
            path.append("_");
        }

        assert path.length() > 0 : "File name not set!";
        // Check if path ends with underscore
        if (path.charAt(path.length() - 1) == '_') {
            path.deleteCharAt(path.length() - 1);
        }

        if (extension != null) {
            path.append(extension.replace(" ", "-"));
        }

        return path.toString();
    }

    public interface Action {
        void run(File file) throws Exception;
    }

}
