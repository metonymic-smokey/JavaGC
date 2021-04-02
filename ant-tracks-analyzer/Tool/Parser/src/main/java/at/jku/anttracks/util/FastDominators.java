package at.jku.anttracks.util;

import at.jku.anttracks.heap.IndexBasedHeap;

import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Stack;
import java.util.function.Function;
import java.util.function.Supplier;

import static at.jku.anttracks.heap.IndexBasedHeap.NULL_INDEX;

/**
 * Based on the Lengauer-Tarjan algorithm presented in 'Modern Compiler Implementation in Java'
 */

public class FastDominators {

    private static final int SUPER_ROOT_INDEX = 0;  // consequently all object indices are shifted by 1 in this algorithm
    // 'none' in algorithm is NULL_INDEX i.e. -1

    private final Function<Integer, int[]> successorFunction;
    private final Function<Integer, int[]> predecessorFunction;
    private final Function<Integer, Boolean> isReachableFunction;
    private final int[] entryNodes;
    private final BitSet entryNodesSet;

    private final int nodeCount;
    private int N = 0;
    private int[] dfnum;
    private int[] vertex;
    private int[] parent;

    private HashSet<?>[] bucket;  // HashSet<Integer> array not allowed...
    private int[] semi;
    private int[] ancestor;
    private int[] idom;
    private int[] samedom;

    private int[] best;

    private int[][] dominated;

    // TODO check for correctness, eg invested some time into this, we think it should be correct now
    // A unit test could help
    public FastDominators(Function<Integer, int[]> successorFunction,
                          Function<Integer, int[]> predecessorFunction,
                          Function<Integer, Boolean> isReachableFunction,
                          int[] entryNodes,
                          int nodeCount) {
        this.successorFunction = successorFunction;
        this.predecessorFunction = predecessorFunction;
        this.isReachableFunction = isReachableFunction;
        this.entryNodes = Arrays.stream(entryNodes)
                                .filter(i -> i != NULL_INDEX)
                                .distinct()
                                .toArray();
        this.entryNodesSet = new BitSet();
        Arrays.stream(this.entryNodes)
              .forEach(entryNodesSet::set);

        this.nodeCount = nodeCount += 1;

        N = 0;
        dfnum = new int[nodeCount];
        vertex = new int[nodeCount];
        parent = new int[nodeCount];
        bucket = new HashSet<?>[nodeCount];
        semi = new int[nodeCount];
        ancestor = new int[nodeCount];
        idom = new int[nodeCount];
        samedom = new int[nodeCount];
        best = new int[nodeCount];

        Arrays.fill(dfnum, 0);
        Arrays.fill(semi, NULL_INDEX);
        Arrays.fill(ancestor, NULL_INDEX);
        Arrays.fill(samedom, NULL_INDEX);
        Arrays.fill(idom, NULL_INDEX);
        Arrays.fill(best, NULL_INDEX);

        //ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("DFS");
        DFS(NULL_INDEX, SUPER_ROOT_INDEX);
        //m.end();

        //m = ApplicationStatistics.getInstance().createMeasurement("calculate idoms");
        calculateDominators();
        //m.end();

        //m = ApplicationStatistics.getInstance().createMeasurement("calculate dominate array");
        complete();
        //m.end();

        assert areIDomsAndDominatedArrayConsistent() : "calculation of 'dominated' array is incorrect!";
    }

    private void calculateDominators() {
        for (int i = N - 1; i >= 1; i--) {
            int n = vertex[i];
            int p = parent[n];
            int s = p;

            if (n != SUPER_ROOT_INDEX) {
                HashSet<Integer> predecessorsClosedSet = new HashSet<>();
                int[] predecessors = predecessorFunction.apply(n - 1);
                if (predecessors != null) {
                    for (int j = 0; j < predecessors.length; j++) {
                        if (predecessors[j] != NULL_INDEX && predecessorsClosedSet.add(predecessors[j])) {
                            int v = predecessors[j] + 1;

                            int sPrime;
                            if (dfnum[v] <= dfnum[n]) {
                                sPrime = v;
                            } else {
                                sPrime = semi[AncestorWithLowestSemi(v)];
                            }
                            if (dfnum[sPrime] < dfnum[s]) {
                                s = sPrime;
                            }
                        }
                    }
                }
            }

            if (entryNodesSet.get(n - 1)) {
                // we are at an entry node => the node has the super root node as additional predecessor
                // => do one more iteration of regular algorithm with predecessor SUPER_ROOT_INDEX
                int v = SUPER_ROOT_INDEX;

                int sPrime;
                if (dfnum[v] <= dfnum[n]) {
                    sPrime = v;
                } else {
                    sPrime = semi[AncestorWithLowestSemi(v)];
                }
                if (dfnum[sPrime] < dfnum[s]) {
                    s = sPrime;
                }
            }

            semi[n] = s;
            // union
            HashSet<Integer> set = (HashSet<Integer>) bucket[s];
            if (set == null) {
                set = new HashSet<>();
                bucket[s] = set;
            }
            set.add(n);
            Link(p, n);

            if (bucket[p] != null) {
                for (int v : (HashSet<Integer>) bucket[p]) {
                    int y = AncestorWithLowestSemi(v);

                    if (semi[y] == semi[v]) {
                        idom[v] = p;
                    } else {
                        samedom[v] = y;
                    }
                }
            }

            bucket[p] = null;
        }

        for (int i = 1; i <= N - 1; i++) {
            int n = vertex[i];
            if (samedom[n] != NULL_INDEX) {
                idom[n] = idom[samedom[n]];
            }
        }
    }

