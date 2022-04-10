
package at.jku.anttracks.gui.model;

import at.jku.anttracks.gui.chart.base.ApplicationChartFactory;
import at.jku.anttracks.heap.statistics.MemoryConsumption;
import org.jetbrains.annotations.NotNull;

public class Size {
    public double objects;
    public double bytes;

    public Size() {
        this.objects = 0;
        this.bytes = 0;
    }

    public Size(double objects, double bytes) {
        this.objects = objects;
        this.bytes = bytes;
    }

    public Size(Size other) {
        this.objects = other.objects;
        this.bytes = other.bytes;
    }

    public static Size of(MemoryConsumption mc) {
        return new Size(mc.getObjects(), mc.getBytes());
    }

    public void set(double val, ApplicationChartFactory.MemoryConsumptionUnit unit) {
        switch (unit) {
            case OBJECTS:
                objects = val;
                break;

            case BYTES:
                bytes = val;
                break;
        }
    }

    public double get(ApplicationChartFactory.MemoryConsumptionUnit unit) {
        switch (unit) {
            case OBJECTS:
                return objects;

            case BYTES:
                return bytes;
        }

        throw new IllegalStateException("switch is not exhaustive!");
    }

    public void inc(double bytesToAdd) {
        objects++;
        bytes += bytesToAdd;
    }

    @NotNull
    public static Size add(
            @NotNull
                    Size size1,
            @NotNull
                    Size size2) {
        return new Size(size1.objects + size2.objects, size1.bytes + size2.bytes);
    }

    @NotNull
    public static Size sub(
            @NotNull
                    Size size1,
            @NotNull
                    Size size2) {
        return new Size(size1.objects - size2.objects, size1.bytes - size2.bytes);
    }

    @NotNull
    public static Size divide(
            @NotNull
                    Size size1,
            @NotNull
                    Size size2) {
        return new Size(size1.objects / size2.objects, size1.bytes / size2.bytes);
    }
}
