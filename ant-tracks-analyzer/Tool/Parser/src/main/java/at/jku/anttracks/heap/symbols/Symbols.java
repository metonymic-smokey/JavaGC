
package at.jku.anttracks.heap.symbols;

import at.jku.anttracks.callcontext.*;
import at.jku.anttracks.callcontext.util.LookupTree;
import at.jku.anttracks.callcontext.util.PeekIteratorMapper;
import at.jku.anttracks.features.FeatureMap;
import at.jku.anttracks.features.FeatureMapCache;
import at.jku.anttracks.heap.GarbageCollectionCauses;
import at.jku.anttracks.heap.datastructures.dsl.DSLDSPartDesc;
import at.jku.anttracks.heap.datastructures.dsl.DataStructureUtil;
import at.jku.anttracks.parser.heap.AllocationContext;
import at.jku.anttracks.util.*;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.logging.Logger;

public class Symbols {

    private static final Logger LOGGER = Logger.getLogger(Symbols.class.getSimpleName());

    // This has to be changed EVERY TIME(!) when the symbols file format gets changed in the VM
    public static final int SYMBOLS_VERSION = 5;

    public static final int CALLCONTEXT_NONE = 0;
    public static final int CALLCONTEXT_STATIC = 1;
    public static final int CALLCONTEXT_DYNAMIC = 2;
    public static final int CALLCONTEXT_FULL = CALLCONTEXT_STATIC | CALLCONTEXT_DYNAMIC;

    public final String root;
    public final int[] header;
    public final boolean anchors;
    public final boolean isHeapFragmented;
    public final int heapWordSize;
    public final boolean expectPointers;
    public final String trace;
    public final String featureFile;
    public final GarbageCollectionCauses causes;
    public final AllocationSites sites;
    public final AllocatedTypes types;
    public final FeatureMap features;
    public final FeatureMapCache featureCache;

    private ConcurrentMap<AllocationSite, CopyOnWriteLookupTree<String, AllocationSite>> dynamicAllocationSites;
    private int useCallContext = CALLCONTEXT_NONE;
    private CallContextAnalyzer cca;
    private IntBooleanMap dynamicallyExtendableSites;
    private LongAdder allocationSiteCreationAttempts;
    private LongAdder allocationSiteCreationFailures;
    private LongAdder allocationContextExhausted;

    private Set<URI> dataStructureDefinitionFiles;

    public Symbols(String root,
                   int[] header,
                   boolean anchors,
                   int heapWordSize,
                   boolean expectPointers,
                   String trace,
                   String featureFile,
                   FeatureMap features,
                   boolean isHeapFragmented) {
        this.root = root;
        this.header = header;
        this.anchors = anchors;
        this.heapWordSize = heapWordSize;
        this.expectPointers = expectPointers;
        this.trace = trace;
        this.featureFile = featureFile;
        this.isHeapFragmented = isHeapFragmented;

        this.features = features;
        featureCache = features != null ? new FeatureMapCache(this) : null;

        causes = new GarbageCollectionCauses();
        sites = new AllocationSites();
        types = new AllocatedTypes();

        this.dataStructureDefinitionFiles = DataStructureUtil.INSTANCE.getDefaultDataStructureDefinitionFiles();
    }

    /**
     * Initialize the {@link CallContextAnalyzer} from the specified class definitions.
     *
     * @param definitions The class definitions, or {@code null}.
     * @param usage       One of {@link #CALLCONTEXT_NONE}, {@link #CALLCONTEXT_STATIC}, {@link #CALLCONTEXT_DYNAMIC}, and
     *                    {@link #CALLCONTEXT_FULL}.
     * @throws TraceException        If a class definition cannot be parsed.
     * @throws IllegalStateException If the {@code CallContextAnalyzer} is already initialized.
     */
    public void initCallContext(ClassDefinitions definitions, int usage) throws TraceException {
        if (cca != null) {
            throw new IllegalStateException();
        }
        if (usage == CALLCONTEXT_NONE || definitions == null || definitions.isEmpty()) {
            useCallContext = CALLCONTEXT_NONE;
            cca = null;
            return;
        }
        try {
            final CallContextBuilder builder = CallContextAnalyzer.builder();
            for (ClassDefinition def : definitions) {
                builder.addClass(def.data);
            }
            cca = builder.build();
            useCallContext = usage;
            dynamicallyExtendableSites = new IntBooleanMap(0, sites.getBiggestId(), true);
        } catch (BadClassDefinitionException ex) {
            throw new TraceException(ex);
        }
        if (useDynamicCallContext()) {
            dynamicAllocationSites = new ConcurrentHashMap<>();
            allocationSiteCreationAttempts = new LongAdder();
            allocationSiteCreationFailures = new LongAdder();
            allocationContextExhausted = new LongAdder();
        }
    }

