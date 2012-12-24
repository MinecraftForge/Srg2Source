package agaricus.applysrg.samplepackage;

import java.util.ArrayList;
import java.util.HashMap;
import java.lang.String;

public class SampleClass {
    public int field1;
    public int field2;
    public int field3;
    public ArrayList<String> field4;
    public int field5 = 5;
    public int field6 = field5 + 1;
    static int sfield7;

    public SampleClass()
    {
        field1 = 1;
        field2 = 2;
        field3 = 3;
    }

    public void a()
    {
        field1 = field2 * field3;
    }

    public int a(int i)
    {
        return i * 2;
    }

    public void a(String s)
    {
    }

    public SampleClass a(int x, int y)
    {
        return this;
    }

    public int paramtest(int i, int j, int k) {
        return i + j / k + k + k + k - j - j - i;
    }

    private int vartest() {
        int j, f, k, aaaaaaaaaa;
        aaaaaaaaaa = 10;
        k = 1; j = f = k;
        System.out.println("?"+aaaaaaaaaa+k+f+j);
        int last = 20121221;
        return last / j;
    }

    static {
        sfield7 = 7;

        int whereAmI = -1;
        sfield7 *= whereAmI;
    }

    {
        field1 = 1;
        int overHere = -2;
    }
}
