
package at.jku.anttracks.heap.symbols;

import at.jku.anttracks.util.Consts;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

import static at.jku.anttracks.util.Consts.START_ARRAY_LEN;

public class ClassDefinitions implements Iterable<ClassDefinition> {

    ClassDefinition[] definitions;
    boolean isEmpty;

    public ClassDefinitions() {
        definitions = new ClassDefinition[Consts.START_ARRAY_LEN];
        isEmpty = true;
    }

    public void add(ClassDefinition definition) {
        if (definition.id >= definitions.length) {
            int newSize = definitions.length * 2;
            while (definition.id >= newSize) {
                newSize *= 2;
            }
            definitions = Arrays.copyOf(definitions, newSize);
        }
        definitions[definition.id] = definition;
        isEmpty = false;
    }

    public ClassDefinition get(int id) {
        return definitions[id];
    }

    /**
     * Get the current length of the array (that is, the maximum capacity before the array is expanded).
     *
     * @return The current capacity.
     */
    public int getLength() {
        return definitions.length;
    }

    /**
     * Get the current count of class definitions.
     *
     * @return The number of class definitions stored.
     */
    public int getCount() {
        return (int) Arrays.stream(definitions).filter(Objects::nonNull).count();
    }

    /**
     * Get whether there are any class definitions.
     *
     * @return True if {@link #getCount()} would return a non-zero value.
     */
    public boolean isEmpty() {
        return isEmpty;
    }

    @Override
    public Iterator<ClassDefinition> iterator() {
        return new Iterator<ClassDefinition>() {
            int idx = 0;

            private boolean advance() {
                while (idx < definitions.length && definitions[idx] == null) {
                    idx++;
                }
                return idx < definitions.length;
            }

            @Override
            public ClassDefinition next() {
                if (!advance()) {
                    throw new NoSuchElementException();
                }
                return definitions[idx++];
            }

            @Override
            public boolean hasNext() {
                return advance();
            }
        };
    }
}
