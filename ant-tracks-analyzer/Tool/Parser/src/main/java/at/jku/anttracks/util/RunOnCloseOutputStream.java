
package at.jku.anttracks.util;

import java.io.IOException;
import java.io.OutputStream;

public final class RunOnCloseOutputStream extends OutputStream {

    private final OutputStream out;
    private final IORunnable[] runnables;

    public RunOnCloseOutputStream(OutputStream out, IORunnable... runnables) {
        this.out = out;
        this.runnables = runnables;
    }

    @Override
    public void write(int value) throws IOException {
        out.write(value);
    }

    @Override
    public void write(byte[] data, int offset, int length) throws IOException {
        out.write(data, offset, length);
    }

    @Override
    public void flush() throws IOException {
        out.flush();
    }

    @Override
    public void close() throws IOException {
        out.close();
        for (IORunnable runnable : runnables) {
            runnable.run();
        }
    }

}
