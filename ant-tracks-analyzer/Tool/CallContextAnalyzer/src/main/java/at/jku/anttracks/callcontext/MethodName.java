

package at.jku.anttracks.callcontext;

import javassist.CtBehavior;
import javassist.bytecode.MethodInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents the fully qualified name of a method. Instances of this class are immutable.
 *
 * @author Peter Feichtinger
 */
public class MethodName implements Comparable<MethodName> {

    public final String className;
    public final String methodName;
    public final String descriptor;
    private transient String toString;
    private transient int hash;

    /**
     * Create a new {@link MethodName} from the specified parts.
     *
     * @param className  The fully qualified name of the class that contains the method.
     * @param methodName The name of the method.
     * @param descriptor The type descriptor of the method.
     * @throws IllegalArgumentException if {@code descriptor} is not a valid method descriptor, or if {@code className} or {@code
     *                                  methodName} is empty.
     */
    public MethodName(String className, String methodName, String descriptor, Cache<String> cache) {
        this.className = cache == null ? className : cache.get(Objects.requireNonNull(className));
        this.methodName = cache == null ? methodName : cache.get(Objects.requireNonNull(methodName));
        this.descriptor = cache == null ? descriptor : cache.get(Objects.requireNonNull(descriptor));
        if (className.isEmpty() || methodName.isEmpty()) {
            throw new IllegalArgumentException("Class and method name cannot be empty.");
        }
        final int lastParen = descriptor.lastIndexOf(')');
        if (descriptor.charAt(0) != '(' || lastParen < 0 || lastParen == descriptor.length() - 1) {
            throw new IllegalArgumentException("Not a valid method descriptor.");
        }
    }

    /**
     * Create a new {@link MethodName} from the specified signature and indices.
     *
     * @param signature The signature.
     * @param dotIdx    Index of the dot separating class name and method name.
     * @param parenIdx  Index of the left parenthesis introducing the descriptor.
     */
    private MethodName(String signature, int dotIdx, int parenIdx) {
        className = signature.substring(0, dotIdx);
        methodName = signature.substring(dotIdx + 1, parenIdx);
        descriptor = signature.substring(parenIdx);
        toString = signature;
    }

    /**
     * Determine whether this name refers to a constructor.
     *
     * @return {@code true} if the name of the method has the value of {@link MethodInfo#nameInit}.
     */
    public boolean isConstructor() {
        return methodName.equals(MethodInfo.nameInit);
    }

    /**
     * Determine whether this name refers to a static initializer.
     *
     * @return {@code true} if the name of the method has the value of {@link MethodInfo#nameClinit}.
     */
    public boolean isStaticInitializer() {
        return methodName.equals(MethodInfo.nameClinit);
    }

    /**
     * Test whether this name is signature-identical with the specified name.
     *
     * @param other The name to test.
     * @return {@code true} if both methods have the same name and parameter types.
     */
    public boolean signatureIdentical(MethodName other) {
        if (!methodName.equals(other.methodName)) {
            return false;
        }
        return descriptor.regionMatches(0, other.descriptor, 0, descriptor.lastIndexOf(')') + 1);
    }

    /**
     * Get the parameter types of the method this name refers to.
     *
     * @return A list of descriptors for the parameter types.
     */
    public List<String> getParameterTypes() {
        if (descriptor.charAt(1) == ')') {
            return new ArrayList<>();
        }
        final List<String> params = new ArrayList<>(4);
        for (int idx = 1; descriptor.charAt(idx) != ')'; idx++) {
            final int start = idx;
            if (descriptor.charAt(idx) == '[') {
                do {
                    idx++;
                } while (descriptor.charAt(idx) == '[');
            }
            if (descriptor.charAt(idx) == 'L') {
                idx = descriptor.indexOf(';', idx + 1);
                assert idx > 0;
            }
            params.add(descriptor.substring(start, idx + 1));
        }
        return params;
    }

    /**
     * Get the return type of the method this name refers to.
     *
     * @return The descriptor of the return type.
     */
    public String getReturnType() {
        return descriptor.substring(descriptor.lastIndexOf(')') + 1);
    }

    /**
     * Get the number of parameters of the method this name refers to.
     *
     * @return The number of parameters.
     */
    public int getParameterCount() {
        int count = 0;
        for (int idx = 1; descriptor.charAt(idx) != ')'; idx++) {
            if (descriptor.charAt(idx) == '[') {
                do {
                    idx++;
                } while (descriptor.charAt(idx) == '[');
            }
            if (descriptor.charAt(idx) == 'L') {
                idx = descriptor.indexOf(';', idx + 1);
                assert idx > 0;
            }
            count++;
        }
        return count;
    }