    /**
     * Get whether call context analysis is enabled at all.
     *
     * @return {@code true} if any of static or dynamic call context analysis is enabled.
     * @see #useStaticCallContext()
     * @see #useDynamicCallContext()
     */
    public boolean useCallContext() {
        return useCallContext != CALLCONTEXT_NONE;
    }

    /**
     * Get whether static call context analysis is enabled.
     *
     * @return {@code true} if static call context analysis is enabled.
     * @see #useCallContext()
     */
    public boolean useStaticCallContext() {
        return (useCallContext & CALLCONTEXT_STATIC) != 0;
    }

    /**
     * Get whether dynamic call context analysis is enabled.
     *
     * @return {@code true} if dynamic call context analysis is enabled.
     * @see #useCallContext()
     * @see #getOrCreateDynamicAllocationSite(AllocationSite, AllocationContext)
     */
    public boolean useDynamicCallContext() {
        return (useCallContext & CALLCONTEXT_DYNAMIC) != 0;
    }

    /**
     * If dynamic call context analysis is enabled ({@link #useDynamicCallContext()} returns {@code true}), get the number of attempts made
     * to expand an allocation site with stack trace elements from dynamic context.
     *
     * @return The number of allocation site creation attempts, or 0 if dynamic call context analysis is disabled.
     */
    public long getAllocationSiteCreationAttempts() {
        if (allocationSiteCreationAttempts == null) {
            return 0L;
        }
        return allocationSiteCreationAttempts.sum();
    }

    /**
     * If dynamic call context analysis is enabled ({@link #useDynamicCallContext()} returns {@code true}), get the number of failed
     * attempts to expand an allocation site with stack trace elements from dynamic context.
     *
     * @return The number of allocation site creation failures, or 0 if dynamic call context analysis is disabled.
     */
    public long getAllocationSiteCreationFailures() {
        if (allocationSiteCreationFailures == null) {
            return 0L;
        }
        return allocationSiteCreationFailures.sum();
    }

    /**
     * If dynamic call context analysis is enabled ({@link #useDynamicCallContext()} returns {@code true}), get the number of times the
     * allocation context was exhausted while trying to expand an allocation site with stack trace elements from dynamic context.
     *
     * @return The number of times the allocation context ran out, or 0 if dynamic call context analysis is disabled.
     */
    public long getAllocationContextExhausted() {
        if (allocationContextExhausted == null) {
            return 0L;
        }
        return allocationContextExhausted.sum();
    }

    /**
     * Get or create a dynamic allocation site for the specified allocation site. This method can be called only if
     * {@link #useDynamicCallContext()} returns {@code true}.
     *
     * @param site The original allocation site.
     * @param ctx  The allocation context.
     * @return The dynamic allocation site for {@code site}, or {@code site} itself.
     */
    public AllocationSite getOrCreateDynamicAllocationSite(AllocationSite site, AllocationContext ctx) {
        // Speculatively get the lookup tree to avoid computeIfAbsent costs in
        // the common case
        CopyOnWriteLookupTree<String, AllocationSite> lookup = dynamicAllocationSites.get(site);
        if (lookup == null) {
            final MutableInt created = new MutableInt(0);
            lookup = dynamicAllocationSites.computeIfAbsent(site, k -> {
                created.inc();
                return new CopyOnWriteLookupTree<>(null, ALLOCATION_SITE_COMPARATOR);
            });
            if (created.get() != 0) {
                return site;
            }
        }
        AllocationSite result = lookup.lookup(ctx.lookupIterator(), null);
        if (result == null) {
            result = createSiteContended(site, ctx, lookup);
        }
        return result;
    }

    private static AbstractBiPredicate<AllocationSite> ALLOCATION_SITE_COMPARATOR = new AbstractBiPredicate<AllocationSite>() {
        @Override
        public boolean test(AllocationSite a, AllocationSite b) {
            // A new allocation site will not have the same ID as an existing
            // one, so `a.equals(b)` will always be false.
            // Instead, compare the allocated type ID and stack traces.
            if (a == null || b == null) {
                return a == b;
            }
            return a.getAllocatedTypeId() == b.getAllocatedTypeId() && Arrays.equals(a.getCallSites(), b.getCallSites());
        }
    };

