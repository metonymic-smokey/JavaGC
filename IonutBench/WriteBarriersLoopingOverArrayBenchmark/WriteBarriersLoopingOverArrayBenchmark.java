import java.util.ArrayList;

public class WriteBarriersLoopingOverArrayBenchmark {

    int size;
    int[] array;

    void test(Integer lRefInteger) {
        int lSize = size;
 
        for (int i = 0; i < lSize; i++) {
            array[i] = lRefInteger;
        }
    }

    public static void main(String[] args) {
       WriteBarriersLoopingOverArrayBenchmark w = new WriteBarriersLoopingOverArrayBenchmark();  
       w.size = Integer.parseInt(args[0]); 
       int lRef = Integer.parseInt(args[1]);
       w.array = new int[w.size];

       w.test(lRef);

    }
}
