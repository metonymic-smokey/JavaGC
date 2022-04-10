

package at.jku.anttracks.callcontext;

import at.jku.anttracks.callcontext.util.LookupTree;
import at.jku.anttracks.callcontext.util.SetMultimap;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * This class provides methods to query context information about classes loaded by the JVM, e.g. allocations immediately before and after
 * calls.
 * <p>
 * Note that all public methods of this class are thread-safe.
 *
 * @author Peter Feichtinger
 */
public class CallContextAnalyzer {

    /**
     * Mapping from calling methods to call sites.
     */
    private final SetMultimap<MethodName, CallSite> mCallers = new SetMultimap<>();
    /**
     * Mapping from target methods to call sites.
     */
    private final SetMultimap<MethodName, CallSite> mTargets = new SetMultimap<>();
    /**
     * Mapping from target methods to names of possible dynamic target methods.
     */
    private final SetMultimap<MethodName, MethodName> mDynamicTargets = new SetMultimap<>();
    /**
     * Mapping from dynamic target methods to names of possible static targets.
     */
    private final SetMultimap<MethodName, MethodName> mStaticTargets = new SetMultimap<>();

    // Caching
    private final transient ConcurrentMap<MethodName, LookupTree<String, CallSite>> mAllocationTraces;
    private transient volatile SortedSet<CallSite> mCallSites;
    private transient volatile SortedSet<CallSite> mUniqueCallers;

    /**
     * Get a {@link CallContextBuilder} instance used to build a {@link CallContextAnalyzer}.
     *
     * @return A {@link CallContextBuilder} instance.
     */
    public static at.jku.anttracks.callcontext.CallContextBuilder builder() {
        return new at.jku.anttracks.callcontext.CallContextBuilder();
    }

    // Builder interface ==================================================================================================================

    /**
     * Create an empty {@link CallContextAnalyzer}.
     */
    CallContextAnalyzer() {
        mAllocationTraces = new ConcurrentHashMap<>();
    }

    /**
     * Add an override for the specified methods.
     *
     * @param superName The name of the method being overridden.
     * @param newName   The name of the overriding method.
     */
    void putOverride(MethodName superName, MethodName newName) {
        mDynamicTargets.put(superName, newName);
        mStaticTargets.put(newName, superName);
    }

    /**
     * Add the specified call to this analyzer.
     *
     * @param call The call to add.
     * @return {@code true} if the call was added, {@code false} if this analyzer already contained an identical call.
     */
    boolean addCall(CallSite call) {
        if (mCallers.put(call.getCaller(), call)) {
            mTargets.put(call.getTarget(), call);
            if (!call.getTarget().isConstructor()) {
                call.setDynamicTargets(mDynamicTargets.get(call.getTarget()));
                call.setStaticTargets(mStaticTargets.get(call.getTarget()));
            }
            return true;
        }
        return false;
    }

    /**
     * Set the method being called for the specified call site .
     *
     * @param call      The call site.
     * @param newTarget A {@link MethodName} for the method that is called.
     */
    void setTarget(CallSite call, MethodName newTarget) {
        mTargets.remove(call.getTarget(), call);
        mTargets.put(newTarget, call);
        assert !call.getTarget().isConstructor() && !newTarget.isConstructor() : "Target of a constructor call should not be changed.";
        if (!newTarget.isConstructor()) {
            call.setDynamicTargets(mDynamicTargets.get(newTarget));
            call.setStaticTargets(mStaticTargets.get(newTarget));
        }
        call.setTarget(newTarget);
    }

    /**
     * Stream all call sites in this analyzer.
     *
     * @return A {@link Stream} to all call sites.
     */
    Stream<CallSite> stream() {
        return mCallers.streamValues();
    }

    /**
     * Stream all call sites with the specified caller.
     *
     * @param caller The name of the calling method.
     * @return A {@link Stream} to all call sites with the specified caller.
     */
    Stream<CallSite> streamTargets(MethodName caller) {
        return mCallers.get(caller).stream();
    }