    private AllocationSite createSiteContended(AllocationSite site, AllocationContext ctx, CopyOnWriteLookupTree<String, AllocationSite> lookup) {
        // Create the new trace outside the lock optimistically
        MutableInt depth = new MutableInt(0);
        final AllocationSite.Location[] newTrace = createDynamicStackTrace(site, ctx, depth);
        allocationSiteCreationAttempts.increment();
        if (newTrace == null) {
            allocationSiteCreationFailures.increment();
            if (depth.get() == 0) {
                return site;
            }
        }

        AllocationSite result;
        do {
            synchronized (lookup) {
                CopyOnWriteLookupTree<String, AllocationSite> newLookup = dynamicAllocationSites.get(site);
                if (newLookup == lookup) {
                    result = createDynamicAllocationSite(site, newTrace);
                    newLookup = lookup.put(new LimitingIterator<>(ctx.insertionIterator(result == null), Math.max(1, depth.get())), result);
                    final CopyOnWriteLookupTree<String, AllocationSite> check = dynamicAllocationSites.put(site, newLookup);
                    assert check == lookup;
                    return (result != null ? result : site);
                } else {
                    // This branch will rarely be taken (i.e. insertion into
                    // lookup trees is almost never contended) for typical
                    // traces
                    result = newLookup.lookup(ctx.lookupIterator(), site);
                    lookup = newLookup;
                }
            }
        } while (result == null);
        return result;
    }

    /**
     * Create a dynamic stack trace for the specified allocation site.
     *
     * @param original The original allocation site.
     * @param ctx      The allocation context.
     * @return The new stack trace, or {@code null}.
     */
    private AllocationSite.Location[] createDynamicStackTrace(AllocationSite original, AllocationContext ctx, MutableInt depth) {
        final List<CallSite> frames = new ArrayList<>();
        int uniqueCount = 0;
        if (!MethodName.test(original.getCallSites()[0].getSignature()) || original.getCallSites()[0].getSignature().contains("<clinit>")) {
            // Don't try to expand the call stack for static initializers
            return null;
        }

        final Function<AllocationSite, String> allocatedTypeNameMapper = alloc -> types.getById(alloc.getAllocatedTypeId()).internalName;

        int lastCount = 0;
        AllocationSite nextSite = original;
        MethodName target = MethodName.tryCreate(ArraysUtil.last(original.getCallSites()).getSignature());
        boolean first = true;
        if (target == null) {
            return null;
        }
        outer:
        while ((nextSite == null || ctx.skipTrace(nextSite.getCallSites()) == nextSite.getCallSites().length) && ctx.hasNext()) {
            // Try to find a CallSite for the last method on the stack
            CallSite call;
            {
                LookupTree<String, CallSite> context;
                if ((call = cca.findUniqueCall(target)) != null) {
                    if (call.getCaller().equals(target)) {
                        // Break on direct recursion: the only way this can
                        // happen is when a directly recursive method is called
                        // reflectively (which CallContextAnalyzer can't track,
                        // otherwise the call wouldn't be unique any more).
                        // Indirect recursion we can't do anything about, but:
                        // the only way indirect recursion can happen with only
                        // unique calls is when there is a set of methods only
                        // calling each other in a circle, and one of them is
                        // called reflectively (which, again,
                        // CallContextAnalyzer can't track). I just hope that
                        // doesn't happen.
                        break;
                    }
                    uniqueCount++;
                    frames.add(call);
                    continue;
                }
                if ((context = cca.getAllocationTraceLookup(target)) == null || context.isEmpty()) {
                    // No unique call and no allocation context, trace finished
                    if (first && context == null) {
                        dynamicallyExtendableSites.set(original.getId(), false);
                    }
                    break;
                }
                depth.set(Math.max(depth.get(), context.getMaxDepth() - 1 /* because of root node */));
                if ((call = context.lookup(new PeekIteratorMapper<>(ctx, allocatedTypeNameMapper))) == null) {
                    // Call context doesn't match, trace finished
                    break;
                }
                frames.add(call);
                first = false;
            }
            if (lastCount != ctx.getCount()) {
                lastCount = ctx.getCount();
                nextSite = ctx.previous();
                // Add the complete stack trace from the next AllocationSite
                // (because that's accurate)
                final AllocationSite.Location[] nextTrace = nextSite.getCallSites();
                if ((target = MethodName.tryCreate(nextTrace[0].getSignature())) == null) {
                    break;
                }
                if (!call.getCaller().equals(target)) {
                    // Last allocation site used for dynamic caller lookup
                    // doesn't match actual caller.
                    // This means that there happened to be a sequence of
                    // allocations that matched a call, but the last allocation
                    // (and
                    // likely others too) was in the wrong method.
                    frames.remove(frames.size() - 1);
                    break;
                }
                for (int j = 1; j < nextTrace.length; j++) {
                    final MethodName caller = MethodName.tryCreate(nextTrace[j].getSignature());
                    final CallSite tmp;
                    if (caller == null || (tmp = cca.findCall(caller, nextTrace[j].getBci(), target)) == null) {
                        // Bail out in case we encounter an invalid name or
                        // can't find a CallSite for a call we know happened
                        break outer;
                    }
                    frames.add(tmp);
                    target = caller;
                }
                assert target.equals(ArraysUtil.last(nextSite.getCallSites()).getSignature());
            } else if (call.getCaller().equals(target)) {
                // Direct recursion and no allocations consumed
                break;
            } else {
                nextSite = null;
                target = call.getCaller();
            }
        } // outer
        if (!ctx.hasNext()) {
            // This might happen for many allocations in the same method or if
            // we have a very deep stack (or a complete stack for that
            // matter)
            allocationContextExhausted.increment();
        }
        if (!frames.isEmpty()) {
            assert frames.size() > uniqueCount : "Stack extended from unique calls only -> SymbolsParser.amendStackTracesStatic(Symbols).";

            final int originalFrames = original.getCallSites().length;
            final AllocationSite.Location[] newTrace = new AllocationSite.Location[originalFrames + frames.size()];
            System.arraycopy(original.getCallSites(), 0, newTrace, 0, originalFrames);
            int j = originalFrames;
            for (CallSite cs : frames) {
                newTrace[j++] = new AllocationSite.Location(cs.getCallerName(), cs.getCallIndex());
            }
            return newTrace;
        }
        return null;
    }

