
package at.jku.anttracks.gui.component.treetable;

import at.jku.anttracks.gui.model.*;
import at.jku.anttracks.gui.model.tablecellrenderer.*;

import javax.swing.*;
import javax.swing.RowSorter.SortKey;
import javax.swing.event.CellEditorListener;
import javax.swing.table.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class TreeTable<T> extends JTable {
    private static final long serialVersionUID = 1L;
    private final Color[] rowColors;
    private final Color columnSeperatorColor = Color.BLACK.brighter();

    private JPopupMenu contextMenu;
    private TreeContextMenuItem copyElementContextMenuItem;
    private TreeContextMenuItem copyRowsContextMenuItem;
    private Point contextMenuOpenedAt;
    private boolean hasBeenCleaned;

    protected static final Logger LOGGER = Logger.getLogger(TreeTable.class.getSimpleName());

    public TreeTable(TreeTableModelInterface<T> model) {
        super();

        setAutoCreateColumnsFromModel(true);
        setShowGrid(false);
        setIntercellSpacing(new Dimension(0, 0));

        setBackground(Color.WHITE);
        setForeground(Color.BLACK);
        setSelectionBackground(Color.LIGHT_GRAY);
        setSelectionForeground(Color.BLACK);
        setGridColor(Color.LIGHT_GRAY);
        rowColors = computeZebraColors();

        setTableHeader(new JTableHeader(columnModel) {
            private static final long serialVersionUID = 1L;

            @Override
            public String getToolTipText(MouseEvent e) {
                Point p = e.getPoint();
                int index = TreeTable.this.columnAtPoint(p);
                return TreeTable.this.getColumnName(index);
            }
        });

        TreeTableSelectionModel<T> selection = new TreeTableSelectionModel<>(this);
        TreeNodeTableCellRenderer<T> renderer = new TreeNodeTableCellRenderer<>(TreeTable::memoryMonitoringTreeNodeText);
        TreeNodeTableCellEditor<T> editor = new TreeNodeTableCellEditor<>(renderer, selection);
        setSelectionModel(selection);
        setDefaultRenderer(TreeNode.class, renderer);
        setDefaultEditor(TreeNode.class, editor);

        setModel(model);
        setupContextMenu();
    }

    private static <T> String memoryMonitoringTreeNodeText(TreeNode<T> node) {
        String formatted = node.getTitle();
        int braceIndex = formatted.indexOf('(');
        int index = braceIndex >= 0 ? formatted.substring(0, braceIndex).lastIndexOf('.') : formatted.lastIndexOf('.');

        if (index >= 0) {
            String plainStart = formatted.substring(0, index + 1);
            String bold, plainEnd;

            if (braceIndex != -1) {
                bold = formatted.substring(index + 1, braceIndex);
                plainEnd = formatted.substring(braceIndex);
            } else {
                bold = formatted.substring(index + 1);
                plainEnd = "";
            }

            return "<html><tt>" + escapeForHTML(plainStart) + "<b>" + escapeForHTML(bold) + "</b>" + escapeForHTML(plainEnd) + "</tt></html>";
        } else {
            return "<html><tt>" + escapeForHTML(formatted) + "</tt></html>";
        }
    }

    private static String escapeForHTML(String input) {
        return input.replace("<", "&lt;").replace(">", "&gt;");
    }

    @SuppressWarnings("unchecked")
    @Override
    public TreeNodeValue<T> getValueAt(int row, int col) {
        return (TreeNodeValue<T>) super.getValueAt(row, col);
    }

    public void setModel(TreeTableModelInterface<T> model) {
        // Get current model
        @SuppressWarnings("unchecked")
        TreeTableModelAdapter<T> adapter = getModel() == null ? null : (getModel() instanceof TreeTableModelAdapter<?> ? (TreeTableModelAdapter<T>) getModel() : null);

        // Store sort keys
        List<SortKey> sortKeys = new ArrayList<>();
        if (adapter != null && adapter.getColumnCount() == model.getColumnCount()) {
            // Keep current sorting if number of columns did not change
            getRowSorter().getSortKeys().forEach(x -> sortKeys.add(new SortKey(x.getColumn(), x.getSortOrder())));

            // Update model & columns
            adapter.updateModel(model);
            LOGGER.finer("Update model");
            createDefaultColumnsFromModel();
        } else {
            // Fall back on default sorting (1) if model has not been set yet or (2) if columns changed
            for (int column = 0; column < model.getColumnCount(); column++) {
                if (model.getColumnName(column).equals(model.getDefaultSorting())) {
                    sortKeys.add(new RowSorter.SortKey(column, SortOrder.DESCENDING));
                    break;
                }
            }

            // Create new adapter
            adapter = new TreeTableModelAdapter<>(model);
            LOGGER.finer("Set new model");
            setModel(adapter);
        }

        LOGGER.finer("Reset sorter");
        setNodeRowSorter(model);
        getRowSorter().setSortKeys(new ArrayList<SortKey>());
        getRowSorter().setSortKeys(sortKeys);
        LOGGER.finer("Reset renderers");
        resetRenderers();
        LOGGER.finer("Reset column widths");
        resetColumnWidths(model);
    }

    public void resetColumnWidths(TreeTableModelInterface<T> model) {
        for (int c = 0; c < model.getColumnCount(); c++) {
            getColumnModel().getColumn(c).setPreferredWidth(200);
        }
    }

    public void resetRenderers() {
        for (int i = 0; i < getColumnCount(); i++) {
            if (i == 0) {
                getTableHeader().getColumnModel().getColumn(i).setHeaderRenderer(new HeaderCellRenderer(this, SwingConstants.LEFT));
            } else {
                getTableHeader().getColumnModel().getColumn(i).setHeaderRenderer(new HeaderCellRenderer(this, SwingConstants.RIGHT));
            }
        }

        setDefaultRenderer(Long.class, new NumberCellRenderer());
        setDefaultRenderer(Double.class, new NumberCellRenderer());
        setDefaultRenderer(Types.class, new TypesCellRenderer());
        setDefaultRenderer(ValueWithReference.class, new ValueWithReferenceCellRenderer());
        setDefaultRenderer(ApproximateLong.class, new ApproximateLongCellRenderer());
        setDefaultRenderer(ApproximateDouble.class, new ApproximateDoubleCellRenderer());
        setDefaultRenderer(Percentage.class, new PercentageCellRenderer());
    }

    @SuppressWarnings("unchecked")
    private void setNodeRowSorter(TreeTableModelInterface<T> model) {
        setRowSorter(new TreeTableRowSorter(model, (TreeTableModelAdapter<T>) getModel()));
    }

    @Override
    public void setDefaultRenderer(Class<?> columnClass, TableCellRenderer renderer) {
        // Wrap renderers to use the TreeNodeValue's inner value
        // TreeTable is assumed to only be used in combination with
        // TreeTableModelAdapter
        TableCellRenderer modifiedRenderer = (table, value, isSelected, hasFocus, row, column) -> {
            // Use inner value
            if (value != null) {
                TreeNodeValue<?> casted = (TreeNodeValue<?>) value;
                value = casted.value;
            } /* else {
                ApplicationStatistics.getInstance().inc("TreeNodeValue from TreeTableModelAdapter is null");
            } */
            return renderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        };

        super.setDefaultRenderer(columnClass, modifiedRenderer);
    }

    @Override
    public void setDefaultEditor(Class<?> columnClass, TableCellEditor editor) {
        // Wrap editors to use the TreeNodeValue's inner value
        TableCellEditor modifiedEditor = new TableCellEditor() {

            @Override
            public boolean shouldSelectCell(EventObject anEvent) {
                return editor.shouldSelectCell(anEvent);
            }

            @Override
            public boolean isCellEditable(EventObject anEvent) {
                return editor.isCellEditable(anEvent);
            }

            @Override
            public Object getCellEditorValue() {
                return editor.getCellEditorValue();
            }

            @Override
            public boolean stopCellEditing() {
                return editor.stopCellEditing();
            }

            @Override
            public void cancelCellEditing() {
                editor.cancelCellEditing();
            }

            @Override
            public void addCellEditorListener(CellEditorListener l) {
                editor.addCellEditorListener(l);
            }

            @Override
            public void removeCellEditorListener(CellEditorListener l) {
                editor.removeCellEditorListener(l);
            }

            @Override
            public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
                // Use inner value
                TreeNodeValue<?> casted = (TreeNodeValue<?>) value;
                assert casted != null : "Model must always return a TreeNodeValue";
                value = casted.value;
                return editor.getTableCellEditorComponent(table, value, isSelected, row, column);
            }
        };

        super.setDefaultEditor(columnClass, modifiedEditor);
    }

    @Override
    // Is for internal use only! Should not be called from the outside!!
    public void setModel(TableModel model) {
        // throw new UnsupportedOperationException("setModel(TableModel model)
        // is not support in TreeTables, use setModel(TreeTableModel<T> model)
        // instead");
        super.setModel(model);
    }

    /**
     * Computes zebra colors as 2-length {@link Color} array based on current background color. If no background is set (
     * {@link #getBackground()} returns null), no zebra colors are returned. Instead, an array consisting of 2 {@link Color#White} entries
     * is returned. If the background color is set, but no selection color ({@link #getSelectionBackground()} returns null), an array
     * consisting of 2 time #getBackground() is returned.
     *
     * @return An array of size 2, containing the computed zebra colors.
     */
    private Color[] computeZebraColors() {
        Color[] colors = new Color[2];
        if ((colors[0] = getBackground()) == null) {
            // No background color set, return only white
            colors[0] = colors[1] = Color.white;
            return colors;
        }

        final Color sel = getSelectionBackground();
        if (sel == null) {
            // No selection color set, return only background color
            colors[1] = colors[0];
            return colors;
        }

        // Calculate zebra colors
        final float[] bgHSB = Color.RGBtoHSB(colors[0].getRed(), colors[0].getGreen(), colors[0].getBlue(), null);
        final float[] selHSB = Color.RGBtoHSB(sel.getRed(), sel.getGreen(), sel.getBlue(), null);
        colors[1] = Color.getHSBColor((selHSB[1] == 0.0 || selHSB[2] == 0.0) ? bgHSB[0] : selHSB[0],
                                      0.1f * selHSB[1] + 0.9f * bgHSB[1],
                                      bgHSB[2] + ((bgHSB[2] < 0.5f) ? 0.05f : -0.05f));
        return colors;
    }

    /**
     * Sets the background for non-selected renderer cells
     */
    @Override
    public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
        JComponent component = (JComponent) super.prepareRenderer(renderer, row, column);
        if (isCellSelected(row, column)) {
            component.setForeground(getSelectionForeground());
            component.setBackground(getSelectionBackground());
        } else {
            component.setForeground(getForeground());
            component.setBackground(rowColors[row % rowColors.length]);
        }

        if (column == 0) {
            component.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 1, columnSeperatorColor));
        } else {
            component.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, columnSeperatorColor));
        }

        return component;
    }

    /**
     * Sets the background for non-selected editor cells
     */
    @Override
    public Component prepareEditor(TableCellEditor renderer, int row, int column) {
        Component component = super.prepareEditor(renderer, row, column);
        if (!isCellSelected(row, column)) {
            component.setBackground(rowColors[row % rowColors.length]);
        }
        return component;
    }

    private class TreeTableRowSorter extends TableRowSorter<TreeTableModelAdapter<T>> {
        private final TreeTableModelInterface<T> modelInterfaced;

        public TreeTableRowSorter(TreeTableModelInterface<T> modelInterfaced, TreeTableModelAdapter<T> modelAdapter) {
            super(modelAdapter);
            this.modelInterfaced = modelInterfaced;
        }

        @Override
        protected boolean useToString(int column) {
            return false;
        }

        @Override
        public Comparator<TreeNodeValue<T>> getComparator(int column) {
            @SuppressWarnings("rawtypes")
            Comparator valueComparator;

            if (getModel().getColumnClass(column).equals(TreeNode.class)) {
                // Comperator for TreeNode (drop-down column) based on text
                valueComparator = (o1, o2) -> {
                    String s1 = o1.toString();
                    String s2 = o2.toString();
                    return s1.compareTo(s2);
                };
            } else {
                valueComparator = super.getComparator(column);
            }

            return new Comparator<TreeNodeValue<T>>() {
                @Override
                @SuppressWarnings("unchecked")
                public int compare(TreeNodeValue<T> container1, TreeNodeValue<T> container2) {
                    TreeNode<T> node1 = container1.node;
                    TreeNode<T> node2 = container2.node;
                    if (node1.getParent() == node2.getParent()) {
                        // Both rows have the same parent
                        // Just compare the value
                        return valueComparator.compare(container1.value, container2.value);
                    } else {
                        TreeNode<T> ancestor1 = node1;
                        TreeNode<T> ancestor2 = node2;
                        while (ancestor1.getLevel() > node2.getLevel()) {
                            ancestor1 = ancestor1.getParent();
                        }
                        while (ancestor2.getLevel() > node1.getLevel()) {
                            ancestor2 = ancestor2.getParent();
                        }
                        if (ancestor1 == node2) {
                            return getSortOrder(column) == SortOrder.DESCENDING ? -1 : +1;
                        } else if (ancestor2 == node1) {
                            return getSortOrder(column) == SortOrder.DESCENDING ? +1 : -1;
                        } else {
                            assert ancestor1.getLevel() == ancestor2.getLevel();
                            while (ancestor1.getParent() != ancestor2.getParent()) {
                                ancestor1 = ancestor1.getParent();
                                ancestor2 = ancestor2.getParent();
                            }
                            return valueComparator.compare(modelInterfaced.getValueAt(ancestor1, column), modelInterfaced.getValueAt(ancestor2, column));
                        }
                    }
                }
            };
        }

        private SortOrder getSortOrder(int column) {
            for (SortKey key : getSortKeys()) {
                if (key.getColumn() == column) {
                    return key.getSortOrder();
                }
            }
            return SortOrder.UNSORTED;
        }
    }

    private void setupContextMenu() {
        // Create
        contextMenu = new JPopupMenu();

        // Add click listener
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handleRowClick(e);
                if (e.isPopupTrigger() && isEnabled()) {
                    doPop(e);
                } else {
                    hidePop();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    doPop(e);
                }
            }

            private void handleRowClick(MouseEvent e) {
                ListSelectionModel selectionModel = getSelectionModel();
                contextMenuOpenedAt = e.getPoint();
                int clickedRow = rowAtPoint(contextMenuOpenedAt);

                if (clickedRow < 0) {
                    // No row selected
                    selectionModel.clearSelection();
                } else {
                    // Some row selected
                    if ((e.getModifiers() & InputEvent.SHIFT_MASK) == InputEvent.SHIFT_MASK) {
                        int maxSelect = selectionModel.getMaxSelectionIndex();

                        if ((e.getModifiers() & InputEvent.CTRL_MASK) == InputEvent.CTRL_MASK) {
                            // Shift + CTRL
                            selectionModel.addSelectionInterval(maxSelect, clickedRow);
                        } else {
                            // Shift
                            selectionModel.setSelectionInterval(maxSelect, clickedRow);
                        }
                    } else if ((e.getModifiers() & InputEvent.CTRL_MASK) == InputEvent.CTRL_MASK) {
                        // CTRL
                        selectionModel.addSelectionInterval(clickedRow, clickedRow);
                    } else {
                        // No modifier key pressed
                        selectionModel.setSelectionInterval(clickedRow, clickedRow);
                    }
                }
            }

            private void doPop(MouseEvent e) {
                if (getSelectedRowCount() == 0) {
                    return;
                }
                updateContextMenu();
                Stream.of(contextMenu.getComponents())
                      .filter(mi -> mi instanceof TreeContextMenuItem)
                      .map(mi -> (TreeContextMenuItem) mi)
                      .filter(tcmi -> tcmi.isVisible())
                      .forEach(tcmi -> tcmi.updateText());
                contextMenu.setVisible(true);
                contextMenu.show(e.getComponent(), e.getX(), e.getY());
            }

            private void hidePop() {
                contextMenu.setVisible(false);
                contextMenuOpenedAt = null;
            }
        });

        // Default entries
        copyElementContextMenuItem = registerContextMenuItem(() -> "Copy element", (e) -> {
            assert contextMenuOpenedAt != null;
            int row = rowAtPoint(contextMenuOpenedAt);
            int col = columnAtPoint(contextMenuOpenedAt);
            StringSelection toCopy = new StringSelection(getValueAt(row, col).toString());
            getToolkit().getSystemClipboard().setContents(toCopy, toCopy);
        });

        copyRowsContextMenuItem = registerContextMenuItem(() -> "Copy rows", (e) -> {
            StringBuilder sb = new StringBuilder();
            int[] rows = getSelectedRows();
            int[] cols = getCopyColumns();
            if (cols == null) {
                cols = new int[0];
            }

            // Return if no rows to copy
            if (rows.length == 0) {
                return;
            }

            // Header
            for (int col = 0; col < cols.length; col++) {
                sb.append(getColumnName(cols[col]));
                sb.append(",");
            }
            sb.deleteCharAt(sb.length() - 1);
            sb.append("\n");

            // Data
            for (int row = 0; row < rows.length; row++) {
                for (int col = 0; col < cols.length; col++) {
                    sb.append(getValueAt(rows[row], cols[col]).toString().replace(",", ";"));
                    sb.append(",");
                }
                // Remove trailing ","
                sb.deleteCharAt(sb.length() - 1);
                sb.append("\n");
            }

            StringSelection toCopy = new StringSelection(sb.toString());
            getToolkit().getSystemClipboard().setContents(toCopy, toCopy);
        });

        contextMenu.addSeparator();
    }

    private void updateContextMenu() {
        copyElementContextMenuItem.setVisible(supportCopyElement());
        copyRowsContextMenuItem.setVisible(supportCopyRows());
    }

    public void registerContextMenuItem(TreeContextMenuItem item) {
        contextMenu.add(item);
    }

    public TreeContextMenuItem registerContextMenuItem(Supplier<String> textSupplier, Consumer<MouseEvent> onClick) {
        TreeContextMenuItem menuItem = new TreeContextMenuItem(textSupplier, onClick);
        registerContextMenuItem(menuItem);
        return menuItem;
    }

    public Point getContextMenuOpenedAtLocation() {
        return contextMenuOpenedAt;
    }

    /**
     * Can be overwritten by subclasses to disable copying of single cells
     *
     * @return True if copying of single cells is enabled
     */
    protected boolean supportCopyElement() {
        return true;
    }

    /**
     * Can be overwritten by subclasses to disable copying of selected rows
     *
     * @return True if copying of selected rows is enabled
     */
    protected boolean supportCopyRows() {
        return true;
    }

    /**
     * Can be overwritten by subclasses to define which columns are copied when the "Copy row(s)" operation is executed
     *
     * @return The column's indices of all columns which should be copied
     */
    protected int[] getCopyColumns() {
        if (getColumnCount() > 0) {
            return IntStream.range(0, getColumnCount()).toArray();
        } else {
            return new int[0];
        }
    }

    public void setLoadingMode(boolean isLoading) {
        // Prevents context menu from showing up
        setEnabled(!isLoading);
    }

    public void resetSort(String key) {
        getRowSorter().setSortKeys(new ArrayList<SortKey>());
        for (int column = 0; column < getColumnCount(); column++) {
            if (getColumnName(column).equals(key)) {
                getRowSorter().setSortKeys(Collections.singletonList(new RowSorter.SortKey(column, SortOrder.DESCENDING)));
            }
        }
    }
}
