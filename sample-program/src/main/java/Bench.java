import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

public class Bench {
    public static void main(String[] args) {
        int n = Integer.parseInt(args[0]);
        int its = Integer.parseInt(args[1]);
        for (int i = 0; i < its; ++i) {
            ArrayList<HahaAClass> a = new ArrayList<HahaAClass>(n);
            for (int j = 0; j < n; ++j) {
                HahaAClass hahaAClass = new HahaAClass();
                hahaAClass.setA(ThreadLocalRandom.current().nextInt());
                a.add(hahaAClass);
            }
        }
    }
}
