import java.util.ArrayList;
import java.util.Random; 

public class ReadBarriersLoopingOverArrayBenchmark {
    
    int size,sum=0;
    int[] array;
    
    int test() {
        int lSize = size;
 
        int sum = 0;
        for (int i = 0; i < lSize; i++) {
            sum += array[i];
        }
 
        return sum;
    } 

    void arrayPopulate() {
        array = new int[size];
        Random random = new Random();

        for(int i = 0;i<size;i++) {
            int val = random.nextInt(size);
            array[i] = val;
        }

    }

    public static void main(String[] args) {
        ReadBarriersLoopingOverArrayBenchmark r = new ReadBarriersLoopingOverArrayBenchmark();

        r.size = Integer.parseInt(args[0]);
        r.arrayPopulate();  
        int res = r.test();
    }
}
