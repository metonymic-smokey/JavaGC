
package at.jku.anttracks.gui.utils;

import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.regex.Pattern;

public class ScaledNumberFormat extends NumberFormat {
    private static final long serialVersionUID = 1L;

    private static final String[] METRIC_PREFIXES = new String[]{"", "k", "M", "G", "T"};

    private static final Integer MAX_LENGTH = 4;

    private static final Pattern TRAILING_DECIMAL_POINT = Pattern.compile("[0-9]+\\.[kMGT]");

    private static final Pattern METRIC_PREFIXED_NUMBER = Pattern.compile("\\-?[0-9]+(\\.[0-9])?[kMGT]");

    @Override
    public StringBuffer format(double number, StringBuffer output, FieldPosition pos) {
        boolean isNegative = number < 0;
        number = Math.abs(number);

        String result = new DecimalFormat("##0E0").format(number);

        Integer index = Character.getNumericValue(result.charAt(result.length() - 1)) / 3;
        result = result.replaceAll("E[0-9]", METRIC_PREFIXES[index]);

        while (result.length() > MAX_LENGTH || TRAILING_DECIMAL_POINT.matcher(result).matches()) {
            int length = result.length();
            result = result.substring(0, length - 2) + result.substring(length - 1);
        }

        return output.append(isNegative ? "-" + result : result);
    }

    @Override
    public StringBuffer format(long number, StringBuffer output, FieldPosition pos) {
        return this.format(number, output, pos);
    }

    @Override
    public Number parse(String source, ParsePosition pos) {
        try {
            double val = Double.valueOf(source);
            pos.setIndex(source.length());
            return val;

        } catch (NumberFormatException ex) {
            if (METRIC_PREFIXED_NUMBER.matcher(source).matches()) {

                boolean isNegative = source.charAt(0) == '-';
                int length = source.length();

                String number = isNegative ? source.substring(1, length - 1) : source.substring(0, length - 1);
                String metricPrefix = Character.toString(source.charAt(length - 1));

                Number absoluteNumber = Double.valueOf(number);

                int index = 0;

                for (; index < METRIC_PREFIXES.length; index++) {
                    if (METRIC_PREFIXES[index].equals(metricPrefix)) {
                        break;
                    }
                }

                Integer exponent = 3 * index;
                Double factor = Math.pow(10, exponent);
                factor *= isNegative ? -1 : 1;

                pos.setIndex(source.length());
                Float result = absoluteNumber.floatValue() * factor.longValue();
                return result.longValue();
            }
        }

        return null;
    }
}
