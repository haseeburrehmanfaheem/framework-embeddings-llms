package com.aacr.helpers.parsers;

import com.aacr.helpers.parsers.functional.CleanupFunction;
import com.aacr.helpers.parsers.models.val.ValueResource;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * This class is a base class that can parse any given XML resource file from val directory
 * and generate resource map from that file.
 */
public class ValResourceParser extends DefaultHandler {

    // Variables to hold the relevant element and attribute names
    private final String resName;
    private final String idAttrName;
    private final String innerElemName;
    private final String innerAttrName;

    // Final map containing the output
    private final HashMap<String, ValueResource> resMap = new HashMap<>();

    // Temp variables used while parsing
    private final Stack<String> curIds = new Stack<>();
    private final Stack<String> curVals = new Stack<>();
    private final HashMap<String, String> tmpMap = new HashMap<>();
    private final ArrayList<String> tmpList = new ArrayList<>();

    // List of functions to clean up the temp variables
    private final List<CleanupFunction> cleanupFunctions = Arrays.asList(
            curIds::clear,
            curVals::clear,
            tmpMap::clear,
            tmpList::clear
    );

    /**
     * This constructor is used when resource file is a simple xml of key value pairs (e.g., strings.xml)
     * The key is hardcoded to always be the attribute 'name', since no other case is relevant for Android apps
     * @param resFilePath File path of the xml resource being parsed
     * @param resName Name of the main element that needs to be extracted (e.g., 'string' for strings.xml)
     */
    public ValResourceParser(String resFilePath, String resName) {
        this(resFilePath, resName, "name", "", "");
    }

    /**
     * This constructor is used when resource file is an XML of key value pairs, where values are arrays (e.g., arrays.xml)
     * The key is hardcoded to always be the attribute 'name', since no other case is relevant for Android apps
     * @param resFilePath File path of the xml resource being parsed
     * @param resName Name of the main element that needs to be extracted (e.g., 'string' for strings.xml)
     * @param innerElemName Name of the element used to specify the individual array values (e.g., 'item' for arrays.xml)
     */
    public ValResourceParser(String resFilePath, String resName, String innerElemName) {
        this(resFilePath, resName, "name", innerElemName, "");
    }

    /**
     * This constructor is used when resource file is an XML of key value pairs, where values are hashmaps (e.g., plurals.xml)
     * The key is hardcoded to always be the attribute 'name', since no other case is relevant for Android apps
     * @param resFilePath File path of the xml resource being parsed
     * @param resName Name of the main element that needs to be extracted (e.g., 'string' for strings.xml)
     * @param innerElemName Name of the element used to specify the individual hashmap values (e.g., 'item' for plurals.xml)
     * @param innerAttrName Name of the attribute used to specify the individual hashmap keys (e.g., 'quantity' for plurals.xml)
     */
    public ValResourceParser(String resFilePath, String resName, String innerElemName, String innerAttrName) {
        this(resFilePath, resName, "name", innerElemName, innerAttrName);
    }

    /**
     * This is the most generic constructor which can be used when the assumptions of commonly used names for elements
     * and attributes are false. This constructor should rarely be required
     * @param resFilePath File path of the xml resource being parsed
     * @param resName Name of the main element that needs to be extracted (e.g., 'string' for strings.xml)
     * @param idAttrName Name of the attribute that specifies the id of the element being extracted (e.g., 'name' for strings.xml)
     * @param innerElemName Name of the element used to specify the individual hashmap values (e.g., 'item' for plurals.xml)
     * @param innerAttrName Name of the attribute used to specify the individual hashmap keys (e.g., 'quantity' for plurals.xml)
     */
    public ValResourceParser(String resFilePath, String resName, String idAttrName, String innerElemName, String innerAttrName) {
        this.resName = resName;
        this.idAttrName = idAttrName;
        this.innerElemName = innerElemName;
        this.innerAttrName = innerAttrName;

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
     * After parsing is done, get the resource map created by the parser
     * Must be called only AFTER the parsing is done
     * @return Returns the created hashmap of resId -> value resource
     */
    public HashMap<String, ValueResource> getResMap() {
        return resMap;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        if (qName.equals(resName)) {
            curIds.add(attributes.getValue(idAttrName));
        } else if (!innerElemName.isEmpty() && !innerAttrName.isEmpty()
                && qName.equals(innerElemName) && !curIds.isEmpty()) {
            curIds.add(attributes.getValue(innerAttrName));
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        String val = (new String(ch, start, length)).trim();

        if (!curIds.isEmpty() && !val.isEmpty())
            curVals.add(val);
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (!innerElemName.isEmpty() && qName.equals(innerElemName) && !curIds.isEmpty()) {
            String curVal = "";
            if (!curVals.isEmpty()) curVal = curVals.pop();
            if (!innerAttrName.isEmpty())
                tmpMap.put(curIds.pop(), curVal);
            else
                tmpList.add(curVal);
        } else if (qName.equals(resName)) {
            if (!curVals.isEmpty())
                tmpList.add(curVals.pop());

            ValueResource res;
            if (!tmpMap.isEmpty())
                res = ValueResource.createValRes(resName, new HashMap<>(tmpMap));
            else
                res = ValueResource.createValRes(resName, new ArrayList<>(tmpList));

            resMap.put(curIds.pop(), res);

            for (CleanupFunction f : cleanupFunctions) {
                f.cleanup();
            }
        }
    }
}
