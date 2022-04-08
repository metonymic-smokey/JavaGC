
package at.jku.anttracks.util;

import java.lang.reflect.Array;
import java.util.Arrays;

public class ArraysUtil {

    /**
     * Get the last element in the specified array.
     *
     * @param array The array.
     * @return The last element in the array.
     * @throws ArrayIndexOutOfBoundsException If the array has length 0.
     */
    public static <T> T last(T[] array) {
        return array[array.length - 1];
    }

    public static <T> T[] insert(Class<T> clazz, T[] array, T[] insertee, int pos) {
        T[] result = newInstance(clazz, array.length + insertee.length);
        System.arraycopy(array, 0, result, 0, pos);
        System.arraycopy(insertee, 0, result, pos, insertee.length);
        System.arraycopy(array, pos, result, pos + insertee.length, array.length - pos);
        return result;
    }

    public static <T> T[] remove(Class<T> clazz, T[] array, int pos, int length) {
        T[] result = newInstance(clazz, array.length - length);
        System.arraycopy(array, 0, result, 0, pos);
        System.arraycopy(array, pos + length, result, pos, array.length - (pos + length));
        return result;
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> T[] concat(Class<T> clazz, T[]... arrays) {
        T[] result = newInstance(clazz, Arrays.stream(arrays).mapToInt(array -> array.length).sum());
        int index = 0;
        for (T[] array : arrays) {
            for (T element : array) {
                result[index++] = element;
            }
        }
        return result;
    }

    public static long[] concat(long[] first, int firstFilled, long[] second, boolean exactResize, long defaultValue) {
        if (firstFilled + second.length <= first.length) {
            System.arraycopy(second, 0, first, firstFilled, second.length);
            return first;
        } else {
            // If exactResize is true, expand array exactly by the needed space.
            // Otherwise, double array size if applicable.
            int newSize = exactResize ? firstFilled + second.length : Math.max(first.length * 2, firstFilled + second.length);
            long[] merged = new long[newSize];
            System.arraycopy(first, 0, merged, 0, firstFilled);
            System.arraycopy(second, 0, merged, firstFilled, second.length);
            return merged;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] newInstance(Class<T> clazz, int length) {
        return (T[]) Array.newInstance(clazz, length);
    }

    private ArraysUtil() {
        throw new Error();
    }
}
