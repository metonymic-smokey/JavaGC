
package at.jku.anttracks.parser.main;

import at.jku.anttracks.parser.TraceParserListener;

import java.util.logging.Level;
import java.util.logging.Logger;

public class ProgressReporter implements TraceParserListener {

    private static final Logger LOGGER = Logger.getLogger(ProgressReporter.class.getSimpleName());

    private final long interval;
    private long next;

    public ProgressReporter(long interval) {
        this.interval = interval;
        next = 0;
    }

    @Override
    public void report(long from, long to, long position) {
        if (next < System.currentTimeMillis()) {
            double progress = 1.0 * (position - from) / (to - from);
            LOGGER.log(Level.INFO, () -> String.format("parsed %.2f%%", progress * 100));
            next = System.currentTimeMillis() + interval;
        }
    }

}
