
package at.jku.anttracks.features;

//abstract class and no interface to allow invokevirtual instead of invokeinterface calls
public abstract class Mapping {
    public abstract boolean matches(String type, String nameAndSignature, int bci);
}
