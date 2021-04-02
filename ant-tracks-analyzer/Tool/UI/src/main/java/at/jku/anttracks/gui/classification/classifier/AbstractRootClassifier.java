package at.jku.anttracks.gui.classification.classifier;

import at.jku.anttracks.classification.Classifier;
import at.jku.anttracks.gui.utils.ImageUtil;
import at.jku.anttracks.heap.roots.LocalVariableRoot;
import at.jku.anttracks.heap.roots.RootPtr;
import at.jku.anttracks.util.ImagePack;
import javafx.scene.image.ImageView;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractRootClassifier extends Classifier<AbstractRootClassifier.RootKey[][]> {

    /**
     * This class is used to give different icons to different root types
     */
    public class RootKey {
        public final RootNodeIcon icon;
        public final String name;

        public RootKey(RootNodeIcon icon, String key) {
            this.icon = icon;
            this.name = key;
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (!RootKey.class.isAssignableFrom(obj.getClass())) {
                return false;
            }
            final RootKey other = (RootKey) obj;
            if ((this.name == null) ? (other.name != null) : !this.name.equals(other.name)) {
                return false;
            }
            return this.icon == other.icon;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 53 * hash + (this.name != null ? this.name.hashCode() : 0);
            hash = 53 * hash + this.icon.index;
            return hash;
        }

        public int iconIndex() {
            return icon.index;
        }
    }

    public enum RootNodeIcon {
        ROOT(0, "Root", "root.png"),
        // note: classifier symbol must be first!
        LOCAL_VAR(1, "Local variable", "local_var.png"),
        METHOD(2, "Method", "method.png"),
        PATH(3, "Path", "path.png"),
        STATIC_FIELD(4, "Static field", "static_field.png"),
        THREAD(5, "Thread", "thread.png"),
        CLASS(6, "Class", "root_type.png"),
        CLASS_LOADER(7, "Class loader", "classloader.png"),
        NO_ROOT(8, "No root", "no_root.png");

        public final int index;
        public final String path;
        public final String name;

        RootNodeIcon(int index, String name, String path) {
            this.index = index;
            this.name = name;
            this.path = path;
        }
    }

    private static final boolean HIDE_INTERNAL_ROOTS = true;

    private static Map<RootPtr, RootKey[]> rootHierarchyCache = createLRUMap(5_000);    // TODO max entries?
    // TODO how to handle paths/callstacks (they can be enabled/disabled)

    private static <K, V> Map<K, V> createLRUMap(final int maxEntries) {
        // https://stackoverflow.com/questions/11469045/how-to-limit-the-maximum-size-of-a-map-by-removing-oldest-entries-when-limit-rea
        return new LinkedHashMap<K, V>(maxEntries * 10 / 7, 0.7f, true) {
            private static final long serialVersionUID = -6908923527434043327L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maxEntries;
            }
        };
    }

    // Duplicate code for list and arry input parameter for performance reasons
    public RootKey[][] hierarchy(RootPtr searchResult, boolean includeCallStack, boolean includePackages) {
        if (HIDE_INTERNAL_ROOTS && searchResult.isInternal()) {
            return null;
        }

        RootKey[][] ret = new RootKey[1][];

        if (rootHierarchyCache.containsKey(searchResult)) {
            ret[0] = rootHierarchyCache.get(searchResult);
            return ret;
        }

        String[] rootDesc;
        if (searchResult.getRootType() == RootPtr.RootType.LOCAL_VARIABLE_ROOT) {
            rootDesc = ((LocalVariableRoot) searchResult).toClassificationString(includePackages, includeCallStack);
        } else {
            rootDesc = searchResult.toClassificationString(includePackages);
        }
        ret[0] = new RootKey[rootDesc.length];
        insertRootDescAndIcons(searchResult, rootDesc, 0, ret[0]);

        rootHierarchyCache.put(searchResult, ret[0]);

        return ret;
    }

    // Duplicate code for list and arry input parameter for performance reasons
    protected RootKey[][] hierarchy(List<? extends RootPtr> searchResult, boolean includeCallStack, boolean includePackages) {
        //ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("AbstractRootClassifier.hierarchy");
        if (HIDE_INTERNAL_ROOTS) {
            searchResult.removeIf(RootPtr::isInternal);
        }

        RootKey[][] ret = new RootKey[searchResult.size()][];

        for (int i = 0; i < ret.length; i++) {
            ret[i] = rootHierarchyCache.get(searchResult.get(i));

            if (ret[i] == null) {
                String[] rootDesc;
                if (searchResult.get(i).getRootType() == RootPtr.RootType.LOCAL_VARIABLE_ROOT) {
                    rootDesc = ((LocalVariableRoot) searchResult.get(i)).toClassificationString(includePackages, includeCallStack);
                } else {
                    rootDesc = searchResult.get(i).toClassificationString(includePackages);
                }
                ret[i] = new RootKey[rootDesc.length];
                insertRootDescAndIcons(searchResult.get(i), rootDesc, 0, ret[i]);

                rootHierarchyCache.put(searchResult.get(i), ret[i]);
            }
        }
        //m.end();
        return ret;
    }

