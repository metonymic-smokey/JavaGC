
package at.jku.anttracks.parser;

@FunctionalInterface
public interface ErrorHandler {
    public abstract void report(Throwable t);
}
