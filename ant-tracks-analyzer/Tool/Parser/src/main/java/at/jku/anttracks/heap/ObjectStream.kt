
package at.jku.anttracks.heap

import at.jku.anttracks.classification.Classifier
import at.jku.anttracks.classification.ClassifierChain
import at.jku.anttracks.classification.Filter
import at.jku.anttracks.classification.nodes.MapGroupingNode
import java.util.stream.Stream

interface ObjectStream {
    interface ThreadVisitorGenerator<I : ObjectVisitor> {
        fun generate(): I
    }

    interface IterationListener {
        fun objectsIterated(objectCount: Long)
    }

    /**
     * Returns a collection containing all [HeapObjectFilter]s to apply
     *
     * @return A collection containing all [HeapObjectFilter]s to apply
     */
    //    Collection<HeapObjectFilter> getFilter();

    /**
     * Adds the given [IterationListener].
     *
     * @param ll The [IterationListener] to add
     */
    fun addLabListener(ll: IterationListener)

    /**
     * Removes the given [IterationListener]. If the [IterationListener] is not
     * present, nothing happens.
     *
     * @param ll The [IterationListener] to remove
     */
    fun removeLabListener(ll: IterationListener)

    /**
     * Adds the given filters to the filter queue
     *
     * @param filter The filters to add
     * @return The [ObjectStream] with the given filters added
     */
    fun filter(vararg filter: Filter): ObjectStream

    /**
     * Sorts the stream by its default sorting parameter
     */
    fun sorted(): ObjectStream

    /**
     * Groups single-threaded according to the given classifiers, taking into
     * account all filters in the filter queue
     *
     * @param classifier            Classifiers applied to all objects
     * @param conditionalClassifier Classifiers applied to a certain subgroup of objects
     * @return The resulting grouping
     */
    fun groupMap(classifier: ClassifierChain, addFilterNodeInTree: Boolean): MapGroupingNode

    /**
     * Groups multi-threaded according to the given classifiers, taking into
     * account all filters in the filter queue
     *
     * @param classifier            Classifiers applied to all objects
     * @param conditionalClassifier Classifiers applied to a certain subgroup of objects
     * @return The resulting grouping
     */
    fun groupMapParallel(classifier: ClassifierChain, addFilterNodeInTree: Boolean): MapGroupingNode

    /**
     * Visits all objects which are not excluded by at least one filter in the
     * filter queue
     *
     * @param visitor The visitor to be executed on each visited object
     */
    fun forEach(visitor: ObjectVisitor, visitorSettings: ObjectVisitor.Settings)

    fun <I : ObjectVisitor> forEachParallel(threadLocalDataGenerator: ThreadVisitorGenerator<I>, visitorSettings: ObjectVisitor.Settings): List<I>

    fun <A> map(classifier: Classifier<A>): Stream<A>
}
