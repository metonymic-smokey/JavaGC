
package at.jku.anttracks.features;

public class MethodMapping extends TypeMapping {

    private final String methodAndSignature;

    public MethodMapping(String type, String method, String signature) {
        super(type);
        this.methodAndSignature = method + "(" + signature + ")";
    }

    @Override
    public String toString() {
        return super.toString() + "::" + methodAndSignature;
    }

    @Override
    public boolean matches(String type, String nameAndSignature, int bci) {
        return super.matches(type, nameAndSignature, bci);
    }

}
