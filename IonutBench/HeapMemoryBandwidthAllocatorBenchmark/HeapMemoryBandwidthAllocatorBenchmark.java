import java.util.ArrayList;


public class HeapMemoryBandwidthAllocatorBenchmark {

    public int sizeInBytes=5;

    byte[] allocate() {
        return new byte[sizeInBytes];
    }
    
 public static void main(String args[]) {
        HeapMemoryBandwidthAllocatorBenchmark o = new HeapMemoryBandwidthAllocatorBenchmark();    
        o.sizeInBytes = Integer.parseInt(args[0]);
        byte[] b = o.allocate();
    }
  
    
};

 

