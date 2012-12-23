package agaricus.applysrg.samplepackage;

import java.util.ArrayList;
import java.util.HashMap;
import java.lang.String;

public class SampleClass {
    public int field1;
    public int field2;
    public int field3;
    public ArrayList<String> field4;

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
        return i + j / k;
    }
}
