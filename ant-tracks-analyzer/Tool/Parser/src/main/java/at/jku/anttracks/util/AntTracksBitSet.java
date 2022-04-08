package at.jku.anttracks.util;

import java.util.Arrays;
import java.util.BitSet;
import java.util.stream.IntStream;

public class AntTracksBitSet extends BitSet {

    private static final long serialVersionUID = -5787690589263903150L;
    private long[] words;
    private int offset;     // number of zero-words before the words array

    public AntTracksBitSet() {
        super(0);   // keep the super word array as small as possible
        words = null;    // first set() call decides over offset
        offset = 0;
    }

    @Override
    public boolean get(int bitIndex) {
        if (bitIndex < 0) {
            throw new IndexOutOfBoundsException("Bit index must be greater equal zero!");
        }

        if (words == null) {
            return false;
        }

        int wordIndex = bitIndex / Long.SIZE;
        if (wordIndex < offset) {
            return false;

        } else if (wordIndex - offset >= words.length) {
            return false;

        } else {
            int indexInWord = bitIndex % Long.SIZE;
            return (words[wordIndex - offset] & (1L << indexInWord)) != 0;
        }
    }

    @Override
    public void set(int bitIndex) {
        if (bitIndex < 0) {
            throw new IndexOutOfBoundsException("Bit index must be greater equal zero!");
        }

        int wordIndex = bitIndex / Long.SIZE;
        if (words == null) {
            // first set() call, init words array and offset
            words = new long[1];
            offset = wordIndex;

        } else if (wordIndex < offset) {
            //ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("AntTracksBitSet::set -> expand front");
            // must expand all zero-words from wordIndex onwards...
            int newWordsLength = words.length + (offset - wordIndex);
            long[] newWords = new long[newWordsLength];
            System.arraycopy(words, 0, newWords, offset - wordIndex, words.length);
            words = newWords;
            offset = wordIndex;
            //m.end();

        } else if (wordIndex - offset >= words.length) {
            //ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("AntTracksBitSet::set -> expand end");
            // must expand words array at the end
            // double words length until new bit index fits in
            int newWordsLength = words.length;
            while (newWordsLength <= wordIndex - offset) {
                newWordsLength *= 2;
            }
            long[] newWords = new long[newWordsLength];
            System.arraycopy(words, 0, newWords, 0, words.length);
            words = newWords;
            //m.end();
        }

        int indexInWord = bitIndex % Long.SIZE;
        words[wordIndex - offset] |= (1L << indexInWord);
    }

    @Override
    public void clear(int bitIndex) {
        if (bitIndex < 0) {
            throw new IndexOutOfBoundsException("Bit index must be greater equal zero!");
        }
        if (words == null) {
            return;
        }

        int wordIndex = bitIndex / Long.SIZE;
        if (wordIndex >= offset && wordIndex - offset < words.length) {
            int indexInWord = bitIndex % Long.SIZE;
            words[wordIndex - offset] &= ~(1L << indexInWord);
        }
    }

    @Override
    public void clear() {
        words = null;
        offset = 0;
    }

    @Override
    public int nextSetBit(int fromIndex) {
        if (fromIndex < 0) {
            throw new IndexOutOfBoundsException("Bit index must be greater equal zero!");
        }

        if (words == null) {
            return -1;
        }

        int wordIndex = fromIndex / Long.SIZE;
        int i;  // word index to start searching from

        if (wordIndex - offset >= words.length) {
            // there are no set bits past word array
            return -1;

        } else if (wordIndex < offset) {
            // start searching at the beginning of the words array
            i = 0;

        } else {
            // start searching at the word of the given index, however mask all bits before indexInWord
            int indexInWord = fromIndex % Long.SIZE;
            long word = words[wordIndex - offset] & (-1L << indexInWord);
            if (word != 0) {
                // next bit is in the masked word
                return Long.numberOfTrailingZeros(word) + wordIndex * Long.SIZE;
            }

            // next bit is after word, start searching after it
            i = wordIndex - offset + 1;
        }

        // move i pointer to the word that contains next bit
        while (i < words.length && words[i] == 0) {
            i++;
        }

        // did we arrive at a word that contains the next bit?
        return i < words.length ?
               Long.numberOfTrailingZeros(words[i]) + (i + offset) * Long.SIZE :
               -1;
    }

