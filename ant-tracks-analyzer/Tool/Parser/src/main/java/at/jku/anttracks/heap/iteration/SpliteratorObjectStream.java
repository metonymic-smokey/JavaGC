package at.jku.anttracks.heap.iteration;

import at.jku.anttracks.classification.Classifier;
import at.jku.anttracks.classification.ClassifierChain;
import at.jku.anttracks.classification.Filter;
import at.jku.anttracks.classification.nodes.MapGroupingNode;
import at.jku.anttracks.heap.DetailedHeap;
import at.jku.anttracks.heap.ObjectStream;
import at.jku.anttracks.heap.ObjectVisitor;
import at.jku.anttracks.util.Consts;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SpliteratorObjectStream implements ObjectStream {
    private final DetailedHeap heap;
    private final List<Filter> filters;
    private final List<IterationListener> listeners;

    private final FakeHeapSpliterator spliterator;
    private final long multiThreadingThreshold;

    ExecutorService executorService;
    Logger LOGGER = Logger.getLogger(SpliteratorObjectStream.class.getSimpleName());

    public SpliteratorObjectStream(DetailedHeap heap, FakeHeapSpliterator spliterator, long multithreadingThreshold) {
        this(heap, spliterator, multithreadingThreshold, new ArrayList<>(), new ArrayList<>(), Executors.newFixedThreadPool(Consts.getAVAILABLE_PROCESSORS() * 2));
    }

    public SpliteratorObjectStream(DetailedHeap heap,
                                   FakeHeapSpliterator spliterator,
                                   long multithreadingThreshold,
                                   List<Filter> filters,
                                   List<IterationListener> listeners,
                                   ExecutorService executorService) {
        this.heap = heap;
        this.filters = filters;
        this.listeners = listeners;
        this.spliterator = spliterator;
        this.spliterator.setListener(listeners);
        multiThreadingThreshold = multithreadingThreshold;
        this.executorService = executorService;
    }

    @Override
    public ObjectStream sorted() {
        spliterator.setSorted(true);
        return this;
    }

    @Override
    public ObjectStream filter(Filter... filter) {
        for (Filter of : filter) {
            this.filters.add(of);
        }
        return this;
    }

    @Override
    public MapGroupingNode groupMap(ClassifierChain classifier, boolean addFilterNodeInTree) {
        ThreadLocalHeapObjectMapGroupingVisitor grouping = new ThreadLocalHeapObjectMapGroupingVisitor(heap, classifier, filters, addFilterNodeInTree);
        forEach(grouping, ObjectVisitor.Settings.Companion.getALL_INFOS());
        return grouping;
    }

    @Override
    public MapGroupingNode groupMapParallel(ClassifierChain classifier, boolean addFilterNodeInTree) {
        List<ThreadLocalHeapObjectMapGroupingVisitor> returns = forEachParallel(() -> new ThreadLocalHeapObjectMapGroupingVisitor(heap, classifier, filters, addFilterNodeInTree),
                                                                                ObjectVisitor.Settings.Companion.getALL_INFOS());
        MapGroupingNode finalGroup = returns.stream().map(x -> (MapGroupingNode) x).reduce((a, b) -> (MapGroupingNode) a.merge(b)).get();
        finalGroup.sampleTopDown(null);
        return finalGroup;
    }

    @Override
    public void forEach(ObjectVisitor visitor, ObjectVisitor.Settings vistitorSettings) {
        ObjectVisitor filteredVisitor = null;

        if (filters != null && filters.size() > 0) {
            filteredVisitor = (address, object, space, rootPtrs) -> {
                for (int i = 0; i < filters.size(); i++) {
                    try {
                        if (!filters.get(i)
                                    .classify(
                                            object,
                                            address,
                                            object.getInfo(),
                                            space,
                                            object.getType(),
                                            object.getSize(),
                                            object.isArray(),
                                            object.getArrayLength(),
                                            object.getSite(),
                                            null, // TODO Pointer
                                            null, // TODO Pointer
                                            object.getEventType(),
                                            rootPtrs,
                                            -1,// TODO Age
                                            object.getInfo().thread,
                                            heap.getExternalThreadName(object.getInfo().thread))) {
                            return;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        return;
                    }
                }

                visitor.visit(address, object, space, rootPtrs);
            };
        } else {
            filteredVisitor = visitor;
        }

        while (spliterator.tryAdvance(filteredVisitor, vistitorSettings)) {
            // Execute visitor on every object
        }
    }

    @Override
    public <I extends ObjectVisitor> List<I> forEachParallel(ThreadVisitorGenerator<I> threadLocalVisitorGenerator,
                                                             ObjectVisitor.Settings vistitorSettings) {
        List<Future<I>> futures = collectFutures(threadLocalVisitorGenerator, spliterator, vistitorSettings);

        try {
            // Wait "forever" until executor service finished all tasks
            executorService.shutdown();
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        List<I> returns = futures.stream().map(future -> {
            try {
                return future.get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
            return null;
        }).filter(x -> x != null).collect(Collectors.toList());
        return returns;
    }

    private <I extends ObjectVisitor> List<Future<I>> collectFutures(ThreadVisitorGenerator<I> threadLocalVisitorGenerator,
                                                                     FakeHeapSpliterator s,
                                                                     ObjectVisitor.Settings vistitorSettings) {
        List<Future<I>> futuresList = new ArrayList<>();

        FakeHeapSpliterator rightSpliterator = null;

        if (s.size() > multiThreadingThreshold) {
            // LOGGER.log(Level.INFO, String.format("Trying to split spliterator %s (size %,d)", s.toString(), s.size()));
            rightSpliterator = s.trySplit();
        }

        if (rightSpliterator != null) {
            // LOGGER.log(Level.INFO, String.format("Splitted into %s (size %,d) and %s (size %,d), try to further split both",
            // s.toString(), s.size(), rightSpliterator.toString(), rightSpliterator.size()));
            futuresList.addAll(collectFutures(threadLocalVisitorGenerator, s, vistitorSettings));
            futuresList.addAll(collectFutures(threadLocalVisitorGenerator, rightSpliterator, vistitorSettings));
        } else {
            // LOGGER.log(Level.INFO, String.format("Didn't split %s (size %,d), add to working list", s.toString(), s.size()));
            futuresList.add(executorService.submit(() -> {
                // LOGGER.log(Level.INFO, String.format("Started running %s (size %,d)", s, s.size()));
                I visitor = threadLocalVisitorGenerator.generate();
                new SpliteratorObjectStream(heap, s, multiThreadingThreshold, filters, listeners, executorService).forEach(visitor, vistitorSettings);
                return visitor;
            }));
        }
        return futuresList;
    }

    @Override
    public <A> Stream<A> map(Classifier<A> classifier) {
        List<A> ret = new ArrayList<>();
        forEach((address, object, space, rootPtrs) -> {
            try {
                ret.add(classifier.classify(
                        object,
                        address,
                        object.getInfo(),
                        space,
                        object.getType(),
                        object.getSize(),
                        object.isArray(),
                        object.getArrayLength(),
                        object.getSite(),
                        null, // TODO Pointer
                        null, // TODO Pointer
                        object.getEventType(),
                        rootPtrs,
                        -1, // TODO Age
                        object.getInfo().thread,
                        heap.getExternalThreadName(object.getInfo().thread)));
            } catch (Exception e) {
            }
        }, ObjectVisitor.Settings.Companion.getALL_INFOS());
        return ret.stream();
    }

    @Override
    public void addLabListener(IterationListener ll) {
        listeners.add(ll);
    }

    @Override
    public void removeLabListener(IterationListener ll) {
        listeners.remove(ll);
    }
}