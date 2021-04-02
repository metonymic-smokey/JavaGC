
package at.jku.anttracks.gui.model;

public class ChartSelection implements Comparable<ChartSelection> {
    public int series;
    public int item;
    public String chartId;
    public double x;
    public double y;

    public ChartSelection(int series, int item, double x, double y, String chartId) {
        this.series = series;
        this.item = item;
        this.chartId = chartId;
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ChartSelection that = (ChartSelection) o;

        if (series != that.series) {
            return false;
        }
        if (item != that.item) {
            return false;
        }
        if (Double.compare(that.x, x) != 0) {
            return false;
        }
        if (Double.compare(that.y, y) != 0) {
            return false;
        }
        return chartId != null ? chartId.equals(that.chartId) : that.chartId == null;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = series;
        result = 31 * result + item;
        result = 31 * result + (chartId != null ? chartId.hashCode() : 0);
        temp = Double.doubleToLongBits(x);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(y);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    @Override
    public int compareTo(ChartSelection other) {
        return (int) Math.signum(other.x - this.x);
    }

    @Override
    public ChartSelection clone() {
        return new ChartSelection(series, item, x, y, chartId);
    }
}
