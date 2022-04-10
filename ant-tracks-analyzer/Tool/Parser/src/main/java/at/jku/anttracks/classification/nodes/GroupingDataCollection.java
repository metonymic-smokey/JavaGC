package at.jku.anttracks.classification.nodes;

import at.jku.anttracks.heap.Heap;

public interface GroupingDataCollection {
        /**
         * @return Return the number of objects in this collection.
         */
        int getObjectCount();

        long getBytes(Heap heap);

        void clear();
    }