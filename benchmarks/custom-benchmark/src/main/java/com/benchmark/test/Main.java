package com.benchmark.test;

import java.io.IOException;
import java.util.ArrayList;
import java.lang.Math;

public class Main {
    public static void main(String[] args) throws IOException {
        // int numOfObjects = Integer.parseInt(args[0]);
	// int iters = Integer.parseInt(args[1]);
        for(int j = 0; j < 10; j++) {
            ArrayList<Test> objArr = new ArrayList<Test>();
            for (int i = 0; i < 1000; i++) {
		Test a = new Test((int)(Math.random() * 1000));
                objArr.add(a);
            }
        }
    }
}
