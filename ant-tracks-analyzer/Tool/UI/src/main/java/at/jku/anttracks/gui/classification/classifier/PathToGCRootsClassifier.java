package at.jku.anttracks.gui.classification.classifier;

import at.jku.anttracks.classification.annotations.C;
import at.jku.anttracks.classification.annotations.ClassifierProperty;
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection;
import at.jku.anttracks.classification.enumerations.ClassifierType;
import at.jku.anttracks.heap.roots.RootPtr;

import java.util.List;

@C(name = "GC Roots: Path",
        desc = "This classifier distinguishes objects based on the path to their root pointers",
        example = "static root 'String lineSeparator' in java.lang.System",
        type = ClassifierType.MANY_HIERARCHY,
        collection = ClassifierSourceCollection.FASTHEAP)
public class PathToGCRootsClassifier extends AbstractRootClassifier {

    @ClassifierProperty(overviewLevel = 10)
    private int maxDepth = 500;

    public int getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    @ClassifierProperty(overviewLevel = 10)
    private boolean showPackageOfPathObjectsType = false;

    public boolean getShowPackageOfPathObjectsType() {
        return showPackageOfPathObjectsType;
    }

    public void setShowPackageOfPathObjectsType(boolean showPackageOfPathObjectsType) {
        this.showPackageOfPathObjectsType = showPackageOfPathObjectsType;
    }

    @ClassifierProperty(overviewLevel = 10)
    private boolean showAllocationSiteOfPathObjects = false;

    public boolean getShowAllocationSiteOfPathObjects() {
        return showAllocationSiteOfPathObjects;
    }

    public void setShowAllocationSiteOfPathObjects(boolean showAllocationSiteOfPathObjects) {
        this.showAllocationSiteOfPathObjects = showAllocationSiteOfPathObjects;
    }

    @ClassifierProperty(overviewLevel = 10)
    private boolean onlyShowVariables = true;

    public boolean getOnlyShowVariables() {return onlyShowVariables;}

    public void setOnlyShowVariables(boolean b) {
        onlyShowVariables = b;
        notDirectlyPointedString = onlyShowVariables ? "Not referenced by any variable" : "Not referenced by any GC root";
    }

    String notDirectlyPointedString = "Not referenced by any GC root";

    @ClassifierProperty(overviewLevel = 10)
    private boolean onlyShowClosest = false;

    public boolean getOnlyShowClosest() {return onlyShowClosest;}

    public void setOnlyShowClosest(boolean b) {
        onlyShowClosest = b;
    }

    @Override
    public RootKey[][] classify() throws Exception {
        List<RootPtr.RootInfo> rootInfos = onlyShowClosest ? traceClosestRoots(maxDepth, onlyShowVariables) : traceIndirectRootPointers(maxDepth, onlyShowVariables);

        return new RootKey[][]{{new RootKey(RootNodeIcon.NO_ROOT, "TODO this classifier is disabled!")}};
//        if (rootInfos == null || rootInfos.size() == 0) {
//            return new RootKey[][]{{new RootKey(RootNodeIcon.NO_ROOT, notDirectlyPointedString)}};
//        }
//
//        return super.hierarchy(rootInfos, showPackageOfPathObjectsType, showAllocationSiteOfPathObjects);
    }
}