    /**
     * Get the number of stack slots that will be occupied by the parameters of this method.
     *
     * @return The number of stack slots occupied by all parameters.
     */
    public int getCallStackSize() {
        int count = 0;
        for (int idx = 1; descriptor.charAt(idx) != ')'; idx++) {
            if (descriptor.charAt(idx) == 'J' || descriptor.charAt(idx) == 'D') {
                count += 2;
            } else {
                while (descriptor.charAt(idx) == '[') {
                    idx++;
                }

                if (descriptor.charAt(idx) == 'L') {
                    idx = descriptor.indexOf(';', idx + 1);
                    assert idx > 0;
                }
                count++;
            }
        }
        return count;
    }

    @Override
    public int hashCode() {
        int result = hash;
        if (result == 0) {
            final int prime = 31;
            result = 1;
            result = prime * result + className.hashCode();
            result = prime * result + methodName.hashCode();
            result = prime * result + descriptor.hashCode();
            hash = result;
        }
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        MethodName other = (MethodName) obj;
        if (!className.equals(other.className)) {
            return false;
        }
        if (!methodName.equals(other.methodName)) {
            return false;
        }
        if (!descriptor.equals(other.descriptor)) {
            return false;
        }
        return true;
    }

    /**
     * Test whether the specified string and this name describe the same method. This is a convenience method for
     * {@code toString().equals(s)}.
     *
     * @param s A fully qualified name of a method.
     * @return {@code true} if this and {@code s} name the same method.
     */
    public boolean equals(String s) {
        return toString().equals(s);
    }

    /**
     * Determine whether this method name and the specified parts refer to the same method. This is semantically equivalent to
     * {@code this.equals(new MethodName(className, methodName, descriptor))}, except that no exception is thrown if any of the values is
     * {@code null} or otherwise invalid, and that no {@code MethodName} instance is created.
     *
     * @param className  The class name.
     * @param methodName The name of the method.
     * @param descriptor The descriptor of the method.
     * @return {@code true} if class name, method name and descriptor of this method name match the specified strings.
     */
    @SuppressWarnings("hiding")
    public boolean equals(String className, String methodName, String descriptor) {
        return this.methodName.equals(methodName) && this.className.equals(className) && this.descriptor.equals(descriptor);
    }

    @Override
    public int compareTo(MethodName o) {
        if (this == o) {
            return 0;
        }
        int tmp = className.compareTo(o.className);
        if (tmp == 0) {
            tmp = methodName.compareTo(o.methodName);
            if (tmp == 0) {
                tmp = descriptor.compareTo(o.descriptor);
            }
        }
        return tmp;
    }

    @Override
    public String toString() {
        if (toString == null) {
            toString = className + '.' + methodName + descriptor;
        }
        return toString;
    }

    /**
     * Get a {@link MethodName} for the specified method or constructor.
     *
     * @param method The {@link CtBehavior} to get a name for.
     * @return The method name.
     */
    public static MethodName create(CtBehavior method, Cache<String> cache) {
        return new MethodName(method.getDeclaringClass().getName(), method.getMethodInfo().getName(), method.getSignature(), cache);
    }

    /**
     * Get a {@link MethodName} from the specified signature.
     *
     * @param signature A complete method signature, including the class name, method name, and method type descriptor.
     * @return A method name for the specified signature.
     * @throws IllegalArgumentException If {@code signature} is not a valid method signature.
     */
    public static MethodName create(String signature) {
        final MethodName result = tryCreate(signature);
        if (result == null) {
            throw new IllegalArgumentException("Not a valid method signature: " + signature);
        }
        return result;
    }

    /**
     * Try to create a {@link MethodName} from the specified signature.
     *
     * @param signature A complete method signature, including the class name, method name, and method type descriptor.
     * @return A method name for the specified signature, or {@code null} if {@code signature} is not a valid method signature.
     */
    public static MethodName tryCreate(String signature) {
        final int parenIdx = signature.indexOf('(');
        final int dotIdx = signature.lastIndexOf('.', parenIdx);
        if (parenIdx < 0 || dotIdx <= 0) {
            // dotIdx <= 0: className.isEmpty()
            return null;
        }
        if (dotIdx + 1 == parenIdx) {
            // methodName.isEmpty()
            return null;
        }
        final int lastParen = signature.lastIndexOf(')');
        if (lastParen < 0 || lastParen == signature.length() - 1 || lastParen < parenIdx) {
            return null;
        }
        return new MethodName(signature, dotIdx, parenIdx);
    }

    /**
     * Test whether the specified string contains a valid method name.
     *
     * @param signature The string to test.
     * @return {@code true} if {@code signature} can be passed to {@link #create(String)} without an exception being thrown.
     */
    public static boolean test(String signature) {
        final int parenIdx = signature.indexOf('(');
        final int dotIdx = signature.lastIndexOf('.', parenIdx);
        if (parenIdx < 0 || dotIdx <= 0) {
            // dotIdx <= 0: className.isEmpty()
            return false;
        }
        if (dotIdx + 1 == parenIdx) {
            // methodName.isEmpty()
            return false;
        }
        final int lastParen = signature.lastIndexOf(')');
        if (lastParen < 0 || lastParen == signature.length() - 1 || lastParen < parenIdx) {
            return false;
        }
        return true;
    }
}