    /**
     * Get the number of call sites in this analyzer.
     *
     * @return The number of call sites in this analyzer.
     */
    int size() {
        return mCallers.size();
    }

    /**
     * Find a {@link CallSite} with the specified caller and bytecode index.
     *
     * @param caller The method name of the calling method.
     * @param bci    The bytecode index into the calling method.
     * @return The call site at the specified location, or {@code null} if there is none.
     */
    CallSite findCall(MethodName caller, int bci) {
        final Set<CallSite> callSites = mCallers.get(caller);
        return callSites.stream().filter(cs -> cs.getCallIndex() == bci).findAny().orElse(null);
    }

    // Query interface ====================================================================================================================

    /**
     * Get all call sites that have been analyzed. This method differs from {@link #getCallSitesFast()} in that a new set is created for
     * each call, which may be modified by the caller.
     *
     * @return A set of call sites by value, sorted by {@linkplain CallSite CallSite's} natural ordering.
     * @see #getCallSitesFast()
     */
    public SortedSet<CallSite> getCallSites() {
        return mTargets.streamValues().collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
    }

    /**
     * Get all call sites that have been analyzed. This method differs from {@link #getCallSites()} in that the returned set is cached and
     * cannot be modified.
     *
     * @return An unmodifiable set of call sites by reference, sorted by {@linkplain CallSite CallSite's} natural ordering.
     * @see #getCallSites()
     */
    public SortedSet<CallSite> getCallSitesFast() {
        SortedSet<CallSite> result = mCallSites;
        if (result == null) {
            synchronized (this) {
                if ((result = mCallSites) == null) {
                    mCallSites = result = Collections.unmodifiableSortedSet(getCallSites());
                }
            }
        }
        return result;
    }

    /**
     * Get all call sites that represent a unique call. A unique call is one where the target method has only one caller.
     * <p>
     * Note that dynamic targets are considered when deciding whether a call is unique.
     *
     * @return An unmodifiable set of call sites by reference, sorted by {@linkplain CallSite CallSite's} natural ordering.
     */
    public SortedSet<CallSite> getUniqueCalls() {
        SortedSet<CallSite> result = mUniqueCallers;
        if (result == null) {
            synchronized (this) {
                if ((result = mUniqueCallers) == null) {
                    final TreeSet<CallSite> uniqueCalls = mTargets.keySet()
                                                                  .stream()
                                                                  .map(this::findUniqueCall)
                                                                  .filter(Objects::nonNull)
                                                                  .collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
                    mUniqueCallers = result = Collections.unmodifiableSortedSet(uniqueCalls);
                }
            }
        }
        return result;
    }

    /**
     * Find a call from the specified call site (caller and bytecode index) to the specified target method.
     *
     * @param caller The name of the calling method.
     * @param bci    The bytecode index of the call in the calling method.
     * @param target The name of the target method.
     * @return The corresponding {@link CallSite}, or {@code null} if none can be found.
     */
    public CallSite findCall(MethodName caller, int bci, MethodName target) {
        final Predicate<CallSite> match = cs -> cs.getCallIndex() == bci && cs.getCaller().equals(caller);
        // Look for a statically bound call that matches
        return mTargets.get(target).stream().filter(match).findFirst().orElseGet(
                // If no statically bound call can be found, look for a possible dynamically bound call
                () -> mStaticTargets.get(target).stream().flatMap(name -> mTargets.get(name).stream()).filter(match).findFirst().orElse(null));
    }

    /**
     * Get a stack trace for the specified target method using only {@linkplain #getUniqueCalls() unique calls}.
     *
     * @param target The target method.
     * @return A list of unique callers (ordered up the stack, that is the first element is the direct caller of {@code target}) by value,
     * may be empty.
     */
    public List<CallSite> getUniqueStackTrace(MethodName target) {
        final List<CallSite> trace = new ArrayList<>(4);
        final Set<MethodName> visited = new HashSet<>();
        MethodName nextTarget = target;
        CallSite uniqeCall;
        while (!visited.contains(nextTarget) && (uniqeCall = findUniqueCall(nextTarget)) != null) {
            visited.add(nextTarget);
            trace.add(uniqeCall);
            nextTarget = uniqeCall.getCaller();
        }
        return trace;
    }

