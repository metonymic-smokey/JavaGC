package com.benchmark.test;

import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.*;
import java.math.BigInteger;
import java.util.HashMap;
import java.nio.charset.Charset;
import java.util.Random;

public class Test {
    int int_var;
    String[] string_arr;
    ArrayList<ArrayList<String>> nestedAL = new ArrayList<ArrayList<String>>();
    HashMap<String, ArrayList<BigInteger>> gigaMap = new HashMap<String, ArrayList<BigInteger>>();

    public Test(int rand){
        this.int_var = rand;

        // Creating random string_arr
        int n = (int)(Math.random() * 10);
        string_arr = new String[n];
        for (int i = 0; i < n; i++) {
            byte[] b_word = new byte[(int)(Math.random() * 10)];
	    new Random().nextBytes(b_word);
	    String word = new String(b_word, Charset.forName("UTF-8"));
            this.string_arr[i] = word;
            ArrayList<String> strAL = new ArrayList<String>();
            int m = (int)(Math.random() * 10);
            ArrayList<BigInteger> big_int_al = new ArrayList<BigInteger>();
            for (int j = 0; j < m; j++) {
                byte[] str = new byte[(int)(Math.random() * 10)];
                new Random().nextBytes(str);
                strAL.add(new String(str, Charset.forName("UTF-8")));
                int len = (int)(Math.random() * 100);
                byte[] num = new byte[len];
                new Random().nextBytes(num);
                BigInteger no;
                if (num.length == 0)
                    no = new BigInteger("1");
                else
                    no = new BigInteger(num);

                big_int_al.add(no);
            }
            this.nestedAL.add(strAL);
            this.gigaMap.put(word, big_int_al);
        }

    }
}
