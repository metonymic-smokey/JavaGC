
package at.jku.anttracks.compiler;

import javax.tools.*;
import java.io.IOException;
import java.io.Writer;
import java.net.URISyntaxException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OnTheFlyCompiler {
    static JavaCompiler javac = ToolProvider.getSystemJavaCompiler();

    public String extractPackage(String sourceCode) {
        Pattern classNamePattern = Pattern.compile("package\\s+([a-zA_Z_][\\.\\w]*);");
        Matcher classNameMatcher = classNamePattern.matcher(sourceCode);
        if (!classNameMatcher.find()) {
            return null;
        }
        return classNameMatcher.group(1);
    }

    public String extractClassName(String sourceCode) {
        Pattern classNamePattern = Pattern.compile("class\\s+(\\w*)");
        Matcher classNameMatcher = classNamePattern.matcher(sourceCode);
        if (!classNameMatcher.find()) {
            return null;
        }
        return classNameMatcher.group(1);
    }

    public Class<?> compile(String sourceCode) throws IllegalArgumentException {
        // Extract class name
        String packageName = extractPackage(sourceCode);
        if (packageName == null) {
            packageName = "";
        }
        String className = extractClassName(sourceCode);
        if (className == null) {
            throw new IllegalArgumentException("Source code does not contain a class definition");
        }
        if (packageName.length() > 0) {
            className = packageName + "." + className;
        }

        Source source;
        try {
            source = new Source(className, sourceCode);
        } catch (URISyntaxException e) {
            return null;
        }

        StringBuilder errorMessage = new StringBuilder();
        JavaFileManager fileManager = new OnTheFlyCompilationFileManager();
        DiagnosticListener<? super JavaFileObject> diagnosticListener = null;
        List<String> options = null;
        List<String> classes = null;
        // May throw RuntimeException on error
        JavaCompiler.CompilationTask task = javac.getTask(new Writer() {

            @Override
            public void write(char[] cbuf, int off, int len) throws IOException {
                for (int i = 0; i < len; i++) {
                    errorMessage.append(cbuf[i + off]);
                }
            }

            @Override
            public void flush() throws IOException {
                // TODO Auto-generated method stub
            }

            @Override
            public void close() throws IOException {
                // TODO Auto-generated method stub
            }

        }, fileManager, diagnosticListener, options, classes, source.singletonList());
        boolean result = task.call();
        if (!result) {
            throw new IllegalArgumentException(errorMessage.toString());
        }
        try {
            return fileManager.getClassLoader(null).loadClass(className);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }
}
