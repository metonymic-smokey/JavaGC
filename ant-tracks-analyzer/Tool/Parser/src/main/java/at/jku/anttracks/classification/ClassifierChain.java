
package at.jku.anttracks.classification;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ClassifierChain {

    private final ArrayList<Classifier<?>> classifiers;
    private ClassifierChain tail = null;

    public ClassifierChain(ClassifierChain chain) {
        this(chain.getList());
    }

    public ClassifierChain(Classifier<?>... classifiersToCombine) {
        classifiers = new ArrayList<>();
        for (Classifier<?> c : classifiersToCombine) {
            if (c != null) {
                classifiers.add(c);
            }
        }
    }

    public ClassifierChain(List<Classifier<?>> classifiersToCombine) {
        classifiers = new ArrayList<>();
        for (Classifier<?> c : classifiersToCombine) {
            if (c != null) {
                classifiers.add(c);
            }
        }
    }

    public ClassifierChain followedBy(Classifier<?> next) {
        classifiers.add(next);
        return this;
    }

    public Classifier<?> get(int i) {
        return classifiers.get(i);
    }

    public ClassifierChain dropFirst() {
        if (tail == null) {
            tail = new ClassifierChain(classifiers.stream().skip(1).collect(Collectors.toList()));
        }
        return tail;
    }

    public int length() {
        return classifiers.size();
    }

    public List<Classifier<?>> getList() {
        return classifiers;
    }

    public Classifier<?> getLast() {
        int size = classifiers.size();
        if (size > 0) {
            return classifiers.get(size - 1);
        } else {
            return null;
        }
    }

    public void clear() {
        classifiers.clear();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object clone() {
        return new ClassifierChain((ArrayList<Classifier<?>>) classifiers.clone());
    }

    @Override
    public int hashCode() {
        return classifiers.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ClassifierChain) {
            return classifiers.equals(((ClassifierChain) obj).classifiers);
        }
        return false;
    }

    public void forEach(Consumer<Classifier<?>> operation) {
        for (int i = 0; i < length(); i++) {
            operation.accept(get(i));
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        forEach(oc -> sb.append("(" + oc.toString() + ")" + " -> "));

        if (sb.length() > 0) {
            sb.deleteCharAt(sb.length() - 1);
            sb.deleteCharAt(sb.length() - 1);
            sb.deleteCharAt(sb.length() - 1);
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }
}
