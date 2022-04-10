
package at.jku.anttracks.compiler;

import javax.tools.SimpleJavaFileObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;

public class ByteCode extends SimpleJavaFileObject {

    private final ByteArrayOutputStream memoryOutputStream;

    public ByteCode(String className) throws URISyntaxException {
        super(new URI(className), Kind.CLASS);
        memoryOutputStream = new ByteArrayOutputStream();
    }

    @Override
    public OutputStream openOutputStream() throws IOException {
        return memoryOutputStream;
    }

    public byte[] get() {
        return memoryOutputStream.toByteArray();
    }
}
