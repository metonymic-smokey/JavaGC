
package at.jku.anttracks.compiler;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

public class OnTheFlyCompilationClassLoader extends ClassLoader {

    /**
     * ClassName -> Byte code
     */
    Map<String, ByteCode> onTheFlyCompiledCode = new HashMap<>();

    public OnTheFlyCompilationClassLoader(ClassLoader parent) {
        super(parent);
    }

    public ByteCode getByteCodeStub(String className) {
        ByteCode empty;
        try {
            empty = new ByteCode(className);

            onTheFlyCompiledCode.put(className, empty);
            return empty;
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        ByteCode bc = onTheFlyCompiledCode.get(name);
        if (bc == null) {
            return super.findClass(name);
        }
        byte[] byteCode = bc.get();
        return defineClass(name, byteCode, 0, byteCode.length);
    }
}