    @Override
    public int previousSetBit(int fromIndex) {
        if (fromIndex < 0) {
            throw new IndexOutOfBoundsException("Bit index must be greater equal zero!");
        }

        if (words == null) {
            return -1;
        }

        int wordIndex = fromIndex / Long.SIZE;
        int i;  // word index to start searching from

        if (wordIndex < offset) {
            // there are no set bits before the word array
            return -1;

        } else if (wordIndex - offset >= words.length) {
            // start searching at the end of the word array
            i = words.length - 1;

        } else {
            // start searching at the word of the given index, however mask all bits after indexInWord
            int indexInWord = fromIndex % Long.SIZE;
            long word = words[wordIndex - offset] & (-1L >> indexInWord);
            if (word != 0) {
                // next bit is in the masked word
                return Long.numberOfTrailingZeros(word) + wordIndex * Long.SIZE;
            }

            // previous bit is before word, start searching before it
            i = wordIndex - offset - 1;
        }

        // move i pointer to the word that contains previous bit
        while (i >= 0 && words[i] == 0) {
            i--;
        }

        // did we arrive at a word tat contains the next bit?
        return i >= 0 ?
               Long.numberOfTrailingZeros(words[i]) + (i + offset) * Long.SIZE :
               -1;
    }

    @Override
    public int cardinality() {
        // this is slow, but keeping an updated cardinality represents an overhead to every set()
        // TODO what is better?
        return words == null ? 0 : Math.toIntExact(stream().count());
    }

    @Override
    public boolean isEmpty() {
        return cardinality() == 0;
    }

    public void or(AntTracksBitSet otherBitSet) {
        if (words == null || otherBitSet == null || otherBitSet.words == null) {
            return;
        }

        int firstSetWordIndexThisBS = nextSetBit(0) / Long.SIZE;
        int firstSetWordIndexOtherBS = otherBitSet.nextSetBit(0) / Long.SIZE;
        int lastSetWordIndexThisBS = previousSetBit((words.length + offset) * Long.SIZE) / Long.SIZE;
        int lastSetWordIndexOtherBS = otherBitSet.previousSetBit((otherBitSet.words.length + otherBitSet.offset) * Long.SIZE) / Long.SIZE;

        if (firstSetWordIndexOtherBS < firstSetWordIndexThisBS || lastSetWordIndexOtherBS > lastSetWordIndexThisBS) {
            // the other BS has indices set that are beyond this BS's word array -> we have to expand this BS (i.e. copy it)
            int newWordsLength = Math.max(lastSetWordIndexOtherBS, lastSetWordIndexThisBS) - Math.min(firstSetWordIndexOtherBS, firstSetWordIndexThisBS) + 1;
            long[] newWords = new long[newWordsLength];
            int wordsToCopy = words.length; // copy all words...
            wordsToCopy -= firstSetWordIndexThisBS - offset;    // ...but not empty words at the beginning...
            wordsToCopy -= words.length - (lastSetWordIndexThisBS + 1 - offset);    // ...and also not empty words at the end
            System.arraycopy(words, firstSetWordIndexThisBS - offset, newWords, 0, wordsToCopy);
            words = newWords;
            offset = Math.min(firstSetWordIndexOtherBS, firstSetWordIndexThisBS);
        }

        // perform or operation
        for (int i = firstSetWordIndexOtherBS; i <= lastSetWordIndexOtherBS; i++) {
            words[i - offset] |= otherBitSet.words[i - otherBitSet.offset];
        }
    }

    // TODO when should trim be called?
    public void trim() {
        if (words == null) {
            return;
        }

        // 1) how many words can be dropped from the beginning of the array because they only contain zeros?
        int slackWordsAtTheBeginning = nextSetBit(0) / Long.SIZE - offset;

        // 2) how many words can be dropped from the end of the array because they only contain zeros?
        int slackWordsAtTheEnd = words.length - (previousSetBit((words.length + offset) * Long.SIZE) / Long.SIZE - offset + 1);

        // 3) drop empty words and increase offset
        if (slackWordsAtTheBeginning > 0 || slackWordsAtTheEnd > 0) {
            long[] newWords = new long[words.length - slackWordsAtTheBeginning - slackWordsAtTheEnd];
            System.arraycopy(words, slackWordsAtTheBeginning, newWords, 0, words.length - slackWordsAtTheBeginning - slackWordsAtTheEnd);
            words = newWords;
            offset += slackWordsAtTheBeginning;
        }
    }

