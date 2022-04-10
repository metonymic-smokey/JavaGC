
package at.jku.anttracks.util;

import java.util.Objects;

public class TraceException extends Exception {

    /**
     * A {@link RuntimeException} that wraps a {@link TraceException} so it can be thrown from inside a lambda. Exceptions of this type
     * should be caught and {@linkplain #rethrow() rethrown} as soon as possible.
     *
     * @author Peter Feichtinger
     */
    public static class TraceExceptionLambdaWrapper extends RuntimeException {
        private static final long serialVersionUID = 1L;

        /**
         * Create a {@link TraceExceptionLambdaWrapper} from the specified exception and throw it.
         *
         * @param ex The {@link TraceException} to wrap.
         * @throws TraceExceptionLambdaWrapper Always.
         */
        public static void boom(TraceException ex) {
            throw new TraceExceptionLambdaWrapper(ex);
        }

        /**
         * Create a {@link TraceExceptionLambdaWrapper} from the specified {@link TraceException}.
         *
         * @param inner The exception to wrap.
         */
        public TraceExceptionLambdaWrapper(TraceException inner) {
            super(Objects.requireNonNull(inner));
        }

        /**
         * Rethrow the wrapped {@link TraceException}.
         *
         * @throws TraceException Always.
         */
        public void rethrow() throws TraceException {
            throw (TraceException) getCause();
        }
    }

    private static final long serialVersionUID = 1L;

    public TraceException() {
        super();
    }

    public TraceException(String message) {
        super(message);
    }

    public TraceException(Throwable cause) {
        super(cause);
    }

}
