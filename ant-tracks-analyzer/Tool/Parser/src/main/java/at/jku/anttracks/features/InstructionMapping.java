
package at.jku.anttracks.features;

public class InstructionMapping extends MethodMapping {

    public final int from;
    public final int to;

    public InstructionMapping(String type, String method, String signature, int from, int to) {
        super(type, method, signature);
        this.from = from;
        this.to = to;
    }

    @Override
    public String toString() {
        return super.toString() + " [" + from + " ... " + to + "]";
    }

    @Override
    public boolean matches(String type, String nameAndSignature, int bci) {
        return super.matches(type, nameAndSignature, bci) && from <= bci && bci < to;
    }

}
