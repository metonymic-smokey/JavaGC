package at.jku.anttracks.classification.nodes;

public class HeapObjectGroupingKey {
        public long objectSizeInBytes;

        public HeapObjectGroupingKey(long objectSizeInBytes) {
            this.objectSizeInBytes = objectSizeInBytes;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + (int) (objectSizeInBytes ^ (objectSizeInBytes >>> 32));
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
            HeapObjectGroupingKey other = (HeapObjectGroupingKey) obj;
            return objectSizeInBytes == other.objectSizeInBytes;
        }
    }