

package at.jku.anttracks.callcontext.cfg;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * A mark that indicates the position of a jump instruction or target.
 *
 * @author Peter Feichtinger
 */
class Mark {

    private final Mark[] mMarks;
    private final int mPosition;
    private final Set<Mark> mIncoming = new HashSet<>();
    private final Set<Mark> mOutgoing = new HashSet<>();
    private boolean mIsBegin;
    private boolean mIsUnconditional;

    /**
     * Create a new mark with the specified position.
     *
     * @param marks   The array of marks indexed by bytecode index.
     * @param bci     The bytecode index of the mark.
     * @param isBegin Whether the mark is at the start of a block.
     */
    private Mark(Mark[] marks, int bci, boolean isBegin) {
        assert marks[bci] == null;
        marks[bci] = this;
        mMarks = marks;
        mPosition = bci;
        mIsBegin = isBegin;
    }

    /**
     * Get the position of this mark.
     *
     * @return The bytecode index this mark is at.
     */
    public int getPosition() {
        return mPosition;
    }

    /**
     * Get the set of incoming marks.
     *
     * @return A set of all marks for jumps that target this mark.
     */
    public Set<Mark> getIncoming() {
        return mIncoming;
    }

    /**
     * Get the set of outgoing marks.
     *
     * @return A set of all marks this mark jumps to.
     */
    public Set<Mark> getOutgoing() {
        return mOutgoing;
    }

    /**
     * Create or get an end mark at the specified position.
     *
     * @param marks           The array of marks indexed by bytecode index.
     * @param bci             The bytecode index of the mark.
     * @param isUnconditional Whether the mark is for an unconditional jump.
     * @return The mark at position {@code bci}.
     */
    public static Mark makeEnd(Mark[] marks, int bci, boolean isUnconditional) {
        Mark mark = marks[bci];
        if (mark == null) {
            mark = new Mark(marks, bci, false);
        }
        if (isUnconditional) {
            mark.setUnconditional();
        }
        return mark;
    }

    /**
     * Create or get a start mark at the specified position.
     *
     * @param marks The array of marks indexed by bytecode index.
     * @param bci   The bytecode index of the mark.
     * @return The mark at position {@code bci}.
     */
    public static Mark makeStart(Mark[] marks, int bci) {
        final Mark mark = marks[bci];
        if (mark != null) {
            mark.mIsBegin = true;
            return mark;
        }
        return new Mark(marks, bci, true);
    }

    /**
     * Get whether this mark is for the start or end of a block.
     *
     * @return {@code true} if this mark is at the start of a block.
     */
    public boolean isBegin() {
        return mIsBegin;
    }

    /**
     * Get whether this mark is for an unconditional jump.
     *
     * @return For an end mark, {@code true} if the jump instruction is unconditional and {@code false} if it is conditional; for a start
     * mark, this has no meaning.
     * @see #isBegin()
     * @see #setUnconditional()
     */
    public boolean isUnconditional() {
        return mIsUnconditional;
    }

    /**
     * Set the unconditional jump flag for this mark.
     *
     * @see #isUnconditional()
     */
    public void setUnconditional() {
        mIsUnconditional = true;
    }

    /**
     * Create a mark or get an existing mark for a jump from this mark to the specified bytecode index.
     *
     * @param targetBci The bytecode index of the jump target.
     * @return The mark at the target position.
     */
    public Mark makeJump(int targetBci) {
        final Mark target = makeStart(mMarks, targetBci);
        link(target);
        return target;
    }

    /**
     * Create or get an existing start mark for the successor block of this mark, if there are instructions after this mark.
     *
     * @param size The size of the instruction at this mark.
     * @return The successor mark, or {@code null} if there are no instructions after this mark.
     * @throws IllegalStateException If this is a begin mark.
     */
    public Mark makeSuccessor(int size) {
        if (mPosition + size >= mMarks.length) {
            return null;
        }
        final Mark m = makeStart(mMarks, mPosition + size);
        if (!mIsUnconditional) {
            link(m);
        }
        return m;
    }

    /**
     * Link this mark to the specified successor mark.
     *
     * @param successor The successor mark to link ot.
     */
    public void link(Mark successor) {
        mOutgoing.add(successor);
        successor.mIncoming.add(this);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName()).append('[');
        sb.append(mIsBegin ? "start" : "end");
        sb.append(", pos=").append(mPosition);
        appendMarks(sb.append(", in="), mIncoming);
        appendMarks(sb.append(", out="), mOutgoing);
        return sb.append(']').toString();
    }

    private static StringBuilder appendMarks(StringBuilder sb, Collection<Mark> marks) {
        if (marks == null) {
            return sb.append("null");
        }
        if (marks.isEmpty()) {
            return sb.append("{}");
        }
        final Iterator<Mark> it = marks.iterator();
        sb.append('{').append(it.next().mPosition);
        while (it.hasNext()) {
            sb.append(", ").append(it.next().mPosition);
        }
        return sb.append('}');
    }
}
