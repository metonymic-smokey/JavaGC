package at.jku.anttracks.gui.classification.classifier;

import at.jku.anttracks.classification.annotations.C;
import at.jku.anttracks.classification.annotations.ClassifierProperty;
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection;
import at.jku.anttracks.classification.enumerations.ClassifierType;
import at.jku.anttracks.heap.roots.RootPtr;
import at.jku.anttracks.util.TraceException;

import java.util.List;
import java.util.stream.Collectors;

@C(name = "GC Roots: Direct",
        desc = "This classifier distinguishes objects based on the GC root that directly references them",
        example = "static root 'String lineSeparator' in java.lang.System",
        type = ClassifierType.MANY_HIERARCHY,
        collection = ClassifierSourceCollection.ALL)
public class DirectGCRootsClassifier extends AbstractRootClassifier {
    @ClassifierProperty(overviewLevel = 10)
    private boolean displayIndirectlyRootPointedGroup = false;

    public void setDisplayIndirectlyRootPointedGroup(boolean displayIndirectlyRootPointedGroup) {
        this.displayIndirectlyRootPointedGroup = displayIndirectlyRootPointedGroup;
    }

    public boolean getDisplayIndirectlyRootPointedGroup() {
        return displayIndirectlyRootPointedGroup;
    }

    @ClassifierProperty(overviewLevel = 10)
    private boolean onlyShowVariables = true;

    public boolean getOnlyShowVariables() {return onlyShowVariables;}

    public void setOnlyShowVariables(boolean b) {
        onlyShowVariables = b;
        notDirectlyPointedString = onlyShowVariables ? "Not directly referenced by any variable" : "Not directly referenced by any GC root";
    }

    String notDirectlyPointedString = "Not directly referenced by any GC root";

    @ClassifierProperty(overviewLevel = 10)
    private boolean showCallStack = false;

    public boolean getShowCallStack() {
        return showCallStack;
    }

    public void setShowCallStack(boolean showCallStack) {
        this.showCallStack = showCallStack;
    }

    @ClassifierProperty(overviewLevel = 10)
    private boolean showPackages = false;

    public boolean getShowPackages() {
        return showPackages;
    }

    public void setShowPackages(boolean showPackages) {
        this.showPackages = showPackages;
    }

    @Override
    public RootKey[][] classify() throws TraceException {
        List<? extends RootPtr> roots = rootPointers();

        if (roots == null || roots.size() == 0) {
            return displayIndirectlyRootPointedGroup ?
                   new RootKey[][]{{new RootKey(RootNodeIcon.ROOT, notDirectlyPointedString)}} :
                   null;
        }

        if (onlyShowVariables) {
            roots = roots.stream().filter(r -> r.getRootType().isVariable).collect(Collectors.toList());

            if (roots.size() == 0) {
                return null;
            }
        }

        return super.hierarchy(roots, showCallStack, showPackages);
    }

}
