
package at.jku.anttracks.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StreamUtil {

    public static long copy(InputStream in, OutputStream out) throws IOException {
        long size = 0;
        byte[] buffer = new byte[1024 * 8];
        for (int nRead = in.read(buffer); nRead > 0; nRead = in.read(buffer)) {
            out.write(buffer, 0, nRead);
            size += nRead;
        }
        return size;
    }

    private StreamUtil() {
        throw new Error("Do not instantiate!");
    }

}
