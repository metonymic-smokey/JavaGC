
package at.jku.anttracks.gui.frame.main.tab.heapvisualization.component;

import at.jku.anttracks.gui.frame.main.tab.heapvisualization.component.listener.HeapPanelEvent;
import at.jku.anttracks.gui.frame.main.tab.heapvisualization.component.listener.HeapPanelListener;
import at.jku.anttracks.gui.frame.main.tab.heapvisualization.model.HeapStateObjectInfo;
import at.jku.anttracks.gui.frame.main.tab.heapvisualization.model.VisualizationModel;
import at.jku.anttracks.util.SignatureConverter;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

/**
 * @author Christina Rammerstorfer
 */
public class ObjectInfoPanel extends JPanel implements HeapPanelListener {
    private static final long serialVersionUID = 1L;
    public static final int HEIGHT = 80, WIDTH = 70 + 350;
    private final VisualizationModel model;
    private final JTextArea text;

    public ObjectInfoPanel(VisualizationModel model) {
        this.model = model;
        setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
        text = new JTextArea();
        text.setEditable(false);
        text.setTabSize(1);
        JScrollPane scrollPane = new JScrollPane(text);
        scrollPane.setViewportBorder(null);
        scrollPane.setBorder(null);
        scrollPane.setMaximumSize(new Dimension((int) scrollPane.getMaximumSize().getWidth(), HEIGHT));
        scrollPane.setPreferredSize(new Dimension((int) scrollPane.getPreferredSize().getWidth(), HEIGHT));
        add(scrollPane);
    }

    @Override
    public void mouseMoved(HeapPanelEvent evt) {}

    @Override
    public void mouseClicked(HeapPanelEvent evt) {
        HeapStateObjectInfo[] curInfo = model.getCurrentPixelMap().getDetailedObjInfo(evt.x, evt.y);
        if (curInfo != null && curInfo.length > 0) {
            StringBuilder text = new StringBuilder("Showing " + curInfo.length + " object");
            if (curInfo.length > 1) {
                text.append("s");
            }
            text.append(" of " + model.getCurrentPixelMap().getNumberOfObjectsAtPixel(evt.x, evt.y));
            text.append(" at pixel (" + evt.x + ", " + evt.y + ")\n");
            int obj = 1;
            for (HeapStateObjectInfo d : curInfo) {
                if (d != null) {
                    text.append("#" + obj + "\t0x" + String.format("%X", d.address));
                    String type = d.type == null ? "" : SignatureConverter.convertToJavaType(d.type.toString(), false);
                    text.append(";\n \t\ttype: " + type + "\n");
                    text.append("\t\tallocation site:\n");
                    if (d.allocationSite != null) {
                        for (String s : Arrays.stream(d.allocationSite.getCallSites())
                                              .map(callSite -> SignatureConverter.convertToJavaMethodSignature(callSite.getSignature(), false) + " : " + callSite.getBci())
                                              .toArray(String[]::new)) {
                            text.append("\t\t\t");
                            text.append(s);
                            text.append("\n");
                        }
                    }
                    text.append("\t\tsize: " + d.size + "B;\n");
                    text.append("\t\tclassifications: ");
                    text.append(d.classifications + "\n");
                    obj++;
                }
            }
            this.text.setText(text.toString());
            this.text.setCaretPosition(0);
        }
        repaint();
        revalidate();
    }

    public void reset() {
        text.setText(null);
    }

}
