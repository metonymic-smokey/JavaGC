
package at.jku.anttracks.gui.frame.main.tab.heapvisualization.component;

import at.jku.anttracks.gui.frame.main.tab.heapvisualization.HeapVisualizationTab;
import at.jku.anttracks.gui.frame.main.tab.heapvisualization.model.PixelMap;
import at.jku.anttracks.gui.utils.WindowUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;

/**
 * @author Christina Rammerstorfer
 */
public class PNGExport {

    private final PixelMap pixelMap;
    private final HeapVisualizationTab tab;
    private final JDialog dialog;
    private final JCheckBox widthCheckBox;
    private final JCheckBox heightCheckBox;
    private final JCheckBox clusterSizeCheckBox;
    private final JSpinner widthSpinner;
    private final JSpinner heightSpinner;
    private final JSpinner clusterSizeSpinner;
    private final JComboBox<String> unitBox;
    private final JTextField folder;
    private final JTextField fileName;
    private final JButton export;

    private PNGExport(PixelMap pixelMap, HeapVisualizationTab tab) {
        this.pixelMap = pixelMap;
        this.tab = tab;
        dialog = new JDialog((Frame) null, "Export as .PNG");
        dialog.getContentPane().setLayout(new BoxLayout(dialog.getContentPane(), BoxLayout.Y_AXIS));
        widthCheckBox = new JCheckBox("Width: ", true);
        heightCheckBox = new JCheckBox("Height: ", true);
        clusterSizeCheckBox = new JCheckBox("Cluster size: ", false);
        widthSpinner = new JSpinner(new SpinnerNumberModel(400, 0, Integer.MAX_VALUE, 1));
        heightSpinner = new JSpinner(new SpinnerNumberModel(200, 0, Integer.MAX_VALUE, 1));
        clusterSizeSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 1024, 1));
        unitBox = new JComboBox<String>(new String[]{HeapVisualizationTab.NO_PREFIX, HeapVisualizationTab.KILO, HeapVisualizationTab.MEGA, HeapVisualizationTab.GIGA});
        folder = new JTextField(System.getProperty("user.home"));
        fileName = new JTextField("anttracksheap.png");
        export = new JButton("Export");
    }

    private void init() {
        widthCheckBox.addItemListener(e -> {
            if (widthCheckBox.isSelected()) {
                widthSpinner.setEnabled(true);
                if (heightCheckBox.isSelected() && clusterSizeCheckBox.isSelected()) {
                    clusterSizeCheckBox.setSelected(false);
                }
                widthUpdate();
            } else {
                widthSpinner.setEnabled(false);
            }
            setExportButtonEnabled();
        });
        heightCheckBox.addItemListener(e -> {
            if (heightCheckBox.isSelected()) {
                heightSpinner.setEnabled(true);
                if (widthCheckBox.isSelected() && clusterSizeCheckBox.isSelected()) {
                    clusterSizeCheckBox.setSelected(false);
                }
                heightUpdate();
            } else {
                heightSpinner.setEnabled(false);
            }
            setExportButtonEnabled();
        });
        clusterSizeCheckBox.addItemListener(e -> {
            if (clusterSizeCheckBox.isSelected()) {
                clusterSizeSpinner.setEnabled(true);
                unitBox.setEnabled(true);
                if (heightCheckBox.isSelected() && widthCheckBox.isSelected()) {
                    heightCheckBox.setSelected(false);
                }
                clusterSizeUpdate();
            } else {
                clusterSizeSpinner.setEnabled(false);
                unitBox.setEnabled(false);
            }
            setExportButtonEnabled();
        });
        widthSpinner.addChangeListener(e -> {
            if (widthSpinner.isEnabled()) {
                if (heightCheckBox.isSelected() && !clusterSizeCheckBox.isSelected()) {
                    clusterSizeUpdate();
                } else if (!heightCheckBox.isSelected() && clusterSizeCheckBox.isSelected()) {
                    heightUpdate();
                }
            }
        });
        heightSpinner.addChangeListener(e -> {
            if (heightSpinner.isEnabled()) {
                if (widthCheckBox.isSelected() && !clusterSizeCheckBox.isSelected()) {
                    clusterSizeUpdate();
                } else if (!widthCheckBox.isSelected() && clusterSizeCheckBox.isSelected()) {
                    widthUpdate();
                }
            }
        });
        clusterSizeSpinner.setEnabled(false);
        long clusterSize = pixelMap.computeClusterSize((int) widthSpinner.getValue(), (int) heightSpinner.getValue());
        setClusterSize(clusterSize);
        clusterSizeSpinner.addChangeListener(e -> {
            if (clusterSizeSpinner.isEnabled()) {
                if (widthCheckBox.isSelected() && !heightCheckBox.isSelected()) {
                    heightUpdate();
                } else if (!widthCheckBox.isSelected() && heightCheckBox.isSelected()) {
                    widthUpdate();
                }
            }
        });
        unitBox.setEnabled(false);
        unitBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED && unitBox.isEnabled()) {
                if (widthCheckBox.isSelected() && !heightCheckBox.isSelected()) {
                    heightUpdate();
                } else if (!widthCheckBox.isSelected() && heightCheckBox.isSelected()) {
                    widthUpdate();
                }
            }
        });
        JPanel panel0 = new JPanel(new GridLayout(3, 2));
        panel0.add(widthCheckBox);
        panel0.add(widthSpinner);
        panel0.add(heightCheckBox);
        panel0.add(heightSpinner);
        panel0.add(clusterSizeCheckBox);
        JPanel clusterSizePanel = new JPanel(new GridLayout(1, 2));
        clusterSizePanel.add(clusterSizeSpinner);
        clusterSizePanel.add(unitBox);
        panel0.add(clusterSizePanel);
        dialog.add(panel0);
        JPanel panel1 = new JPanel(new BorderLayout());
        panel1.add(new JLabel("Target folder: "), BorderLayout.WEST);
        folder.addKeyListener(new KeyListener() {

            @Override
            public void keyTyped(KeyEvent e) {
                setExportButtonEnabled();
            }

            @Override
            public void keyPressed(KeyEvent e) {
            }

            @Override
            public void keyReleased(KeyEvent e) {
            }

        });
        panel1.add(folder);
        JButton fileChooserButton = new JButton("...");
        fileChooserButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle("Select target directory");
            int returnVal = chooser.showOpenDialog(dialog);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File myFile = chooser.getSelectedFile();
                folder.setText(myFile.getAbsolutePath());
            }
        });
        panel1.add(fileChooserButton, BorderLayout.EAST);
        dialog.add(panel1);
        JPanel panel2 = new JPanel(new BorderLayout());
        panel2.add(new JLabel("Target file name: "), BorderLayout.WEST);
        fileName.addKeyListener(new KeyListener() {

            @Override
            public void keyTyped(KeyEvent e) {
                setExportButtonEnabled();
            }

            @Override
            public void keyPressed(KeyEvent e) {
            }

            @Override
            public void keyReleased(KeyEvent e) {
            }

        });
        panel2.add(fileName);
        dialog.add(panel2);
        export.addActionListener(e -> {
            String fileNameString = fileName.getText();
            if (!fileNameString.endsWith(".png") && !fileNameString.endsWith(".PNG")) {
                fileNameString = fileNameString + ".png";
            }
            String targetFile = folder.getText() + File.separator + fileNameString;
            tab.startPNGExport((int) widthSpinner.getValue(), (int) heightSpinner.getValue(), computeClusterSize(), targetFile);
            dialog.dispose();
        });
        dialog.add(export);
    }

    private long computeClusterSize() {
        long clusterSize = (Integer) clusterSizeSpinner.getValue();
        String unit = (String) unitBox.getSelectedItem();
        switch (unit) {
            case HeapVisualizationTab.NO_PREFIX:
                return clusterSize;
            case HeapVisualizationTab.KILO:
                return clusterSize * 1024;
            case HeapVisualizationTab.MEGA:
                return clusterSize * 1024 * 1024;
            case HeapVisualizationTab.GIGA:
                return clusterSize * 1024 * 1024 * 1024;
        }
        return clusterSize;
    }

    private void setClusterSize(long clusterSize) {
        int unitIdx = 0;
        while (clusterSize > 1024) {
            clusterSize /= 1024;
            unitIdx++;
        }
        clusterSizeSpinner.setValue((int) clusterSize);
        unitBox.setSelectedIndex(unitIdx);
    }

    public static void showDialog(PixelMap pixelMap, HeapVisualizationTab tab) {
        PNGExport components = new PNGExport(pixelMap, tab);
        components.init();
        components.dialog.setModal(true);
        components.dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        components.dialog.setSize(500, 500);
        components.dialog.pack();
        WindowUtil.INSTANCE.centerOnDefaultScreen(components.dialog);
        components.dialog.setVisible(true);
    }

    private void widthUpdate() {
        int width = pixelMap.computeWidth((int) heightSpinner.getValue(), computeClusterSize());
        widthSpinner.setValue(width);
    }

    private void heightUpdate() {
        int height = pixelMap.computeHeight((int) widthSpinner.getValue(), computeClusterSize());
        heightSpinner.setValue(height);
    }

    private void clusterSizeUpdate() {
        long clusterSize = pixelMap.computeClusterSize((int) widthSpinner.getValue(), (int) heightSpinner.getValue());
        setClusterSize(clusterSize);
    }

    private void setExportButtonEnabled() {
        int selectionCount = 0;
        if (widthCheckBox.isSelected()) {
            selectionCount++;
        }
        if (heightCheckBox.isSelected()) {
            selectionCount++;
        }
        if (clusterSizeCheckBox.isSelected()) {
            selectionCount++;
        }
        if (selectionCount < 2) {
            export.setEnabled(false);
            return;
        }
        if (folder.getText() == null || folder.getText().length() == 0) {
            export.setEnabled(false);
            return;
        }
        if (fileName.getText() == null || fileName.getText().length() == 0) {
            export.setEnabled(false);
            return;
        }
        export.setEnabled(true);
    }
}
