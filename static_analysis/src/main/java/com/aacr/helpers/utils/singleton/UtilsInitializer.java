package com.aacr.helpers.utils.singleton;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;

//A helper class to easily initialize all utils singletons in correct order and format
public class UtilsInitializer {
    public static void initAllUtils(@Nonnull String gradleTaskName, @Nullable ArrayList<String> customPropStrings) {
        PropertyUtils.init(customPropStrings);
        ValidationUtils.init(gradleTaskName);
        ValidationUtils.getInstance().validateProperties();
    }

    public static void initAllUtils(@Nonnull String gradleTaskName, @Nullable HashMap<String, String> customPropStrings) {
        PropertyUtils.init(customPropStrings);
        ValidationUtils.init(gradleTaskName);
        ValidationUtils.getInstance().validateProperties();
    }
}
