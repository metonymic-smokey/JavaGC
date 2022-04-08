

package at.jku.anttracks.callcontext.cfg;

import at.jku.anttracks.callcontext.util.ImmutableList;
import javassist.bytecode.MethodInfo;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Represents a basic block in the control flow graph.
 *
 * @author Peter Feichtinger
 */
public class Block {

    private final MethodInfo mMethod;
    private final int mPosition;
    private final int mLength;
    private ImmutableList<Block> mIncomings;
    private ImmutableList<Block> mOutgoings;
    private boolean mIsSubroutine;

    /**
     * Create a new {@link Block} with the specified position and length.
     *
     * @param method   The method in which the basic block lives.
     * @param position The position of the basic block.
     * @param length   The length of the basic block.
     */
    Block(MethodInfo method, int position, int length) {
        mMethod = Objects.requireNonNull(method);
        mPosition = position;
        mLength = length;
        mIncomings = ImmutableList.empty();
        mOutgoings = ImmutableList.empty();
    }

    /**
     * Create a subroutine copy of the specified {@link Block} without any links.
     *
     * @param other The block to copy.
     */
    Block(Block other) {
        mMethod = other.mMethod;
        mPosition = other.mPosition;
        mLength = other.mLength;
        mIsSubroutine = true;
    }

    void setIncoming(Block singlePredecessor) {
        mIncomings = ImmutableList.of(singlePredecessor);
    }

    void setIncomings(Collection<Block> incomings) {
        mIncomings = ImmutableList.of(incomings);
    }

    void setOutgoing(Block singleSuccessor) {
        mOutgoings = ImmutableList.of(singleSuccessor);
    }

    void setOutgoings(Collection<Block> outgoings) {
        mOutgoings = ImmutableList.of(outgoings);
    }

    /**
     * Get the method in which this basic block lives.
     *
     * @return The method in which this basic block lives.
     */
    public MethodInfo getMethod() {
        return mMethod;
    }

    /**
     * Get the position of this basic block.
     *
     * @return The bytecode index of the first instruction in this block.
     */
    public int getPosition() {
        return mPosition;
    }

    /**
     * Get the length of this basic block.
     *
     * @return The number of bytes in this basic block.
     */
    public int getLength() {
        return mLength;
    }

    /**
     * Get the position past the end of this basic block.
     *
     * @return The bytecode index of the first instruction after this block.
     */
    public int getEnd() {
        return mPosition + mLength;
    }

    /**
     * Determine whether this block is part of a subroutine. Blocks in subroutines are duplicated for every call site of that subroutine. As
     * a result, there may be multiple blocks with the same position and length in a method's control flow.
     *
     * @return {@code true} if there may be duplicates of this block with different predecessors and successors.
     */
    public boolean isSubroutine() {
        return mIsSubroutine;
    }

    /**
     * Get the list of predecessors of this basic block (i.e. basic blocks from which control can transfer into this basic block).
     *
     * @return An immutable list of this basic block's predecessors.
     */
    public List<Block> getPredecessors() {
        return mIncomings;
    }

    /**
     * Get the number of predecessor blocks. This is a convenience method for {@code getPredecessors().size()}.
     *
     * @return The number of basic blocks from which control may flow into this block.
     * @see #getPredecessors()
     */
    public int enters() {
        return mIncomings.size();
    }

    /**
     * Get a predecessor block. This is a convenience method for {@code getPredecessors().get(n)}.
     *
     * @param n The index of the predecessor block to get.
     * @return The {@code n}th basic block from which control may flow into this block.
     * @throws IndexOutOfBoundsException If {@code n} &lt; 0 or {@code n} &geq; {@link #enters()}.
     * @see #getPredecessors()
     */
    public Block enter(int n) {
        return mIncomings.get(n);
    }

    /**
     * Get the list of successors of this basic block (i.e. basic blocks to which control can transfer into from this basic block).
     *
     * @return An immutable list of this basic block's successors.
     */
    public List<Block> getSuccessors() {
        return mOutgoings;
    }

    /**
     * Get the number of successor blocks. This is a convenience method for {@code getSuccessors().size()}.
     *
     * @return The number of basic blocks to which control may flow from this block.
     * @see #getSuccessors()
     */
    public int exits() {
        return mOutgoings.size();
    }

    /**
     * Get a successor block. This is a convenience method for {@code getSuccessors().get(n)}.
     *
     * @param n The index of the successor block to get.
     * @return The {@code n}th basic block to which control may flow from this block.
     * @throws IndexOutOfBoundsException If {@code n} &lt; 0 or {@code n} &geq; {@link #exits()}.
     * @see #getSuccessors()
     */
    public Block exit(int n) {
        return mOutgoings.get(n);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + mPosition;
        result = prime * result + mLength;
        result = prime * result + mMethod.hashCode();
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

        final Block other = (Block) obj;
        if (mPosition != other.mPosition) {
            return false;
        }
        if (mLength != other.mLength) {
            return false;
        }
        if (!mMethod.equals(other.mMethod)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName()).append('[');
        sb.append("pos=").append(mPosition);
        sb.append(", len=").append(mLength);
        appendBlocks(sb.append(", in="), mIncomings);
        appendBlocks(sb.append(", out="), mOutgoings);
        return sb.append(']').toString();
    }

    private static StringBuilder appendBlocks(StringBuilder sb, Collection<Block> blocks) {
        if (blocks == null) {
            return sb.append("null");
        }
        if (blocks.isEmpty()) {
            return sb.append("{}");
        }
        final Iterator<Block> it = blocks.iterator();
        sb.append('{').append(it.next().mPosition);
        while (it.hasNext()) {
            sb.append(", ").append(it.next().mPosition);
        }
        return sb.append('}');
    }
}
