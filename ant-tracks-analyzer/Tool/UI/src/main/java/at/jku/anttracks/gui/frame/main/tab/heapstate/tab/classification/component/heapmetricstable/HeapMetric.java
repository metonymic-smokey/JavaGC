package at.jku.anttracks.gui.frame.main.tab.heapstate.tab.classification.component.heapmetricstable;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.text.DecimalFormat;
import java.text.NumberFormat;

public class HeapMetric {

    public static final NumberFormat formatter = new DecimalFormat("#,###.###");

    public final StringProperty labelProperty;
    public final DoubleProperty valueProperty;

    public HeapMetric(String label, Number value) {
        this.labelProperty = new SimpleStringProperty(label);
        this.valueProperty = new SimpleDoubleProperty(value.doubleValue());
    }

    public String toString() {
        return labelProperty.get() + ": " + formatter.format(valueProperty.get());
    }

}
