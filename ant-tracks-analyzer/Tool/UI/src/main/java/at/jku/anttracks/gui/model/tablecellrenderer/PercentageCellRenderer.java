
package at.jku.anttracks.gui.model.tablecellrenderer;

import at.jku.anttracks.gui.model.Percentage;

import java.text.DecimalFormat;

public class PercentageCellRenderer extends BarCellRenderer {

    private static final long serialVersionUID = 2824618201181802033L;

    private static final DecimalFormat format = new DecimalFormat("###,##0.0");

    @Override
    String defineTextToShow(Object value) {
        return format.format(((Percentage) value).getValue());
    }

    @Override
    double defineFillRatio(Object value) {
        return ((Percentage) value).getValue() / 100.0;
    }
}
