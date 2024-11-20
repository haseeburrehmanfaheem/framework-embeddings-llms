package com.aacr.helpers.detectors.app;

import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.aacr.helpers.accesscontrol.app.Component;
import com.aacr.helpers.accesscontrol.app.Protection;
import com.aacr.helpers.detectors.app.componentgraph.models.info.ComponentInfo;
import com.aacr.helpers.detectors.app.componentgraph.models.info.UiInfo;
import com.aacr.helpers.detectors.app.ui.UiEntrypointDetector;
import com.aacr.helpers.parsers.models.MapperResource;
import com.aacr.helpers.parsers.models.val.ValueResource;
import com.aacr.helpers.parsers.models.view.LayoutResource;
import com.ibm.wala.util.collections.Pair;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class AppEntrypointDetector {

  private final HashMap<Component, ComponentInfo> compMap = new HashMap<>();

  private final HashMap<AppEntrypoint, Protection> eps = new HashMap<>();

  private final ClassHierarchy appCha;
  private final AnalysisScope appScope;
  private final HashMap<String, Component> epProtectionMap;
  private final HashMap<Integer, MapperResource> allResIdMap;
  private final HashMap<String, HashMap<String, ValueResource>> valResMap;
  private final HashMap<String, LayoutResource> layoutResMap;
  private final HashMap<String, HashSet<String>> allWidgetsMap;

  public AppEntrypointDetector(ClassHierarchy appCha, AnalysisScope appScope,
                               HashMap<String, Component> epProtectionMap,
                               HashMap<Integer, MapperResource> allResIdMap,
                               HashMap<String, HashMap<String, ValueResource>> valResMap,
                               HashMap<String, LayoutResource> layoutResMap,
                               HashMap<String, HashSet<String>> allWidgetsMap) {
    this.appCha = appCha;
    this.appScope = appScope;
    this.epProtectionMap = epProtectionMap;
    this.allResIdMap = allResIdMap;
    this.valResMap = valResMap;
    this.layoutResMap = layoutResMap;
    this.allWidgetsMap = allWidgetsMap;

    init();
  }

  private void init() {

    //Extract all known entry points of app components
    long start = System.currentTimeMillis();
    addToMap(ComponentEntrypointDetector.getEps(epProtectionMap, appCha, appScope));
    long end = System.currentTimeMillis();
    System.out.println("Known component EP extraction: "+(end-start));

    //Extract all service component classes from EPs
    HashMap<String, Component> serviceEps = new HashMap<>();
    for (Component component : epProtectionMap.values()) {
      if (Component.Type.SERVICE == component.getType())
        serviceEps.put(component.getClassStr(), component);
    }
    //Extract and add the exposed binder methods from within all inner binder classes of the service EPs
    start = System.currentTimeMillis();
    addToMap(BinderEntrypointDetector.getEps(serviceEps, appCha, appScope));
    end = System.currentTimeMillis();
    System.out.println("Binder EP extraction: "+(end-start));

    //Extract all activity component classes from EPs
    HashMap<String, Pair<Component, ComponentInfo>> activityFragmentEps = new HashMap<>();
    for (Component component : compMap.keySet()) {
      if (Component.Type.ACTIVITY == component.getType())
        activityFragmentEps.put(component.getClassStr(), Pair.make(component, compMap.get(component)));
    }

    //Extract and add the known callbacks of fragment classes as potential entry points
    start = System.currentTimeMillis();
    addToMap(FragmentEntrypointDetector.getEps(appCha, appScope, activityFragmentEps));
    end = System.currentTimeMillis();
    System.out.println("Known Fragment EP extraction: "+(end-start));

    for (Component component : compMap.keySet()) {
      if (Component.Type.FRAGMENT == component.getType())
        activityFragmentEps.put(component.getClassStr(), Pair.make(component, compMap.get(component)));
    }

    start = System.currentTimeMillis();
    UiEntrypointDetector uiEpDetector = new UiEntrypointDetector(activityFragmentEps, appCha, appScope,
            allResIdMap, valResMap, layoutResMap, allWidgetsMap);
    addToMap(uiEpDetector.extractUiEps());
    addToMap(uiEpDetector.addDynamicStrings());
    end = System.currentTimeMillis();
    System.out.println("UI EP extraction: "+(end-start));

    start = System.currentTimeMillis();
    try {
      addToMap(AsyncTaskDetector.getEps(compMap, appCha, appScope));
    } catch (Exception e) {
      e.printStackTrace();
    }
    end = System.currentTimeMillis();
    System.out.println("AsyncTasks EP extraction: "+(end-start));
  }

  private void addToMap(HashMap<Component, ComponentInfo> newInfoMap) {
    for (Map.Entry<Component, ComponentInfo> newInfo : newInfoMap.entrySet()) {
      eps.putAll(newInfo.getValue().epMap);
      if (newInfoMap != compMap) {
        if (!compMap.containsKey(newInfo.getKey()))
          compMap.put(newInfo.getKey(), newInfo.getValue());
        else {
          if (newInfo.getValue() instanceof UiInfo) {
            newInfo.getValue().addEps(compMap.get(newInfo.getKey()).epMap);
            compMap.put(newInfo.getKey(), newInfo.getValue());
          } else {
            compMap.get(newInfo.getKey()).addEps(newInfo.getValue().epMap);
          }
        }
      }
    }
  }

  public HashMap<Component, ComponentInfo> getCompMap() {
    return compMap;
  }

  public HashMap<AppEntrypoint, Protection> getEps() {
    return eps;
  }
}
