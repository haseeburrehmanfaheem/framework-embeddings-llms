package com.aacr.wala.workshop.analyzers;

public class Analyzer {
    public enum Type {
        App,
        Framework,
        DATA,
    }

    public static void launch(Type type) throws Exception {
        switch (type) {
            case App: AppAnalyzer.launch(); break;
            case Framework: FrameworkAnalyzer.launch(); break;
            case DATA: DataCollection.launch(); break;
            default: System.err.println("Should never reach here!");
        }
    }
}
