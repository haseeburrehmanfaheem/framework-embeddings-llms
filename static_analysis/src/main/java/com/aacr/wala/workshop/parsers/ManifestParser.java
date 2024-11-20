package com.aacr.wala.workshop.parsers;

import com.aacr.helpers.utils.FileUtils;
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
import java.util.HashSet;

public class ManifestParser extends DefaultHandler {

    //Before using this class, make sure this variable is pointing to a valid compiled binary of axmldec
    //To know more about compiling axmldec see https://github.com/ytsutano/axmldec
    private static final String AXMLDEC_PATH = "./lib/axmldec";

    private static final String OUTPUT_FILE = "DecodedManifest.xml";
    private static final String MANIFEST_FILE = "AndroidManifest.xml";

    private static final String MANIFEST_COMPONENT = "manifest";
    private static final String INTENT_FILTER = "intent-filter";

    private static final HashSet<String> componentsToDetect = new HashSet<>(Arrays.asList(
            "activity",
            "receiver",
            "provider",
            "service"
    ));

    private String packageName = "";
    private final ArrayList<String> epClasses = new ArrayList<>();

    private String tempCompName = "";
    private boolean isCompExported = false;
    private boolean isIntentFilterRegistered = false;

    public static void parseManifest(String path) {

        SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
        try {
            String inPath = FileUtils.findFileNameFromParentPath(path, MANIFEST_FILE);
            String outPath = inPath.substring(0, inPath.lastIndexOf("/")+1) + OUTPUT_FILE;
            Runtime.getRuntime().exec(AXMLDEC_PATH + " " + inPath + " -o " + outPath);

            File outFile = new File(outPath);

            SAXParser parser = saxParserFactory.newSAXParser();
            ManifestParser manifestParser = new ManifestParser();
            parser.parse(outFile, manifestParser);
            ArrayList<String> epClasses = manifestParser.getEpClasses();
            for (String epClass : epClasses) {
                System.out.println("EP Class : " + epClass);
            }

            outFile.delete();
        } catch (ParserConfigurationException | IOException | SAXException e) {
            e.printStackTrace();
        }
    }

    public ArrayList<String> getEpClasses() {
        return epClasses;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {
        if (qName.equals(MANIFEST_COMPONENT)) {
            packageName = attributes.getValue("package");
        } else if (qName.equals(INTENT_FILTER)) {
            isIntentFilterRegistered = true;
        } else if (componentsToDetect.contains(qName)) {
            tempCompName = attributes.getValue("android:name");
            isCompExported = (attributes.getValue("android:exported") != null
                    && attributes.getValue("android:exported").equals("true"));
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
        if (componentsToDetect.contains(qName)) {
            if (isCompExported || isIntentFilterRegistered) {
                String finalClassName;
                if (tempCompName.indexOf('.') > 0)
                    finalClassName = tempCompName;
                else {
                    finalClassName = packageName;
                    if (tempCompName.startsWith("."))
                        finalClassName += tempCompName;
                    else
                        finalClassName += ("." + tempCompName);
                }
                finalClassName = finalClassName.replace('$', '.');
                epClasses.add(finalClassName);
            }
            tempCompName = "";
            isCompExported = false;
            isIntentFilterRegistered = false;
        }
    }
}
