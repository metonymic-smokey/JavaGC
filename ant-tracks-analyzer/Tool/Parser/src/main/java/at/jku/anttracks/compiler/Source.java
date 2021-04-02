
package at.jku.anttracks.compiler;

import javax.tools.SimpleJavaFileObject;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;

public class Source extends SimpleJavaFileObject {
    private final String sourceCode;

    public Source(String className, String sourceCode) throws URISyntaxException {
        super(new URI(className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
        this.sourceCode = sourceCode;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
        return sourceCode;
    }

    public List<Source> singletonList() {
        return Collections.singletonList(this);
    }
}
