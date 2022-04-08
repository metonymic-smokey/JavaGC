package at.jku.anttracks.gui.classification.classifier;

import at.jku.anttracks.classification.ClassifierException;
import at.jku.anttracks.classification.annotations.C;
import at.jku.anttracks.classification.annotations.ClassifierProperty;
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection;
import at.jku.anttracks.classification.enumerations.ClassifierType;
import at.jku.anttracks.heap.roots.RootPtr;

import java.util.List;
import java.util.stream.Collectors;

@C(name = "GC Roots: Indirect",
        desc = "This classifier distinguishes objects based on all roots that directly or indirectly reference them",
        example = "static root 'String lineSeparator' in java.lang.System",
        type = ClassifierType.MANY_HIERARCHY,
        collection = ClassifierSourceCollection.FASTHEAP)
public class IndirectGCRootsClassifier extends AbstractRootClassifier {
    @ClassifierProperty(overviewLevel = 10)
    private int maxDepth = 500;

    public int getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    @ClassifierProperty(overviewLevel = 10)
    private boolean showCallStack = false;

    public boolean getShowCallStack() {
        return showCallStack;
    }

    public void setShowCallStack(boolean showCallStack) {
        this.showCallStack = showCallStack;
    }

    @ClassifierProperty(overviewLevel = 10)
    private boolean onlyShowVariables = true;

    public boolean getOnlyShowVariables() {return onlyShowVariables;}

    public void setOnlyShowVariables(boolean b) {
        onlyShowVariables = b;
        notDirectlyPointedString = onlyShowVariables ? "Not referenced by any variable in given max depth" : "Not referenced by any GC root in given max depth";
    }

    @ClassifierProperty(overviewLevel = 10)
    private boolean showPackages = false;

    public boolean getShowPackages() {return showPackages;}

    public void setShowPackages(boolean showPackages) {
        this.showPackages = showPackages;
    }

    String notDirectlyPointedString = "Not referenced by any GC root in given max depth";

    @Override
    public RootKey[][] classify() throws ClassifierException {
        List<RootPtr> roots = indirectGCRoots(maxDepth);

        if (roots == null || roots.size() == 0) {
            return new RootKey[][]{{new RootKey(RootNodeIcon.NO_ROOT, notDirectlyPointedString)}};
        }

        if (onlyShowVariables) {
            roots = roots.stream().filter(r -> r.getRootType().isVariable).collect(Collectors.toList());
        }

        return super.hierarchy(roots, showCallStack, showPackages);
    }
}
