

package at.jku.anttracks.callcontext;

import java.util.*;

/**
 * Represents a call site and associated information.
 *
 * @author Peter Feichtinger
 */
public class CallSite implements Comparable<CallSite> {

    /**
     * The initial size of the lists holding allocations.
     */
    private static final int EXPECTED_ALLOCATION_COUNT = 2;

    private final MethodName mCaller;
    private final int mCallIndex;
    private MethodName mTarget;

    private boolean mAbstractTarget;
    private Set<MethodName> mDynamicTargets = Collections.emptySet();
    private Set<MethodName> mStaticTargets = Collections.emptySet();
    private List<String> mAllocationsBefore;
    private List<String> mAllocationsAfter;

    /**
     * Create a new {@link CallSite} with the specified caller and target methods.
     *
     * @param caller The fully qualified name of the method that contains the call.
     * @param bci    The bytecode index of the call in the caller method.
     * @param target The name of the method that is called.
     * @throws IllegalArgumentException if {@code bci} is negative.
     */
    CallSite(MethodName caller, int bci, MethodName target) {
        mCaller = Objects.requireNonNull(caller);
        if (bci < 0) {
            throw new IllegalArgumentException("bci cannot be negative.");
        }
        mCallIndex = bci;
        mTarget = Objects.requireNonNull(target);
    }

    /**
     * Get the calling method.
     *
     * @return A {@link MethodName} for the method that contains the call.
     */
    public MethodName getCaller() {
        return mCaller;
    }

    /**
     * Get the method name of the calling method. This is a convenience method for {@code getCaller().toString()}.
     *
     * @return The fully qualified name of the method that contains the call.
     */
    public String getCallerName() {
        return mCaller.toString();
    }

    /**
     * Get the bytecode index of the call in the caller method.
     *
     * @return The bytecode index into the calling method to the call instruction.
     */
    public int getCallIndex() {
        return mCallIndex;
    }

    /**
     * Get the method being called.
     *
     * @return A {@link MethodName} for the method that is called.
     */
    public MethodName getTarget() {
        return mTarget;
    }

    /**
     * Get the method name of the method being called. This is a convenience method for {@code getTarget().toString()}.
     *
     * @return The fully qualified name of the method that is called.
     */
    public String getTargetName() {
        return mTarget.toString();
    }

    /**
     * Set the method being called. <strong>This method should only be used by {@link CallContextAnalyzer}.</strong>
     *
     * @param target A {@link MethodName} for the method that is called.
     */
    void setTarget(MethodName target) {
        mTarget = Objects.requireNonNull(target);
    }

    /**
     * Get whether the target method is an abstract method.
     *
     * @return {@code true} if the target method is an abstract method that cannot be called.
     */
    public boolean isAbstract() {
        return mAbstractTarget;
    }

    /**
     * Set whether the target method is an abstract method.
     *
     * @param abstractTarget Whether the target method is an abstract method.
     */
    void setAbstract(boolean abstractTarget) {
        mAbstractTarget = abstractTarget;
    }

    /**
     * Get the dynamic targets for this call site. A dynamic call target is one that overrides the statically bound target method of this
     * call site.
     *
     * @return An unmodifiable view of the dynamic targets. Changes to this call site's dynamic targets will be reflected in the returned
     * set.
     */
    public Set<MethodName> getDynamicTargets() {
        return mDynamicTargets;
    }

    /**
     * Set the dynamic call targets for this call site.
     * <p>
     * A dynamic call target is one that overrides the statically bound target method of this call site.
     *
     * @param dynamicTargets The set of dynamic targets.
     */
    void setDynamicTargets(Set<MethodName> dynamicTargets) {
        mDynamicTargets = Collections.unmodifiableSet(dynamicTargets);
    }

    /**
     * Get the static targets for this call site. A static call target is one that is overridden by the target method of this call site.
     *
     * @return An unmodifiable view of the static targets. Changes to this call site's static targets will be reflected in the returned set.
     */
    public Set<MethodName> getStaticTargets() {
        return mStaticTargets;
    }

    /**
     * Set the static call targets for this call site.
     * <p>
     * A static call target is one that is overridden by the target method of this call site.
     *
     * @param staticTargets The set of static targets.
     */
    void setStaticTargets(Set<MethodName> staticTargets) {
        mStaticTargets = Collections.unmodifiableSet(staticTargets);
    }

    /**
     * Get the allocations immediately succeeding this call. The allocations are ordered as they appear in code (first allocation first).
     *
     * @return An unmodifiable view of the allocations succeeding this call.
     */
    public List<String> getAllocationsAfter() {
        if (mAllocationsAfter == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(mAllocationsAfter);
    }

    /**
     * Add an allocation after this call to the back of the list.
     *
     * @param className The descriptor of the type of object being allocated.
     */
    void addAllocationAfter(String className) {
        // About 80% of calls don't have any allocations associated with them
        if (mAllocationsAfter == null) {
            mAllocationsAfter = new ArrayList<>(EXPECTED_ALLOCATION_COUNT);
        }
        mAllocationsAfter.add(className);
    }

    /**
     * Get the allocations immediately preceding this call. The allocations are ordered in reverse (last allocation first).
     *
     * @return An unmodifiable view of the allocations preceeding this call.
     */
    public List<String> getAllocationsBefore() {
        if (mAllocationsBefore == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(mAllocationsBefore);
    }

    /**
     * Add an allocation before this call to the front of the list.
     *
     * @param className The descriptor of the type of object being allocated.
     */
    void addAllocationBefore(String className) {
        // About 80% of calls don't have any allocations associated with them
        if (mAllocationsBefore == null) {
            mAllocationsBefore = new ArrayList<>(EXPECTED_ALLOCATION_COUNT);
        }
        mAllocationsBefore.add(className);
        Collections.rotate(mAllocationsBefore, 1);
    }

    /**
     * Delete all allocations before this call that have already been recorded.
     *
     * @see #addAllocationBefore(String)
     * @see #getAllocationsBefore()
     */
    void clearAllocationsBefore() {
        mAllocationsBefore = null;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + mCaller.hashCode();
        result = prime * result + mCallIndex;
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

        CallSite other = (CallSite) obj;
        if (mCallIndex != other.mCallIndex) {
            return false;
        }
        if (!mCaller.equals(other.mCaller)) {
            return false;
        }
        return true;
    }

    @Override
    public int compareTo(CallSite o) {
        if (this == o) {
            return 0;
        }
        int tmp = mCaller.compareTo(o.mCaller);
        if (tmp == 0) {
            // Call index is not negative so this is safe
            tmp = mCallIndex - o.mCallIndex;
        }
        return tmp;
    }

    @Override
    public String toString() {
        return makeName(mCaller, mCallIndex);
    }

    /**
     * Build a call site name from the specified caller and bytecode index.
     *
     * @param caller The fully qualified name of the method that contains the call.
     * @param bci    The bytecode index into the calling method to the call instruction.
     * @return A unique name that identifies the call site.
     */
    public static String makeName(MethodName caller, int bci) {
        return caller.toString() + ':' + bci;
    }
}
