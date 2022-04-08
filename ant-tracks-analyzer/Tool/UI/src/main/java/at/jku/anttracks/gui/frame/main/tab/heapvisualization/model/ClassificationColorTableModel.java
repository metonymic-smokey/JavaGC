
package at.jku.anttracks.gui.frame.main.tab.heapvisualization.model;

import at.jku.anttracks.gui.frame.main.tab.heapvisualization.HeapVisualizationTab;

import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;
import java.awt.*;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author Christina Rammerstorfer
 */
public class ClassificationColorTableModel implements TableModel {

    private final ObjectVisualizationData data;
    private final HeapVisualizationTab tab;
    private final List<TableModelListener> listeners;

    public ClassificationColorTableModel(ObjectVisualizationData data, HeapVisualizationTab tab) {
        this.data = data;
        this.tab = tab;
        listeners = new CopyOnWriteArrayList<TableModelListener>();
    }

    @Override
    public int getRowCount() {
        return data.getClassificationColors().size();
    }

    @Override
    public int getColumnCount() {
        return 2;
    }

    @Override
    public String getColumnName(int columnIndex) {
        switch (columnIndex) {
            case 0:
                return "Classification";
            case 1:
                return "Color";
            default:
                return null;
        }
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        switch (columnIndex) {
            case 0:
                return Object.class;
            case 1:
                return Color.class;
            default:
                return null;
        }
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        switch (columnIndex) {
            case 1:
                return true;
            default:
                return false;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Entry<Object, PixelDescription> row = data.getClassificationColors().entrySet().toArray(new Entry[0])[rowIndex];
        if (columnIndex == 0) {
            return row.getKey();
        } else if (columnIndex == 1) {
            return row.getValue().color;
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        Color newColor = (Color) aValue;
        Entry<Object, PixelDescription> row = data.getClassificationColors().entrySet().toArray(new Entry[0])[rowIndex];
        tab.setColorForClassification(row.getKey(), newColor);
        listeners.forEach(l -> l.tableChanged(new TableModelEvent(this, rowIndex, rowIndex, columnIndex, TableModelEvent.UPDATE)));
    }

    @Override
    public void addTableModelListener(TableModelListener l) {
        listeners.add(l);
    }

    @Override
    public void removeTableModelListener(TableModelListener l) {
        listeners.remove(l);
    }

}
