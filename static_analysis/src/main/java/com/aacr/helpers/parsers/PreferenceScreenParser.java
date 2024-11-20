package com.aacr.helpers.parsers;

import com.aacr.helpers.parsers.models.val.StringResource;
import com.aacr.helpers.parsers.models.val.ValueResource;
import com.aacr.helpers.parsers.models.view.preference.PreferenceResource;
import com.aacr.helpers.parsers.models.view.preference.PreferenceScreenResource;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;

public class PreferenceScreenParser extends DefaultHandler {

    private final HashMap<String, HashMap<String, ValueResource>> valResMap;
    private final String xmlName;

    private HashMap<String, String> prefScreenProps;
    private final HashMap<String, PreferenceResource> preferences = new HashMap<>();

    private PreferenceScreenResource resource;
    private boolean isPref = false;

    private String curPrefKey = "";
    private String curPrefName = "";
    private HashMap<String, String> curPrefProps = new HashMap<>();
    private final HashMap<String, HashMap<String, String>> curPrefInternal = new HashMap<>();

    public PreferenceScreenParser(String resFilePath, String xmlName,
                                  HashMap<String, HashMap<String, ValueResource>> valResMap) {
        this.valResMap = valResMap;
        this.xmlName = xmlName;
        parseFile(resFilePath);
    }

    // This method MUST be called before using any of the getters
    private void parseFile(String resFilePath) {
        SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
        try {
            SAXParser parser = saxParserFactory.newSAXParser();
            parser.parse(new File(resFilePath), this);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {
        if (!isPref) {
            if (qName.equals("PreferenceScreen")) {
                isPref = true;
                prefScreenProps = getNormalizedAttrs(attributes);
            }
            return;
        }

        if (curPrefName.isEmpty()) {
            curPrefProps = getNormalizedAttrs(attributes);
            curPrefKey = curPrefProps.get("android:key");
            curPrefName = qName;
        } else {
            curPrefInternal.put(qName, getNormalizedAttrs(attributes));
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
        if (!isPref)
            return;
        if (qName.equals("PreferenceScreen")) {
            isPref = false;
            return;
        }
        if (qName.equals(curPrefName)) {
            preferences.put(curPrefKey, new PreferenceResource(curPrefName, curPrefKey, curPrefProps, curPrefInternal));
            curPrefName = "";
            curPrefKey = "";
            curPrefProps.clear();
            curPrefInternal.clear();
        }
    }

    @Override
    public void endDocument() {
        if (preferences.isEmpty() || prefScreenProps.isEmpty())
            resource = null;
        else
            resource = new PreferenceScreenResource(xmlName, prefScreenProps, preferences);;
    }

    public boolean preferenceFound() {
        return resource != null;
    }

    public PreferenceScreenResource getResource() {
        return resource;
    }

    private HashMap<String, String> getNormalizedAttrs(Attributes attributes) {
        HashMap<String, String> normalizedAttrs = new HashMap<>();
        for (int i=0; i < attributes.getLength(); i++) {
            String attr = attributes.getQName(i);

            if (attr.startsWith("xmlns"))
                continue;

            String curStr = attributes.getValue(i);
            try {
                if (curStr != null && !curStr.isEmpty()) {
                    if (curStr.contains("@string/")) {
                        String strId = curStr.substring(curStr.lastIndexOf('/') + 1);
                        StringResource res = (StringResource) valResMap.get(StringResource.name).get(strId);
                        curStr = res.value;
                    }
                    normalizedAttrs.put(attr, curStr);
                }
            } catch (Exception e) {
                //ignore
            }
        }

        return normalizedAttrs;
    }
}
