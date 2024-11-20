package com.aacr.helpers.accesscontrol.app;

import com.aacr.helpers.parsers.models.view.WidgetResource;

import java.util.ArrayList;
import java.util.Collection;

public class Protection {
    // Which kind of protection is it?
    private final Type protectionType;

    // Common protection fields for all types
    private final Component originatingComponent;

    // Manifest based traditional access control protection
    // For UI type, all these fields will be invalid / null
    private final boolean isExported;
    private final boolean isIntentFilterRegistered;
    private final String permission;
    private final ArrayList<String> actions;
    private final ArrayList<String> categories;

    // UI related protection which can be used to infer access control
    // For Manifest type, all these fields will be invalid / null
    private final Collection<WidgetResource> possibleWidgets;
    private final Collection<String> dynamicStrings;
//    private final ArrayList<String> allRelatedDrawables;

    // This constructor is only used for creating Manifest based protections
    public Protection(boolean isExported,
                      boolean isIntentFilterRegistered,
                      String permission,
                      ArrayList<String> actions,
                      ArrayList<String> categories, Component originatingComponent) {
        this.protectionType = Type.MANIFEST;
        this.isExported = isExported;
        this.isIntentFilterRegistered = isIntentFilterRegistered;
        this.permission = permission;
        this.actions = actions;
        this.categories = categories;
        this.originatingComponent = originatingComponent;

        // Initialize non-related properties to null
        this.possibleWidgets = null;
        this.dynamicStrings = null;
//        allRelatedDrawables = null;
    }

    // This constructor is only used for creating UI based protections
    public Protection(Collection<WidgetResource> possibleWidgets, //ArrayList<String> allRelatedDrawables,
                       Component originatingActivity) {
        this.protectionType = Type.UI;
        this.possibleWidgets = possibleWidgets;
        this.dynamicStrings = new ArrayList<>();
//        this.allRelatedDrawables = allRelatedDrawables;
        this.originatingComponent = originatingActivity;

        // Initialize all non-related fields as false/null
        this.isExported = false;
        this.isIntentFilterRegistered = false;
        this.permission = null;
        this.actions = null;
        this.categories = null;
    }

    @Override
    public String toString() {
        StringBuilder protectionStr = new StringBuilder("AppComponentProtection{ Type=" + protectionType);
        if (Type.MANIFEST == protectionType) {
            protectionStr.append(" isExported=").append(isExported)
                    .append("   isIntentFilterRegistered=").append(isIntentFilterRegistered)
                    .append("   protected with permission=").append(permission)
                    .append("   actions=").append(actions)
                    .append("   categories=").append(categories)
                    .append("   originatingComponent=").append(originatingComponent.toStringWithoutProtection());
        } else if (Type.UI == protectionType) {
            protectionStr.append("    possibleWidgets=").append(possibleWidgets)
                    .append("    dynamicStrings=").append(dynamicStrings)
//                    .append(", allRelatedRes=").append(allRelatedDrawables)
                    .append(" originatingComponent=").append(originatingComponent.toStringWithoutProtection()).append('}');
        }
        protectionStr.append('}');

        return protectionStr.toString();
    }

    public String toStringWithoutComponent() {
        StringBuilder protectionStr = new StringBuilder("AppComponentProtection{ Type=" + protectionType);
        if (Type.MANIFEST == protectionType) {
            protectionStr.append("   isExported=").append(isExported)
                    .append("   isIntentFilterRegistered=").append(isIntentFilterRegistered)
                    .append("   protected with permission=").append(permission)
                    .append("   actions=").append(actions)
                    .append("   categories=").append(categories);
        } else if (Type.UI == protectionType) {
            protectionStr.append("    possibleWidgets=").append(possibleWidgets)
                    .append("    dynamicStrings=").append(dynamicStrings);
//                    .append(", allRelatedRes=").append(allRelatedDrawables);
        }
        protectionStr.append("}");

        return protectionStr.toString();
    }

    public Component getOriginatingComponent() {
        return originatingComponent;
    }

    public Type getProtectionType() {
        return protectionType;
    }

    public void addDynamicStrings(Collection<String> dynamicStrings) {
        this.dynamicStrings.addAll(dynamicStrings);
    }

//    public ArrayList<String> getAllRelatedDrawables() {
//        return allRelatedDrawables;
//    }

    public enum Type {
        MANIFEST,
        UI,
        ;
    }
}
