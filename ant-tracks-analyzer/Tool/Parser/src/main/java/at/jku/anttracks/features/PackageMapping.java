
package at.jku.anttracks.features;

public class PackageMapping extends Mapping {

    public final String pakkage;

    public PackageMapping(String pakkage) {
        this.pakkage = pakkage;
    }

    @Override
    public boolean matches(String type, String nameAndSignature, int bci) {
        return type.startsWith(pakkage);
    }

    @Override
    public String toString() {
        return pakkage;
    }

}
