
package at.jku.anttracks.parser;

public interface TraceParserListener {

    public abstract void report(long from, long to, long position);

}