    private void DFS(int startParent, int startNode) {
        Stack<Integer> pStack = new Stack<>();
        Stack<Integer> nStack = new Stack<>();
        BitSet closedSet = new BitSet();

        pStack.push(startParent);
        nStack.push(startNode);

        while (!pStack.isEmpty() && !nStack.isEmpty()) {
            int p = pStack.pop();
            int n = nStack.pop();

            if (closedSet.get(n)) {
                // checking this condition before pushing on the stack results in a different DFS visit order and incorrectly calculated dominators
                // checking it this late however means that the stacks might grow too big...
                // => TODO something about this
                continue;
            }

            closedSet.set(n);
            dfnum[n] = N;
            vertex[N] = n;
            parent[n] = p;
            N = N + 1;

            int[] successors = n == SUPER_ROOT_INDEX ?
                               entryNodes :
                               successorFunction.apply(n - 1);
            if (successors != null) {
                for (int w : successors) {
                    if (w != NULL_INDEX) {
                        pStack.push(n);
                        nStack.push(w + 1);
                    }
                }
            }
        }
    }

    private int AncestorWithLowestSemiNaive(int v) {
        int u = v;

        while (ancestor[v] != NULL_INDEX) {
            if (dfnum[semi[v]] < dfnum[semi[u]]) {
                u = v;
            }
            v = ancestor[v];
        }

        return u;
    }

    private void LinkNaive(int p, int n) {
        ancestor[n] = p;
    }

    private int AncestorWithLowestSemi(int v) {
        int a = ancestor[v];

        if (ancestor[a] != NULL_INDEX) {
            int b = AncestorWithLowestSemi(a);
            ancestor[v] = ancestor[a];

            if (dfnum[semi[b]] < dfnum[semi[best[v]]]) {
                best[v] = b;
            }
        }

        return best[v];
    }

    private void Link(int p, int n) {
        ancestor[n] = p;
        best[n] = n;
    }

    private void complete() {
        // make no longer needed data eligible for garbage collection
        dfnum = null;
        vertex = null;
        parent = null;
        bucket = null;
        semi = null;
        ancestor = null;
        samedom = null;
        best = null;

        // build data structure for immediate access to dominated nodes (i.e., turn the dom tree upside down)
        // note that this data structure does not account for the super root node! (leads to quicker access, i.e., indices do not have to be incremented)
        dominated = new int[nodeCount - 1][];

        // first calculate how many nodes are dominated by each node such that the sub arrays in 'dominated' only have to instantiated once
        int[] dominatedCount = new int[nodeCount - 1];
        for (int i = 1; i < idom.length; i++) {
            // the super root has no entry in 'dominated'
            if (idom[i] > 0) {
                dominatedCount[idom[i] - 1]++;
            }
        }

        // now instantiate the sub arrays
        for (int i = 0; i < dominated.length; i++) {
            if (dominatedCount[i] > 0) {
                dominated[i] = new int[dominatedCount[i]];

                // reset the previously calculated array size entry, it will now be used to hold a top pointer (necessary when gradually filling each subarray)
                dominatedCount[i] = 0;
            }
        }

        // now iterate once more over immediate dominators, filling the 'dominated' subarrays
        for (int i = 1; i < idom.length; i++) {
            if (idom[i] > 0) {
                dominated[idom[i] - 1][dominatedCount[idom[i] - 1]++] = i - 1;
            }
        }
    }

    private boolean areIDomsAndDominatedArrayConsistent() {
        // check whether the 'dominated' array is consistent with the 'idom' array
        for (int i = 0; i < dominated.length; i++) {
            int finalI = i;
            if (dominated[i] != null && !Arrays.stream(dominated[i]).allMatch(idx -> getImmediateDominator(idx) == finalI)) {
                return false;
            }
        }

        return true;
    }

    // --------------------------------
    // ACTUAL INTERFACE
    // --------------------------------

    /**
     * Returns the immediate dominator of a given object
     *
     * @param n the index of an object in the heap
     * @return the index of the object that immediately dominates the given object. If the given object is immediately dominated by the super root object, -2 is returned.
     */
    public int getImmediateDominator(int n) {
        if (!isReachableFunction.apply(n)) {
            // nodes that are not reachable from the super root don't have an immediate dominator
            return NULL_INDEX;
        }

        return idom[n + 1] == SUPER_ROOT_INDEX ?
               IndexBasedHeap.SUPER_ROOT_INDEX :
               idom[n + 1] - 1;
    }

    public int[] getImmediatelyDominated(int n) {
        return dominated[n];
    }
}
