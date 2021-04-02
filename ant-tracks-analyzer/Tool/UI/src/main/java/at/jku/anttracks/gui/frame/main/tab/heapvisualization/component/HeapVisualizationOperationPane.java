
package at.jku.anttracks.gui.frame.main.tab.heapvisualization.component;

import at.jku.anttracks.gui.frame.main.tab.heapvisualization.HeapVisualizationTab;
import at.jku.anttracks.gui.frame.main.tab.heapvisualization.model.ClassificationColorTableModel;
import at.jku.anttracks.gui.utils.FXMLUtil;
import at.jku.anttracks.gui.utils.WindowUtil;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;

import javax.swing.*;
import javax.swing.table.TableModel;
import java.awt.*;

public class HeapVisualizationOperationPane extends HBox {

    // Buttons
    @FXML
    private Button collapseButton;
    @FXML
    private Button exportButton;
    @FXML
    private Button selectColorsButton;

    public HeapVisualizationOperationPane() {
        FXMLUtil.load(this, HeapVisualizationOperationPane.class);
    }

    public void init(HeapVisualizationTab tab) {
        collapseButton.setOnAction(ae -> tab.minimizeConfigView());
        selectColorsButton.setOnAction(ae -> {
            JDialog colorPicker = new JDialog((Frame) null, "Select Colors");

            TableModel tableModel = new ClassificationColorTableModel(tab.getModel().getCurrentPixelMap().getData(), tab);
            JTable table = new JTable(tableModel);
            table.setDefaultRenderer(Color.class, new ColorRenderer());
            table.setDefaultEditor(Color.class, new ColorEditor());
            table.setAutoCreateRowSorter(true);
            JScrollPane scrollPane = new JScrollPane(table);
            colorPicker.add(scrollPane);
            colorPicker.setModal(true);
            colorPicker.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            colorPicker.setSize(500, 500);
            colorPicker.pack();
            WindowUtil.INSTANCE.centerOnDefaultScreen(colorPicker);
            colorPicker.setVisible(true);
        });
        exportButton.setOnAction(ae -> {
            PNGExport.showDialog(tab.getModel().getCurrentPixelMap(), tab);
        });
    }

}
