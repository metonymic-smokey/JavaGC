
package at.jku.anttracks.gui.utils;

import at.jku.anttracks.util.Tuple;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class ProgressCalc {

    private static final class Unit {
        public final String name, multiName;
        public final long factor;

        public Unit(String name, long factor) {
            this(name, null, factor);
        }

        public Unit(String name, String multiName, long factor) {
            this.name = name;
            this.multiName = multiName;
            this.factor = factor;
        }

        public String getName(long count) {
            return count == 1 ? name : (multiName != null ? multiName : (name + "s"));
        }
    }

    private static final Unit[] UNITS = new Unit[]{new Unit("millisecond", 1000),
                                                   new Unit("second", 60),
                                                   new Unit("minute", 60),
                                                   new Unit("hour", 24),
                                                   new Unit("day", 7),
                                                   new Unit("week", 4),
                                                   new Unit("month", 12),
                                                   new Unit("year", 1000),
                                                   new Unit("millenium", "millenia", 14_000_000),
                                                   new Unit("universe lifetime", 0),};

    private static final long MIN_UNIT_INDEX = 1;

    private int MAX_PROGRESS_ENTRIES = 10_000;
    // Key = Time, Value = Progress
    private List<Tuple<Double, Double>> progressUpdates = new ArrayList<>();

    private static final Logger LOGGER = Logger.getLogger(ProgressCalc.class.getSimpleName());

    private long lastProgressReportTime;
    SimpleDoubleProperty progressProperty;

    private static final long REMAINING_TIME_CALCULATION_INTERVAL = 5;
    private static final long REMAINING_TIME_TEXT_CALCULATION_INTERVAL = 200;
    private long lastRemainingTimeTextUpdateTime;
    private long remainingTime;

    private long lastLogTime;
    private static long LOG_INTERVAL = 100;
    private StringBuilder log = new StringBuilder();

    public SimpleStringProperty remainingTimeTextProperty = new SimpleStringProperty("Unknown time");
    public SimpleStringProperty percentTextProperty = new SimpleStringProperty("0");

    boolean first = true;
    long firstTime;

    Timeline decreasingTimeline = new Timeline();

    public ProgressCalc() {
        this(null);
    }

    public ProgressCalc(ReadOnlyDoubleProperty progressPropertyToHook) {
        lastProgressReportTime = time();
        lastLogTime = time();

        lastRemainingTimeTextUpdateTime = 0;

        this.progressProperty = new SimpleDoubleProperty(0);

        remainingTime = -1;

        decreasingTimeline.getKeyFrames().add(new KeyFrame(Duration.seconds(1), actionEvent -> {
            if (remainingTime != -1) {
                remainingTime -= 1000;
                if (remainingTime < 0) {
                    remainingTime = 0;
                }
                updateText();
            }
        }));
        decreasingTimeline.setCycleCount(Timeline.INDEFINITE);
        decreasingTimeline.play();

        // Auto-update if provided property changes
        if (progressPropertyToHook != null) {
            progressPropertyToHook.addListener((observable, oldValue, newValue) -> update((double) newValue));
        }
    }

    public synchronized void update(double progress) {
        if (first) {
            firstTime = time();
            first = false;
        }

        // Time since last update / constructor
        long deltaT = time() - lastProgressReportTime;

        // Ignore update that come in less than the given intervals
        if (deltaT < REMAINING_TIME_CALCULATION_INTERVAL) {
            return;
        }

        // Calculate current speed
        double distanceChange = progress - this.progressProperty.get();
        double currentSpeed = 1.0 * distanceChange / deltaT;

        progressUpdates.add(0, new Tuple<>(time() * 1.0, progress));
        if (progressUpdates.size() > MAX_PROGRESS_ENTRIES) {
            progressUpdates.remove(progressUpdates.size() - 1);
        }
        // TODO currently we do not use something like moving average, but the overall time
        // Include some more intelligent measure
        Tuple<Double, Double> newOne = progressUpdates.get(0);
        Tuple<Double, Double> oldOne = progressUpdates.get(progressUpdates.size() - 1);
        double s = newOne.b - oldOne.b;
        double t = newOne.a - oldOne.a;
        double v = s / t;

        // Update remaining time
        // s = d / t => s * t = d => t = d / s
        double remainingDistance = 1 - progress;
        remainingTime = (long) (1.0 * remainingDistance / v);
        percentTextProperty.set(String.format("%,.2f", formatPercentage(progress)));

        if (lastRemainingTimeTextUpdateTime + REMAINING_TIME_TEXT_CALCULATION_INTERVAL < time()) {
            updateText();
            lastRemainingTimeTextUpdateTime = time();
        }

        log.append(String.format("\nTime since last update: \t %,d ms\n" + "Distance change: \t\t %,.6f p (%,f - %,f)\n" + "Current speed: \t\t\t %,.6f p/ms\n" +
                                         "Average Speed: " +
                                         "\t\t\t %,.6f p/ms\n" + "Remaining Distance: \t %,.6f p\n" + "Remaining Time: \t\t %,d ms\n",
                                 deltaT,
                                 distanceChange,
                                 progress,
                                 this.progressProperty.get(),
                                 currentSpeed,
                                 v,
                                 remainingDistance,
                                 remainingTime));

        if (time() - lastLogTime >= LOG_INTERVAL) {
            LOGGER.finest(log.toString());
            lastLogTime = time();
            log = new StringBuilder();
        }

        // Update percentage progress
        this.progressProperty.set(progress);

        lastProgressReportTime = time();
    }

    private void updateText() {
        remainingTimeTextProperty.set(formatTime(remainingTime) + String.format(" remaining (Running for " + formatTime(time() - firstTime) + ")"));
    }

    private static double formatPercentage(double value) {
        return ((double) (long) (value * 10000)) / 100;
    }

    private static String formatTime(long millis) {
        if (millis < 0) {
            return "unknown time";
        }

        long[] values = new long[UNITS.length];

        values[0] = millis;
        for (int i = 1; i < UNITS.length; i++) {
            values[i] = values[i - 1] / UNITS[i - 1].factor;
        }

        for (int i = 0; i < UNITS.length - 1; i++) {
            values[i] = values[i] % UNITS[i].factor;
        }

        StringBuilder result = new StringBuilder();
        for (int i = UNITS.length - 1; i >= MIN_UNIT_INDEX; i--) {
            long value = values[i];
            if (value != 0) {
                result.append(values[i] + " " + UNITS[i].getName(value) + " ");
            }
        }
        if (result.length() == 0) {
            result.append("only a few seconds");
        }
        return result.toString().trim();
    }

    private static long time() {
        return System.currentTimeMillis();
    }

    public void close() {
    }
}
