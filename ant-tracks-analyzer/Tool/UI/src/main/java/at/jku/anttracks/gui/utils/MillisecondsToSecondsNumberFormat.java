
package at.jku.anttracks.gui.utils;

import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.ParsePosition;

public class MillisecondsToSecondsNumberFormat extends NumberFormat {

    private static final long serialVersionUID = -7953011557311145229L;

    double relation = 1000.0;

    @Override
    public StringBuffer format(double number, StringBuffer toAppendTo, FieldPosition pos) {
        toAppendTo.append(String.format("%.2f", number / this.relation));
        return toAppendTo;
    }

    @Override
    public StringBuffer format(long number, StringBuffer toAppendTo, FieldPosition pos) {
        toAppendTo.append(String.format("%.2f", number / this.relation));
        return toAppendTo;
    }

    @Override
    public Number parse(String source, ParsePosition parsePosition) {
        return Double.parseDouble(source) * this.relation;
    }

}
