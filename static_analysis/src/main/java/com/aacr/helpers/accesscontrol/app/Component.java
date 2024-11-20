package com.aacr.helpers.accesscontrol.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

// A class representing exposed components discovered from parsing the app Manifest
public class Component {

    private final String classStr;
    private final Type type;
    private final Protection protection;

    // Should ONLY be used with exposed components discovered from parsing the Manifest
    public Component(String classStr,
                     Type type,
                     boolean isExported,
                     boolean isIntentFilterRegistered,
                     String permission,
                     ArrayList<String> actions,
                     ArrayList<String> categories) {
        this.classStr = classStr;
        this.type = type;
        this.protection = new Protection(isExported, isIntentFilterRegistered, permission, actions, categories, this);
    }

    // Should ONLY be used with external app's components (i.e., ones that do not belong to current app)
    public Component(String classStr) {
        this.classStr = classStr;
        this.type = Type.OUT_OF_APP;
        this.protection = null;
    }

    public Type getType() {
        return type;
    }

    public Protection getProtection() {
        return protection;
    }

    public String getClassStr() {
        return classStr;
    }

    @Override
    public String toString() {
        String protectionStr = "";
        if (protection != null)
            protectionStr = protection.toStringWithoutComponent();
        return "AppComponent{" +
                "classStr='" + classStr + '\'' +
                "   type=" + type + "}" +
                "," + protectionStr;// +
//                '}';
    }

    public String toStringWithoutProtection() {
        return "AppComponent{" +
                "   classStr='" + classStr + '\'' +
                "   type=" + type +
                '}';
    }

    // TODO: Uncomment other start activity variants if we decide to handle them
    public enum Type {
        OUT_OF_APP(null, null),

        ACTIVITY(new HashSet<>(Arrays.asList(
                "onCreate",
                "onStart",
                "onResume",
                "onPause",
                "onStop",
                "onDestroy",
                "onSaveInstanceState",
                "onRestoreInstanceState",
                "onActivityResult"
        )), new HashSet<>(Arrays.asList(
                "startActivities",
                "startActivity",
                "startActivityForResult",
                "startActivityFromChild",
                "startActivityIfNeeded",
                "startNextMatchingActivity"
        ))),
        // Handling for exposed Methods within returned Binders from onBind
        // is done separately in getAppEntryPoints method
        SERVICE(new HashSet<>(Arrays.asList(
                "onStartCommand",
                "onBind",
                "onCreate",
                "onDestroy",
                "onUnbind",
                "onRebind"
        )), new HashSet<>(Arrays.asList(
                "bindIsolatedService",
                "bindService",
                "bindServiceAsUser",
                "startForegroundService",
                "startService"
        ))),
        // TODO: This is tricky, decide on how to handle the cases
        RECEIVER(new HashSet<>(Collections.singletonList(
                "onReceive"
        )), new HashSet<>(Arrays.asList(
                "sendBroadcast",
                "sendBroadcastAsUser",
                "sendBroadcastWithMultiplePermissions",
                "sendOrderedBroadcast" //Check if we want to handle this specially by utilizing the app ops arguments
        ))),
        // TODO: This is also tricky, decide on how to handle the cases - LOTS of functions from content resolver!!
        PROVIDER(new HashSet<>(Arrays.asList(
                "onCreate",
                "query",
                "insert",
                "delete",
                "update",
                "getType",
                "call"
        )), new HashSet<>(Arrays.asList(
        ))),

        // Technically, these are not app components, but are very important for UI analysis
        FRAGMENT(new HashSet<>(Arrays.asList(
                "onAttach",
                "onCreate",
                "onCreateView",
                "onViewCreated",
                "onActivityCreated",
                "onActivityResult",
                "onViewStateRestored",
                "onStart",
                "onResume",
                "onPause",
                "onStop",
                "onSaveInstanceState",
                "onDestroyView",
                "onDestroy",
                "onPreferenceTreeClick",
                "onCreatePreferences",
                "onCreateRecyclerView",
                "onDisplayPreferenceDialog",
                "onNavigateToScreen",
                "onCreateAdapter",
                "onCreateDialog"
        )), new HashSet<>(Arrays.asList(
                // TODO: Find a way to add FragmentTransaction::add and FragmentTransaction::replace methods here
        )))
        ;

        public final HashSet<String> knownEpMethods;
        public final HashSet<String> knownInvokeMethods;

        Type(HashSet<String> knownEpMethods, HashSet<String> knownInvokeMethods) {
            this.knownEpMethods = knownEpMethods;
            this.knownInvokeMethods = knownInvokeMethods;
        }
    }

}
