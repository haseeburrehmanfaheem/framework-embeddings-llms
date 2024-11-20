package com.aacr.helpers.utils;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.aacr.helpers.extension.ExtendedMethodReference;

public class FactoryUtils {

  public static ExtendedMethodReference makeExtendedMethodReference(CGNode nd) {
    return new ExtendedMethodReference(nd.getMethod().getDeclaringClass().getReference(),
        nd.getMethod().getReference());
  }
}
