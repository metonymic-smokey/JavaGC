
package at.jku.anttracks.features;

public class TypeMapping extends Mapping {

    public final String type;

    public TypeMapping(String type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return type;
    }

    @Override
    public boolean matches(String type, String nameAndSignature, int bci) {
        return this.type.equals(type);
    }

}
