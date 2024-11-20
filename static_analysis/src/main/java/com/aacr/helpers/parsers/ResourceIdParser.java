package com.aacr.helpers.parsers;

import com.aacr.helpers.parsers.models.MapperResource;
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
 * This class parses public.xml resource file from val directory
 * and generates a map of all resource ids to resource names and types.
 */
public class ResourceIdParser extends DefaultHandler {

    // Final map containing the output
    private final HashMap<Integer, MapperResource> allResMap = new HashMap<>();

    /**
     * A simple constructor. Nothing special.
     * @param resFilePath File path of the xml resource being parsed
     */
    public ResourceIdParser(String resFilePath) {
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
     * @return Returns the created hashmap of resId -> resource name/type
     */
    public HashMap<Integer, MapperResource> getAllResMap() {
        return allResMap;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        if (qName.equals("public")) {
            String name = attributes.getValue("name");
            String type = attributes.getValue("type");
            int id = Integer.decode(attributes.getValue("id"));
            allResMap.put(id, new MapperResource(name, type, id));
        }
    }
}
