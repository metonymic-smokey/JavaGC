
package at.jku.anttracks.features;

import java.util.ArrayList;
import java.util.List;

public class Feature {

    public final String name;
    public final int r, g, b;

    private final List<PackageMapping> packages;
    private final List<TypeMapping> types;
    private final List<MethodMapping> methods;
    private final List<InstructionMapping> instructions;

    public Feature(String name, int r, int g, int b) {
        this.name = name;
        this.r = r;
        this.g = g;
        this.b = b;
        packages = new ArrayList<>(1);
        types = new ArrayList<>(1);
        methods = new ArrayList<>(1);
        instructions = new ArrayList<>(1);
    }

    public List<PackageMapping> getPackages() {
        return packages;
    }

    public List<TypeMapping> getTypes() {
        return types;
    }

    public List<MethodMapping> getMethods() {
        return methods;
    }

    public List<InstructionMapping> getInstructions() {
        return instructions;
    }

    public void addPackage(String pakkage) {
        packages.add(new PackageMapping(pakkage));
    }

    public void addType(String type) {
        types.add(new TypeMapping(type));
    }

    public void addMethod(String type, String method, String signature) {
        methods.add(new MethodMapping(type, method, signature));
    }

    public void addInstructions(String type, String method, String signature, int from, int to) {
        instructions.add(new InstructionMapping(type, method, signature, from, to));
    }

}
