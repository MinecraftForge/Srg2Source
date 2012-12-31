package agaricus.srg2source.samplepackage;

import java.util.HashMap;

public class SampleClass2 {
    agaricus.srg2source.samplepackage.SampleClass fqClass;
    agaricus.srg2source.samplepackage.SampleClass[] fqClassArray;
    agaricus.srg2source.samplepackage.SampleClass[][] fqClassArray2;
    SampleClass nonFqClass;
    SampleClass[] nonFqClassArray;
    SampleClass[][] nonFqClassArray2;
    HashMap<SampleClass, SampleClass2> nonFqMap;
    HashMap<agaricus.srg2source.samplepackage.SampleClass, SampleClass2> fqMap;
    java.util.HashMap<agaricus.srg2source.samplepackage.SampleClass, SampleClass2> fqfqMap;

    private class SampleInnerClass {
        public int innerClassField;

        private class SampleInner2 extends HashMap<java.lang.String, java.lang.Double>
        {
            private int innermostField;
            public String initnewtest = new java.lang.String("");
        }
    }
}
