package com.aacr.helpers.parsers;

import com.aacr.helpers.parsers.models.val.StringResource;
import com.aacr.helpers.parsers.models.val.ValueResource;
import com.aacr.helpers.parsers.models.view.LayoutResource;
import com.aacr.helpers.parsers.models.view.WidgetResource;
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

/**
 * This class can parse any given XML resource file from layout directory
 * and generate resource map from that file.
 */
public class LayoutResourceParser extends DefaultHandler {

    // All known resource attributes that can hold reference to string literal
    private static final List<String> STRING_ATTRS = Arrays.asList(
            "android:text",
            "android:contentDescription",
            "android:hint"
    );

    // Known value resources
    private final HashMap<String, HashMap<String, ValueResource>> valResMap;
    // The name of the layout
    private final String layoutName;

    // This is what needs to be built by the parser
    private LayoutResource output;

    // Temp variables used that will eventually build the final output
    private final ArrayList<String> allStrings = new ArrayList<>();
    private final HashMap<String, WidgetResource> widgets = new HashMap<>();
    private final ArrayList<String> innerLayouts = new ArrayList<>();

    /**
     * This constructor will start the parsing of file as soon as it is invoked and will run synchronously
     * It will only exit once the parsing is done or an exception occurs
     * @param resFilePath File path of the layout xml resource being parsed
     * @param layoutName
     * @param valResMap A map of already parsed value resources
     */
    public LayoutResourceParser(String resFilePath,
                                String layoutName,
                                HashMap<String, HashMap<String, ValueResource>> valResMap) {
        this.valResMap = valResMap;
        this.layoutName = layoutName;
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

    /**
     * After parsing is done, get the resource created by the parser
     * Must be called only AFTER the parsing is done
     * @return Returns the created layout resource
     */
    public LayoutResource getResource() {
        return output;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {

        // Check if there is a string somewhere in the attributes
        HashMap<String, String> tmpStrings = new HashMap<>();
        for (String stringAttr : STRING_ATTRS) {
            String curStr = attributes.getValue(stringAttr);
            if (curStr != null && !curStr.isEmpty()) {
                if (curStr.contains("@string/")) {
                    try {
                        String strId = curStr.substring(curStr.lastIndexOf('/')+1);
                        StringResource res = (StringResource) valResMap.get(StringResource.name).get(strId);
                        curStr = res.value;
                    } catch (Exception e) {
                        //ignore
                    }
                }
                tmpStrings.put(stringAttr, curStr);
            }
        }
        // Add all the detected strings into global arraylist of strings
        allStrings.addAll(tmpStrings.values());

        // Check if there is a view for which the id is explicitly mentioned
        String curId = attributes.getValue("android:id");
        if (curId != null && !curId.isEmpty()) {
            if (curId.contains("@"))
                curId = curId.substring(curId.lastIndexOf('/')+1);
            // Add the detected view with id in widgets map
            widgets.put(curId, new WidgetResource(curId, qName, tmpStrings));
        }

        // Check if there is another layout being included
        for (int i=0; i<attributes.getLength(); i++) {
            String curVal = attributes.getValue(i);
            if (curVal != null && curVal.contains("@layout/")) {
                innerLayouts.add(curVal.substring(curVal.lastIndexOf('/')+1));
            }
        }
    }

    @Override
    public void endDocument() throws SAXException {
        output = new LayoutResource(layoutName, allStrings, widgets, innerLayouts);
    }
}
