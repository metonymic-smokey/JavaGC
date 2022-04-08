
package at.jku.anttracks.diff;

import at.jku.anttracks.classification.Classifier;
import at.jku.anttracks.classification.nodes.GroupingNode;
import at.jku.anttracks.classification.nodes.ListGroupingNode;
import at.jku.anttracks.classification.nodes.MapGroupingNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PermBornDiedTempGrouping {
    private final GroupingNode perm;
    private final GroupingNode born;
    private final GroupingNode died;
    private final MapGroupingNode temp;
    private final Object key;
    private final int level;
    private final PermBornDiedTempGrouping parent;

    public PermBornDiedTempGrouping(GroupingNode perm, GroupingNode born, GroupingNode died, MapGroupingNode temp) {
        this(perm, born, died, temp, "Filtered", 0, null);
    }

    public PermBornDiedTempGrouping(GroupingNode perm, GroupingNode born, GroupingNode died, MapGroupingNode temp, Object key, int level, PermBornDiedTempGrouping parent) {
        this.perm = perm;
        this.born = born;
        this.died = died;
        this.temp = temp;
        this.key = key;
        this.level = level;
        this.parent = parent;
    }

    public List<PermBornDiedTempGrouping> getChildren() {
        Map<String, Object[]> unionChildrenKeys = new HashMap<>();

        if (perm != null) {
            perm.getChildren()
                .stream()
                .filter(child -> !unionChildrenKeys.containsKey(child.getFullKeyAsString()))
                .forEach(child -> unionChildrenKeys.put(child.getFullKeyAsString(), child.getFullKey()));
        }
        if (born != null) {
            born.getChildren()
                .stream()
                .filter(child -> !unionChildrenKeys.containsKey(child.getFullKeyAsString()))
                .forEach(child -> unionChildrenKeys.put(child.getFullKeyAsString(), child.getFullKey()));
        }
        if (died != null) {
            died.getChildren()
                .stream()
                .filter(child -> !unionChildrenKeys.containsKey(child.getFullKeyAsString()))
                .forEach(child -> unionChildrenKeys.put(child.getFullKeyAsString(), child.getFullKey()));
        }
        if (temp != null) {
            temp.getChildren()
                .stream()
                .filter(child -> !unionChildrenKeys.containsKey(child.getFullKeyAsString()))
                .forEach(child -> unionChildrenKeys.put(child.getFullKeyAsString(), child.getFullKey()));
        }

        return unionChildrenKeys.values()
                                .stream()
                                .map(key -> {
                                    Object childKey = key[key.length - 1];
                                    return new PermBornDiedTempGrouping(perm == null ? null : (ListGroupingNode) perm.getChild(childKey),
                                                                        born == null ? null :
                                                                        (born instanceof ListGroupingNode ?
                                                                         (ListGroupingNode) born.getChild(childKey) :
                                                                         (MapGroupingNode) born.getChild(childKey)),
                                                                        died == null ? null : (ListGroupingNode) died.getChild(childKey),
                                                                        temp == null ? null : (MapGroupingNode) temp.getChild(childKey),
                                                                        childKey,
                                                                        level + 1,
                                                                        this);
                                })
                                .collect(Collectors.toList());
    }

    public GroupingNode getPerm() {
        return perm;
    }

    public GroupingNode getBorn() {
        return born;
    }

    public GroupingNode getDied() {
        return died;
    }

    public MapGroupingNode getTemp() {
        return temp;
    }

    public Object getKey() {
        return key;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Perm:\n");
        sb.append(perm);
        sb.append("\nBorn:\n");
        sb.append(born);
        sb.append("\nDied:\n");
        sb.append(died);
        sb.append("\nTemp:\n");
        sb.append(temp);
        return sb.toString();
    }

    public void clear() {
        if (perm != null) {
            perm.clear();
        }
        if (born != null) {
            born.clear();
        }
        if (died != null) {
            died.clear();
        }
        if (temp != null) {
            temp.clear();
        }
    }

    public int getSubTreeLevel() {
        if (perm != null) {
            return perm.getSubTreeLevel();
        }
        if (born != null) {
            return born.getSubTreeLevel();
        }
        if (temp != null) {
            return temp.getSubTreeLevel();
        }
        if (died != null) {
            return died.getSubTreeLevel();
        }
        assert false : "At least one object-set must be non-null";
        return -1;
    }

    public Class<? extends Classifier<?>> getClassifier() {
        if (perm != null) {
            return perm.getClassifier();
        }
        if (born != null) {
            return born.getClassifier();
        }
        if (temp != null) {
            return temp.getClassifier();
        }
        if (died != null) {
            return died.getClassifier();
        }
        assert false : "At least one object-set must be non-null";
        return null;
    }

    public PermBornDiedTempGrouping getParent() {
        return parent;
    }
}