    /**
     * Create a dynamic allocation site.
     *
     * @param original The original allocation site.
     * @param newTrace The new stack trace, or {@code null}.
     * @return The new allocation site, or {@code null}.
     */
    private AllocationSite createDynamicAllocationSite(AllocationSite original, AllocationSite.Location[] newTrace) {
        if (newTrace == null) {
            return null;
        }
        LOGGER.fine(() -> {
            final String msg = "Extended stack trace of allocation site %7d by %d frames.";
            return String.format(msg, original.getId(), newTrace.length - original.getCallSites().length);
        });
        return sites.copy(original, newTrace);
    }

    public boolean isDynamicallyExtendableSite(int id) {
        return dynamicallyExtendableSites.get(id);
    }

    /**
     * Try to add missing methods to the stack traces of all allocation sites using call context information}.
     */

    public void amendStackTracesStatic() {
        assert useStaticCallContext();
        long siteCount = 0;
        long methodCount = 0;
        long newSiteCount = 0;
        long newMethodCount = 0;
        long time = System.currentTimeMillis();
        for (AllocationSite site : sites) {
            if (site != null) {
                siteCount++;
                methodCount += site.getCallSites().length;
                final MethodName topMethodName;
                try {
                    topMethodName = MethodName.create(ArraysUtil.last(site.getCallSites()).getSignature());
                } catch (IllegalArgumentException ex) {
                    LOGGER.info("Could not amend stack trace for method: " + ex.getMessage());
                    continue;
                }
                final List<CallSite> trace = cca.getUniqueStackTrace(topMethodName);
                if (!trace.isEmpty()) {
                    final AllocationSite.Location[] newSites = trace.stream()
                                                                    .map(cs -> new AllocationSite.Location(cs.getCallerName(), cs.getCallIndex()))
                                                                    .toArray(AllocationSite.Location[]::new);
                    final AllocationSite newSite = site.extendStatic(newSites);
                    sites.add(newSite);
                    newSiteCount++;
                    newMethodCount += newSites.length;
                    // Waaay too much output
                    // LOGGER.finest(() -> String.format("Added new call sites to allocation site %d:%n%s%n-> added %s", site.id, site,
                    // Arrays.toString(newSites)));
                }
            }
        } // for(AllocationSite : symbols.sites)
        time = System.currentTimeMillis() - time;
        LOGGER.info(String.format("Added %,d new stack trace elements (%,d existing) to %,d (out of %,d) allocation sites, took %,dms.",
                                  newMethodCount,
                                  methodCount,
                                  newSiteCount,
                                  siteCount,
                                  time));
    }

    public boolean compressedOopsUsed() {
        // TODO: Write this metric into the symbols file and read it!
        return true;
    }

    public void addDataStructureDefinitionFiles(Collection<URI> dataStructureDefinitionFile) {
        if (dataStructureDefinitionFiles.addAll(dataStructureDefinitionFile)) {
            List<DSLDSPartDesc> descriptions = DataStructureUtil.INSTANCE.parseDataStructureDefinitionFiles(dataStructureDefinitionFiles);
            DataStructureUtil.INSTANCE.resolveDescriptionsAndStoreDefinitionsInTypes(types, descriptions);
        }
    }

    public Collection<URI> getDataStructureDefinitionFiles() {
        return Collections.unmodifiableSet(dataStructureDefinitionFiles);
    }

    public void freeCallContextMemory() {
        System.gc();
        cca = null;
        System.gc();
    }
}
