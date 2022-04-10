
package at.jku.anttracks.util;

import java.io.IOException;

@FunctionalInterface
public interface IORunnable {
    public abstract void run() throws IOException;
}
