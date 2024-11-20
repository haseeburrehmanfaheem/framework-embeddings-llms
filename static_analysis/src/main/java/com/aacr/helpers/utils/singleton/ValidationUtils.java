package com.aacr.helpers.utils.singleton;

import java.util.Map;

// Property Utils must be initialized with all required custom properties before initializing this class
// Because the usage string depends on the property strings
// Preferably use UtilsInitializer instead of invoking init directly
public class ValidationUtils {

    private static ValidationUtils instance = null;

    // Inferred from task name provided during initialization and
    // property strings from the PropertyUtils
    private String usageStr;

    // Must be invoked precisely once, and in order AFTER initializing PropertyUtils
    public static synchronized void init(String taskName) {
        if (instance == null) {
            instance = new ValidationUtils(taskName);
        } else {
            throw new RuntimeException("Already initialized - cannot change the project name on runtime");
        }
    }

    // Can be used as frequently as needed but AFTER initialization
    public static synchronized ValidationUtils getInstance() {
        if (instance == null)
            throw new RuntimeException("Must initialize before getting instance");
        return instance;
    }

    // Hide constructor for Singleton enforcement
    private ValidationUtils(String taskName) {
        initUsageString(taskName);
    }

    // The logic to create (infer) usage string from `gradle` keyword, task name and property strings
    private void initUsageString(String taskName) {
        StringBuilder usageStrBuilder = new StringBuilder("Usage: gradle " + taskName);
        for (Map.Entry<String, String> prop : PropertyUtils.getInstance().getAllPropStrings().entrySet()) {
            usageStrBuilder.append(" -D").append(prop.getKey()).append("=[");
            if (prop.getValue() == null)
                usageStrBuilder.append("value");
            else
                usageStrBuilder.append(prop.getValue());
            usageStrBuilder.append("]");
        }
        this.usageStr = usageStrBuilder.toString();
    }

    private void exitPrintingError() {
        System.out.println(usageStr);
        System.exit(0);
    }

    public void validateProperties() {
        for (Map.Entry<String, String> property : PropertyUtils.getInstance().getAllPropStrings().entrySet()) {
            String propVal = PropertyUtils.getInstance().getProperty(property.getKey());
            if (propVal == null || propVal.isEmpty()) {
                exitPrintingError();
            }
        }
    }
}
