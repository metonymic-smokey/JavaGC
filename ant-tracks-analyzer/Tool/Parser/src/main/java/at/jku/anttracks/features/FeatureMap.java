
package at.jku.anttracks.features;

import at.jku.anttracks.heap.symbols.AllocationSite;

import java.util.ArrayList;
import java.util.List;

public class FeatureMap {

    private final Feature others = new Feature("others", 127, 127, 127);
    private final List<Feature> features = new ArrayList<>();

    {
        features.add(others);
    }

    public Feature[] getFeatures() {
        return features.toArray(new Feature[features.size()]);
    }

    public Feature getFeature(int id) {
        return features.get(id);
    }

    public int getFeatureCount() {
        return features.size();
    }

    public long getPackageMappingCount() {
        return features.stream().flatMap(f -> f.getPackages().stream()).count();
    }

    public long getTypeMappingCount() {
        return features.stream().flatMap(f -> f.getTypes().stream()).count();
    }

    public long getMethodeMappingCount() {
        return features.stream().flatMap(f -> f.getMethods().stream()).count();
    }

    public long getInstructionMappingCount() {
        return features.stream().flatMap(f -> f.getInstructions().stream()).count();
    }

    public void add(Feature feature) {
        features.add(feature);
    }

    public int[] match(AllocationSite site) {
        for (AllocationSite.Location callSite : site.getCallSites()) {
            final String splitter = ".";
            String signature = callSite.getSignature();
            int index = signature.lastIndexOf(splitter);
            if (index < 0) {
                return new int[]{0};
            }
            String type = signature.substring(0, index);
            String methodAndSignature = signature.substring(index + splitter.length());

            int nMatches = 0;
            boolean[] matches = new boolean[features.size()];

            if (nMatches == 0) {
                for (int id = 0; id < getFeatureCount(); id++) {
                    if (matches[id]) {
                        continue;
                    }
                    Feature feature = getFeature(id);
                    for (InstructionMapping mapping : feature.getInstructions()) {
                        if (mapping.matches(type, methodAndSignature, callSite.getBci())) {
                            matches[id] = true;
                            nMatches++;
                            break;
                        }
                    }
                }
            }

            if (nMatches == 0) {
                for (int id = 0; id < getFeatureCount(); id++) {
                    if (matches[id]) {
                        continue;
                    }
                    Feature feature = getFeature(id);
                    for (MethodMapping mapping : feature.getMethods()) {
                        if (mapping.matches(type, methodAndSignature, callSite.getBci())) {
                            matches[id] = true;
                            nMatches++;
                            break;
                        }
                    }
                }
            }

            if (nMatches == 0) {
                for (int id = 0; id < getFeatureCount(); id++) {
                    if (matches[id]) {
                        continue;
                    }
                    Feature feature = getFeature(id);
                    for (TypeMapping mapping : feature.getTypes()) {
                        if (mapping.matches(type, methodAndSignature, callSite.getBci())) {
                            matches[id] = true;
                            nMatches++;
                            break;
                        }
                    }
                }
            }

            if (nMatches == 0) {
                for (int id = 0; id < getFeatureCount(); id++) {
                    if (matches[id]) {
                        continue;
                    }
                    Feature feature = getFeature(id);
                    for (PackageMapping mapping : feature.getPackages()) {
                        if (mapping.matches(type, methodAndSignature, callSite.getBci())) {
                            matches[id] = true;
                            nMatches++;
                            break;
                        }
                    }
                }
            }

            if (nMatches > 0) {
                int[] result = new int[nMatches];
                int cursor = 0;
                for (int id = 0; id < getFeatureCount(); id++) {
                    if (matches[id]) {
                        result[cursor++] = id;
                    }
                }
                return result;
            }
        }

        return new int[]{0};
    }

}
