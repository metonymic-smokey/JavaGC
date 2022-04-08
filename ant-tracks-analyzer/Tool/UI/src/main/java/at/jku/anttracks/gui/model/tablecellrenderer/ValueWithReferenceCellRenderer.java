
package at.jku.anttracks.gui.model.tablecellrenderer;

import at.jku.anttracks.gui.model.ValueWithReference;

import java.text.DecimalFormat;

public class ValueWithReferenceCellRenderer extends BarCellRenderer {
    private static final long serialVersionUID = 2805711811767537371L;

    private static final DecimalFormat format = new DecimalFormat("###,###.#");

    @Override
    String defineTextToShow(Object value) {
        return format.format(((ValueWithReference) value).getValue());
    }

    @Override
    double defineFillRatio(Object value) {
        return ((ValueWithReference) value).getPercentage().getValue() / 100.0;
    }
}