    @Override
    public IntStream stream() {
        IntStream.Builder streamBuilder = IntStream.builder();

        if (words != null) {
            for (int i = nextSetBit(0); i != -1; i = nextSetBit(i + 1)) {
                streamBuilder.accept(i);
            }
        }

        return streamBuilder.build();   // is ordered
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("{");

        if (words != null) {
            for (int i = nextSetBit(0); i != -1; i = nextSetBit(i + 1)) {
                stringBuilder.append(i)
                             .append(", ");
            }

            if (stringBuilder.length() > 1) {
                // remove trailing comma and space
                stringBuilder.setLength(stringBuilder.length() - 2);
            }
        }

        stringBuilder.append("}");
        return stringBuilder.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AntTracksBitSet)) {
            return false;
        }

        if (obj == this) {
            return true;
        }

        AntTracksBitSet otherBitset = (AntTracksBitSet) obj;

        // to bit sets are equal if they have the same bits set
        return Arrays.equals(otherBitset.stream().toArray(), this.stream().toArray());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(stream().toArray());
    }

    @Override
    public Object clone() {
        if (words == null) {
            return new AntTracksBitSet();
        }

        trim();
        AntTracksBitSet result = (AntTracksBitSet) super.clone();
        result.words = words.clone();
        result.offset = offset;
        return result;
    }

    @Override
    public int size() {
        return words.length * Long.SIZE;
    }

    // all non overridden methods should never be called

    @Override
    public BitSet get(int fromIndex, int toIndex) {
        throw new UnsupportedOperationException("Not implemented in AntTracksBitSet!");
    }

    @Override
    public void set(int bitIndex, boolean value) {
        throw new UnsupportedOperationException("Not implemented in AntTracksBitSet!");
    }

    @Override
    public void set(int fromIndex, int toIndex) {
        throw new UnsupportedOperationException("Not implemented in AntTracksBitSet!");
    }

    @Override
    public void set(int fromIndex, int toIndex, boolean value) {
        throw new UnsupportedOperationException("Not implemented in AntTracksBitSet!");
    }

    @Override
    public boolean intersects(BitSet set) {
        throw new UnsupportedOperationException("Not implemented in AntTracksBitSet!");
    }

    @Override
    public byte[] toByteArray() {
        throw new UnsupportedOperationException("Not implemented in AntTracksBitSet!");
    }

    @Override
    public int length() {
        throw new UnsupportedOperationException("Not implemented in AntTracksBitSet!");
    }

    @Override
    public int nextClearBit(int fromIndex) {
        throw new UnsupportedOperationException("Not implemented in AntTracksBitSet!");
    }

    @Override
    public int previousClearBit(int fromIndex) {
        throw new UnsupportedOperationException("Not implemented in AntTracksBitSet!");
    }

    @Override
    public long[] toLongArray() {
        throw new UnsupportedOperationException("Not implemented in AntTracksBitSet!");
    }

    @Override
    public void and(BitSet set) {
        throw new UnsupportedOperationException("Not implemented in AntTracksBitSet!");
    }

    @Override
    public void andNot(BitSet set) {
        throw new UnsupportedOperationException("Not implemented in AntTracksBitSet!");
    }

    @Override
    public void clear(int fromIndex, int toIndex) {
        throw new UnsupportedOperationException("Not implemented in AntTracksBitSet!");
    }

    @Override
    public void flip(int bitIndex) {
        throw new UnsupportedOperationException("Not implemented in AntTracksBitSet!");
    }

    @Override
    public void flip(int fromIndex, int toIndex) {
        throw new UnsupportedOperationException("Not implemented in AntTracksBitSet!");
    }

    @Override
    public void or(BitSet set) {
        if (set instanceof AntTracksBitSet) {
            or((AntTracksBitSet) set);
        } else {
            throw new UnsupportedOperationException("Not implemented in AntTracksBitSet!");
        }
    }

    @Override
    public void xor(BitSet set) {
        throw new UnsupportedOperationException("Not implemented in AntTracksBitSet!");
    }

    @Override
    protected void finalize() throws Throwable {
        throw new UnsupportedOperationException("Not implemented in AntTracksBitSet!");
    }
}