//    protected RootKey[][] hierarchy(List<RootPtr.RootInfo> searchResult, boolean showPackageOfPathObjectsType, boolean showAllocationSiteOfPathObjects)
//            throws TraceException {
//        RootKey[][] ret = new RootKey[searchResult.size()][];
//
//        // TODO adjust to new RootInfo format (or remove path classifiers altogether)
//        //        for (int i = 0; i < ret.length; i++) {
//        //            String[] rootDesc = searchResult.get(i).ptr.hierarchy();
//        //            ret[i] = new RootKey[1 + searchResult.get(i).path.length - 1 + rootDesc.length];
//        //
//        //            ret[i][0] = new RootKey(RootNodeIcon.PATH, "Paths to root:");
//        //
//        //            for (int j = 0; j < searchResult.get(i).path.length - 1; j++) {
//        //                ObjectInfo pathObj = fastHeap().getObjectInfo(searchResult.get(i).path[j]);
//        //                // j+1 because first entry is 'Path to root'
//        //                ret[i][j + 1] = new RootKey(RootNodeIcon.PATH,
//        //                                            pathObj != null ? pathObj.type.getExternalName(!showPackageOfPathObjectsType,
//        //                                                                                           false) +
//        //                                                    (showAllocationSiteOfPathObjects ? " in " + pathObj.allocationSite.callSites[0].toString() : "") :
//        //                                            "???");
//        //            }
//        //
//        //            int rsi = 0; // root start index ... where does the root description start in the returned array
//        //            rsi = searchResult.get(i).path.length;  // append root desc after path
//        //
//        //            insertRootDescAndIcons(searchResult.get(i).ptr, rootDesc, rsi, ret[i]);
//        //        }
//
//        return ret;
//    }

    private void insertRootDescAndIcons(RootPtr root, String[] rootDesc, int rsi, RootKey[] ret) {
        // rsi = root start index ... where should the root description start in ret?
        // root icon at topmost node
        ret[rsi] = new RootKey(RootNodeIcon.ROOT, rootDesc[0]);
        // the following root types have additional symbols
        switch (root.getRootType()) {
            case LOCAL_VARIABLE_ROOT: {
                ret[rsi + 1] = new RootKey(RootNodeIcon.THREAD, rootDesc[1]);
                for (int i = 2; i < rootDesc.length - 1; i++) {
                    ret[rsi + i] = new RootKey(RootNodeIcon.METHOD, rootDesc[i]);
                }
                ret[ret.length - 1] = new RootKey(RootNodeIcon.LOCAL_VAR, rootDesc[rootDesc.length - 1]);
                break;
            }

            case VM_INTERNAL_THREAD_DATA_ROOT:
                ret[rsi + 1] = new RootKey(RootNodeIcon.THREAD, rootDesc[1]);
                break;

            case CODE_BLOB_ROOT:
                ret[rsi + 1] = new RootKey(RootNodeIcon.CLASS, rootDesc[1]);
                ret[rsi + 2] = new RootKey(RootNodeIcon.METHOD, rootDesc[2]);
                break;

            case JNI_LOCAL_ROOT:
                ret[rsi + 1] = new RootKey(RootNodeIcon.THREAD, rootDesc[1]);
                break;

            case CLASS_LOADER_ROOT:
                ret[rsi + 1] = new RootKey(RootNodeIcon.CLASS_LOADER, rootDesc[1]);
                break;

            case CLASS_ROOT:
                ret[rsi + 1] = new RootKey(RootNodeIcon.CLASS, rootDesc[1]);
                break;

            case STATIC_FIELD_ROOT:
                ret[rsi + 1] = new RootKey(RootNodeIcon.CLASS, rootDesc[1]);
                ret[rsi + 2] = new RootKey(RootNodeIcon.STATIC_FIELD, rootDesc[2]);
                break;

            case DEBUG_ROOT:
                ret[rsi + 1] = new RootKey(RootNodeIcon.ROOT, rootDesc[1]);
        }
    }

    @Override
    public ImagePack[] loadIcons() {
        ImagePack[] icons = new ImagePack[RootNodeIcon.values().length];

        for (int i = 0; i < icons.length; i++) {
            icons[i] = ImageUtil.getResourceImagePack(RootNodeIcon.values()[i].name, RootNodeIcon.values()[i].path);
        }

        return icons;
    }

    @Override
    public ImagePack getIcon(Object key) {
        if (key == null) {
            return super.getIcon(null);
        }

        RootKey rootKey = null;
        try {
            rootKey = (RootKey) key;
        } catch (ClassCastException e) {
            e.printStackTrace();
        }

        if (!iconsLoaded) {
            // Load icons only once
            icons = this.loadIcons();
            iconsLoaded = true;
        }

        if (rootKey.iconIndex() >= 0 && rootKey.iconIndex() < icons.length) {
            return icons[rootKey.iconIndex()];
        } else {
            return super.getIcon(key);
        }
    }

    @Override
    public ImageView getIconNode(Object key) {
        if (!iconsLoaded) {
            // Load icons only once
            icons = this.loadIcons();
            loadIconNodes();
            iconsLoaded = true;
        }

        if (key == null) {
            return null;
        }

        // Key should always be RootKey, but this is a workaround if classification tree is loaded from file
        // Currently classification trees that are loaded from files only have String keys (and thus we cannot detect their root type)
        if (key instanceof RootKey) {
            RootKey rootKey = null;
            try {
                rootKey = (RootKey) key;
            } catch (ClassCastException e) {
                e.printStackTrace();
            }

            if (rootKey.iconIndex() >= 0 && rootKey.iconIndex() < iconNodes.length) {
                return iconNodes[rootKey.iconIndex()];
            } else {
                return super.getIconNode(key);
            }
        }
        return null;
    }
}
