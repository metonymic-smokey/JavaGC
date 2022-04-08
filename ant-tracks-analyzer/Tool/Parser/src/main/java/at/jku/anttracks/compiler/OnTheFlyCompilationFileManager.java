
package at.jku.anttracks.compiler;

import javax.tools.*;
import javax.tools.JavaFileObject.Kind;
import java.io.IOException;

public class OnTheFlyCompilationFileManager extends ForwardingJavaFileManager<JavaFileManager> {

    OnTheFlyCompilationClassLoader cl = new OnTheFlyCompilationClassLoader(ClassLoader.getSystemClassLoader());

    protected OnTheFlyCompilationFileManager() {
        super(ToolProvider.getSystemJavaCompiler().getStandardFileManager(null, null, null));
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location, String className, Kind kind, FileObject sibling) throws IOException {
        return cl.getByteCodeStub(className);
    }

    @Override
    public ClassLoader getClassLoader(Location location) {
        return cl;
    }
}