    /**
     * Attempt to find the unique call to the specified target method.
     *
     * @param target The target method to find the only call for.
     * @return The unique call to the target method (which may be a call to another method which {@code target} overrides), or {@code null}
     * if none can be found (meaning either there is no call at all or no unique call).
     */
    @SuppressWarnings("fallthrough")
    public CallSite findUniqueCall(MethodName target) {
        // This is a little complicated, so I need to explain it to myself in plain English ;-)
        //
        // We're looking for uniquely resolvable calls to a target method.
        // If `mTargets` contains exactly one call site for `target`, then this is the only call site that is statically bound to `target`;
        // if there are multiple call sites, then there is of course no unique caller, so we return.
        // In case there is no call site or only one, we need to look for calls to target methods which `target` overrides (that is,
        // statically bound calls to methods for which `target` is a dynamic target).
        // If there is no such method, the static call we possibly found earlier is a unique call; otherwise, if we found a static call
        // earlier and there is any method that is actually called, or if there are multiple methods which are actually called, the static
        // or dynamic call, respectively, is not unique and we return.
        // Now if we didn't find a static call earlier and there is one overridden candidate, this is the unique call if it is called by one
        // call site, otherwise it's not unique and we return.

        CallSite uniqueCall = null;
        Set<CallSite> calls = mTargets.get(target);
        switch (calls.size()) {
            case 1:
                // `target` has a unique statically bound call site
                uniqueCall = calls.iterator().next();
                // fallthrough
            case 0:
                // Check for multiple dynamic targets (or find single target)
                final MethodName[] staticCalls = mStaticTargets.get(target).stream().filter(it -> mTargets.containsKey(it)).toArray(MethodName[]::new);
                if (uniqueCall != null && staticCalls.length != 0 || staticCalls.length > 1) {
                    // `target` is both a static and a dynamic target, or it overrides multiple methods -> not unique
                    return null;
                } else if (uniqueCall != null) {
                    // `target` is not a dynamic target -> unique
                    assert staticCalls.length == 0;
                    return uniqueCall;
                } else if (staticCalls.length == 0) {
                    return null;
                }
                // `target` is an override for one statically bound target
                calls = mTargets.get(staticCalls[0]);
                assert !calls.isEmpty();
                if (calls.size() > 1) {
                    // `target` i a dynamic target for a non-uniquely called method -> not unique
                    return null;
                }
                // `target` is a dynamic target for one uniquely called method -> unique
                uniqueCall = calls.iterator().next();
                assert uniqueCall.getDynamicTargets().contains(target);
                return uniqueCall;
            default:
                // `target` has multiple call sites -> not unique
                return null;
        }
    }

    /**
     * Get a {@link LookupTree} from allocations to call sites for the specified target method.
     *
     * @param target The name of the target method.
     * @return A lookup tree from allocations before a call to a call site with target method {@code target}, or {@code null} of the
     * specified method is not called.
     */
    public LookupTree<String, CallSite> getAllocationTraceLookup(MethodName target) {
        if (!mTargets.containsKey(target)) {
            return null;
        }
        return mAllocationTraces.computeIfAbsent(target, this::computeAllocationTraces);
    }

    /**
     * Build a {@link LookupTree} for all possible dynamic allocation traces for the specified target method.
     *
     * @param method The name of the target method for which traces should be computed.
     * @return A lookup tree from allocated type name to {@link CallSite} for the specified method.
     */
    private LookupTree<String, CallSite> computeAllocationTraces(MethodName method) {
        final LookupTree.Builder<String, CallSite> builder = LookupTree.greedyBuilder();
        for (CallSite site : mTargets.get(method)) {
            final List<String> allocs = site.getAllocationsBefore();
            if (allocs.isEmpty()) {
                return LookupTree.empty();
            }
            builder.put(allocs, site);
        }
        return builder.build();
    }
}
