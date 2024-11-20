package com.aacr.helpers.parsers;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.aacr.helpers.accesscontrol.app.Component;
import com.aacr.helpers.parsers.functional.CleanupFunction;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class ManifestParser extends DefaultHandler {

    // Element string constants
    private final String ELEM_MANIFEST = "manifest";
    private final String ELEM_APPLICATION = "application";
    private final String ELEM_USES_PERMISSION = "uses-permission";
    private final String ELEM_INTENT_FILTER = "intent-filter";
    private final String ELEM_ACTION = "action";
    private final String ELEM_CATEGORY = "category";

    //Attribute string constants
    private final String ATTR_PACKAGE = "package";
    private final String ATTR_NAME = "android:name";
    private final String ATTR_EXPORTED = "android:exported";
    private final String ATTR_PERMISSION = "android:permission";

    //Components to include in analysis
    private final HashMap<String, Component.Type> componentsToDetect = Maps.newHashMap(ImmutableMap.of(
            "activity", Component.Type.ACTIVITY,
            "receiver", Component.Type.RECEIVER,
            "provider", Component.Type.PROVIDER,
            "service",  Component.Type.SERVICE
    ));

    public static String packageName = "";
    private final HashMap<String, Component> epProtectionMap = new HashMap<>();
    private String appPermission;

    private final ArrayList<String> usesPermissions = new ArrayList<>();

    public ManifestParser(String decodedManifestPath) {
        parseManifest(decodedManifestPath);
    }

    public HashMap<String, Component> getEpProtectionMap() {
        return epProtectionMap;
    }

    public ArrayList<String> getUsesPermissions() {
        return usesPermissions;
    }

    // Variables to store info about the component currently being parsed
    // Everything declared here must be cleaned up in corresponding end-element callback
    // Simply add the cleanup code in the functional array at the end, and it will automatically be called
    private String name = "";
    private Component.Type type = null;
    private boolean isExported = false;
    private boolean isIntentFilterRegistered = false;
    private String permission = "";
    private final ArrayList<String> actions = new ArrayList<>();
    private final ArrayList<String> categories = new ArrayList<>();
    private final List<CleanupFunction> cleanupFunctions = Arrays.asList(
            () -> name = "",
            () -> type = null,
            () -> isExported = false,
            () -> isIntentFilterRegistered = false,
            () -> permission = "",
            actions::clear,
            categories::clear
    );

    // This method MUST be called before using any of the getters
    public void parseManifest(String decodedManifestPath) {
        SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
        try {
            SAXParser parser = saxParserFactory.newSAXParser();
            parser.parse(new File(decodedManifestPath), this);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {
        if (qName.equals(ELEM_MANIFEST)) {
            packageName = attributes.getValue(ATTR_PACKAGE);
        } else if (qName.equals(ELEM_APPLICATION)) {
            if (attributes.getValue(ATTR_PERMISSION) != null)
                appPermission = attributes.getValue(ATTR_PERMISSION);
        } else if (qName.equals(ELEM_USES_PERMISSION)) {
            usesPermissions.add(attributes.getValue(ATTR_NAME));
        } else if (qName.equals(ELEM_INTENT_FILTER)) {
            isIntentFilterRegistered = true;
        } else if (qName.equals(ELEM_ACTION)) {
            actions.add(attributes.getValue(ATTR_NAME));
        } else if (qName.equals(ELEM_CATEGORY)) {
            categories.add(attributes.getValue(ATTR_NAME));
        } else if (componentsToDetect.containsKey(qName)) {
            type = componentsToDetect.get(qName);
            name = attributes.getValue(ATTR_NAME);
            isExported = (attributes.getValue(ATTR_EXPORTED) != null
                    && attributes.getValue(ATTR_EXPORTED).equals("true"));
            if (attributes.getValue(ATTR_PERMISSION) != null)
                permission = attributes.getValue(ATTR_PERMISSION);
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
        if (componentsToDetect.containsKey(qName)) {
//            if (name.contains("RequestPermissionHelperActivity"))
//                System.out.println();
            if (name != null && !name.isEmpty()) {
                StringBuilder finalClassName = new StringBuilder("L");
                if (name.indexOf('.') > 0)
                    finalClassName.append(name);
                else {
                    finalClassName.append(packageName);
                    if (name.startsWith("."))
                        finalClassName.append(name);
                    else
                        finalClassName.append(".").append(name);
                }
                String finalPerm;
                if (permission != null && !permission.isEmpty())
                    finalPerm = permission;
                else
                    finalPerm = appPermission;
                epProtectionMap.put(finalClassName.toString().replace('.', '/'),
                        new Component(
                                finalClassName.toString().replace('.', '/'),
                                type,
                                isExported,
                                isIntentFilterRegistered,
                                finalPerm,
                                new ArrayList<>(actions),
                                new ArrayList<>(categories))
                );
            }
            for (CleanupFunction f : cleanupFunctions) {
                f.cleanup();
            }
        }
    }
}
