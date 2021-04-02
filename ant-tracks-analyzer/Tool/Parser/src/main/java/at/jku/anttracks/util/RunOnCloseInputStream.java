
package at.jku.anttracks.util;

import java.io.IOException;
import java.io.InputStream;

public final class RunOnCloseInputStream extends InputStream {

    private final InputStream in;
    private final IORunnable[] runnables;

    public RunOnCloseInputStream(InputStream in, IORunnable... runnables) {
        this.in = in;
        this.runnables = runnables;
    }

    @Override
    public int read() throws IOException {
        return in.read();
    }

    @Override
    public int read(byte[] data, int offset, int length) throws IOException {
        return in.read(data, offset, length);
    }

    @Override
    public void close() throws IOException {
        in.close();
        for (IORunnable runnable : runnables) {
            runnable.run();
        }
    }
}
