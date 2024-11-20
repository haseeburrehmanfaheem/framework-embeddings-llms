package com.aacr.wala.workshop;

import com.aacr.wala.workshop.analyzers.Analyzer;
import com.aacr.wala.workshop.utils.PropertyUtils;
import com.aacr.wala.workshop.utils.ValidationUtils;

public class Main {
    public static void main(String[] args) throws Exception {
        ValidationUtils.validateProperties();
        Analyzer.launch(Analyzer.Type.valueOf(PropertyUtils.getType()));
    }
}
