package at.jku.anttracks.util;

import java.util.function.Consumer;

public class IterationUtil {
    public interface Consumer2<A, B> {
        void consume(A a, B b);
    }

    public interface Consumer3<A, B, C> {
        void consume(A a, B b, C c);
    }

    public interface Consumer4<A, B, C, D> {
        void consume(A a, B b, C c, D d);
    }

    public interface Consumer5<A, B, C, D, E> {
        void consume(A a, B b, C c, D d, E e);
    }

    public interface Consumer6<A, B, C, D, E, F> {
        void consume(A a, B b, C c, D d, E e, F f);
    }

    public interface Consumer7<A, B, C, D, E, F, G> {
        void consume(A a, B b, C c, D d, E e, F f, G g);
    }

    public interface Consumer8<A, B, C, D, E, F, G, H> {
        void consume(A a, B b, C c, D d, E e, F f, G g, H h);
    }

    public static <A> void iterate(Iterable<A> i1,
                                   Consumer<A> consumer) {
        i1.forEach(consumer);
    }

    public static <A, B> void iterate(Iterable<A> i1,
                                      Iterable<B> i2,
                                      Consumer2<A, B> consumer) {
        i2.iterator().forEachRemaining(b -> iterate(i1, (a) -> consumer.consume(a, b)));
    }

    public static <A, B, C> void iterate(Iterable<A> i1,
                                         Iterable<B> i2,
                                         Iterable<C> i3,
                                         Consumer3<A, B, C> consumer) {
        i3.iterator().forEachRemaining(c -> iterate(i1, i2, (a, b) -> consumer.consume(a, b, c)));
    }

    public static <A, B, C, D> void iterate(Iterable<A> i1,
                                            Iterable<B> i2,
                                            Iterable<C> i3,
                                            Iterable<D> i4,
                                            Consumer4<A, B, C, D> consumer) {
        i4.iterator().forEachRemaining(d -> iterate(i1, i2, i3, (a, b, c) -> consumer.consume(a, b, c, d)));
    }

    public static <A, B, C, D, E> void iterate(Iterable<A> i1,
                                               Iterable<B> i2,
                                               Iterable<C> i3,
                                               Iterable<D> i4,
                                               Iterable<E> i5,
                                               Consumer5<A, B, C, D, E> consumer) {
        i5.iterator().forEachRemaining(e -> iterate(i1, i2, i3, i4, (a, b, c, d) -> consumer.consume(a, b, c, d, e)));
    }

    public static <A, B, C, D, E, F> void iterate(Iterable<A> i1,
                                                  Iterable<B> i2,
                                                  Iterable<C> i3,
                                                  Iterable<D> i4,
                                                  Iterable<E> i5,
                                                  Iterable<F> i6,
                                                  Consumer6<A, B, C, D, E, F> consumer) {
        i6.iterator().forEachRemaining(f -> iterate(i1, i2, i3, i4, i5, (a, b, c, d, e) -> consumer.consume(a, b, c, d, e, f)));
    }

    public static <A, B, C, D, E, F, G> void iterate(Iterable<A> i1,
                                                     Iterable<B> i2,
                                                     Iterable<C> i3,
                                                     Iterable<D> i4,
                                                     Iterable<E> i5,
                                                     Iterable<F> i6,
                                                     Iterable<G> i7,
                                                     Consumer7<A, B, C, D, E, F, G> consumer) {
        i7.iterator().forEachRemaining(g -> iterate(i1, i2, i3, i4, i5, i6, (a, b, c, d, e, f) -> consumer.consume(a, b, c, d, e, f, g)));
    }

    public static <A, B, C, D, E, F, G, H> void iterate(Iterable<A> i1,
                                                        Iterable<B> i2,
                                                        Iterable<C> i3,
                                                        Iterable<D> i4,
                                                        Iterable<E> i5,
                                                        Iterable<F> i6,
                                                        Iterable<G> i7,
                                                        Iterable<H> i8,
                                                        Consumer8<A, B, C, D, E, F, G, H> consumer) {
        i8.iterator().forEachRemaining(h -> iterate(i1, i2, i3, i4, i5, i6, i7, (a, b, c, d, e, f, g) -> consumer.consume(a, b, c, d, e, f, g, h)));
    }
}
