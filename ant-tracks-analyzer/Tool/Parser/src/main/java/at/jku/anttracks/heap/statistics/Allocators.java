
package at.jku.anttracks.heap.statistics;

public class Allocators {
    private long vm;
    private long ir;
    private long c1;
    private long c2;

    public Allocators(long vm, long ir, long c1, long c2) {
        this.vm = vm;
        this.ir = ir;
        this.c1 = c1;
        this.c2 = c2;
    }

    public Allocators() {}

    public long getVm() {
        return vm;
    }

    public void incrementVm() {
        this.vm++;
    }

    public long getIr() {
        return ir;
    }

    public void incrementIr() {
        this.ir++;
    }

    public long getC1() {
        return c1;
    }

    public void incrementC1() {
        this.c1++;
    }

    public long getC2() {
        return c2;
    }

    public void incrementC2() {
        this.c2++;
    }

    public void addC1(long c1) {
        this.c1 += c1;
    }

    public void addC2(long c2) {
        this.c2 += c2;

    }

    public void addIr(long ir) {
        this.ir += ir;
    }

    public void addVm(long vm) {
        this.vm += vm;
    }

    public void clear() {
        this.vm = 0;
        this.ir = 0;
        this.c1 = 0;
        this.c2 = 0;
    }
}
