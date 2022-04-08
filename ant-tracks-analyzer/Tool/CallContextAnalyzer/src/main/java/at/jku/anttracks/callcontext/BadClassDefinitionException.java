

package at.jku.anttracks.callcontext;

/**
 * Exception thrown to indicate that a class definition could not be successfully parsed from a data buffer.
 *
 * @author Peter Feichtinger
 */
public class BadClassDefinitionException extends RuntimeException {

    private static final long serialVersionUID = -6630507179906532418L;

    private final byte[] mData;

    /**
     * Constructs a new {@link BadClassDefinitionException} with the specified data buffer, detail message, and cause.
     * <p>
     * Note that the detail message associated with {@code cause} is <i>not</i> automatically incorporated in this exception's detail
     * message.
     *
     * @param data    The class definition data that caused the exception to be thrown, may be {@code null}.
     * @param message The detail message (which is saved for later retrieval by {@link #getMessage()}).
     * @param cause   The cause (which is saved for later retrieval by {@link #getCause()}). (A {@code null} value is permitted, and
     *                indicates
     *                that the cause is nonexistent or unknown.)
     */
    public BadClassDefinitionException(byte[] data, String message, Throwable cause) {
        super(message, cause);
        mData = clone(data);
    }

    /**
     * Constructs a new {@link BadClassDefinitionException} with the specified data buffer and detail message. The cause is not initialized,
     * and may subsequently be initialized by a call to {@link #initCause}.
     *
     * @param data    The class definition data that caused the exception to be thrown, may be {@code null}.
     * @param message The detail message. The detail message is saved for later retrieval by the {@link #getMessage()} method.
     */
    public BadClassDefinitionException(byte[] data, String message) {
        super(message);
        mData = clone(data);
    }

    /**
     * Constructs a new {@link BadClassDefinitionException} with the specified data buffer, cause, and a detail message of
     * {@code (cause==null ? null : cause.toString())} (which typically contains the class and detail message of {@code cause}). This
     * constructor is useful for exceptions that are little more than wrappers for other throwables.
     *
     * @param data  The class definition data that caused the exception to be thrown, may be {@code null}.
     * @param cause The cause (which is saved for later retrieval by the {@link #getCause()} method). (A {@code null} value is permitted,
     *              and
     *              indicates that the cause is nonexistent or unknown.)
     */
    public BadClassDefinitionException(byte[] data, Throwable cause) {
        super("Failed to create class from byte array.", cause);
        mData = clone(data);
    }

    /**
     * Get the class file data that caused this exception to be thrown. Note that the data array is returned by reference, so it should not
     * be changed unless the original data is not needed any more.
     *
     * @return The class file data by reference, or {@code null} if none was set.
     */
    public byte[] getData() {
        return mData;
    }

    /**
     * Clone the specified byte array.
     *
     * @param data The array to copy, or {@code null}.
     * @return A copy of {@code data}, or {@code null} if {@code data} is {@code null}.
     */
    private static byte[] clone(byte[] data) {
        return (data != null ? data.clone() : null);
    }
}
