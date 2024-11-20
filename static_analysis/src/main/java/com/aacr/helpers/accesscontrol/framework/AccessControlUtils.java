package com.aacr.helpers.accesscontrol.framework;

import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;


public class AccessControlUtils {

    /**
     * This enum organizes access control-related methods based, primarily based
     * on whether they are enforcing (meaning that they throw SecurityExceptions),
     * checking (meaning that they return some boolean value), or getting, meaning
     * that they return some access control-related value, such as uid or pid.
     */
    public enum MethodSet {
        PermissionEnforcementMethods(new HashSet<>(Arrays.asList(
                "enforceCallingOrSelfPermission",
                "enforceCallingOrSelfUriPermission",
                "enforceCallerIsRecentsOrHasPermission",
                "enforceCallingPermission",
                "enforceCallingUriPermission",
                "enforcePermission",
                "enforceUriPermission",
                "enforceAnyPermissionOf"
        ))),

        PermissionCheckingMethods(new HashSet<>(Arrays.asList(
                "checkCallingOrSelfPermission",
                "checkCallingOrSelfUriPermission",
                "checkCallingOrSelfUriPermissions",
                "checkCallingPermission",
                "checkCallingUriPermission",
                "checkCallingUriPermissions",
                "checkPermission",
                "checkSelfPermission",
                "checkUriPermission",
                "checkUriPermissions",
                "checkComponentPermission",
                "checkAnyPermissionOf",
                "checkUidPermission"
        ))),

        GetUidMethods(new HashSet<>(Arrays.asList(
                "getUidForName",
                "getUidForPid",
                "getUid",
                "getCallingUid",
                "myUid"
        ))),

        CheckUidMethods(new HashSet<>(Arrays.asList(
                "isCoreUid",
                "isApplicationUid",
                "isApp",
                "isCore",
                "isIsolated",
                "isSameApp"
        ))),

        GetPidMethods(new HashSet<>(Arrays.asList(
                "myPid",
                "myPpid",
                "getParentPid",
                "getCallingPid",
                "getPid"
        ))),

        GetAppIdMethods(new HashSet<>(Arrays.asList(
                "getAppId",
                "getAppIdFromSharedAppGid",
                "getCallingAppId"
        ))),

        GetGidMethods(new HashSet<>(Arrays.asList(
                "getGidForName",
                "getUserGid",
                "getSharedAppGid",
                "getCacheAppGid"
        ))),

        CheckGidMethods(new HashSet<>(Arrays.asList(
                "isSharedAppGid"
        ))),

        GetTidMethods(new HashSet<>(Arrays.asList(
                "myTid"
        ))),

        GetUserHandleMethods(new HashSet<>(Arrays.asList(
                "myUserHandle",
                "getUserHandleForUid"
        ))),

        GetUserIdMethods(new HashSet<>(Arrays.asList(
                "getUserId",
                "getCallingUserId",
                "getIdentifier",
                "getCurrentUser",
                "getCurrentUserId",
                "getUserRestriction",
                "handleIncomingUser"
        ))),

        GetActionMethods(new HashSet<>(Arrays.asList(
                "getAction"
        ))),

        CheckUserHandleMethods(new HashSet<>(Arrays.asList(
                "isApp",
                "isCore"
        ))),

        CheckAppTypeMethods(new HashSet<>(Arrays.asList(
                "isCoreUid"
        ))),

        CheckUserStatusMethods(new HashSet<>(Arrays.asList(
                "hasUserRestriction",
                "hasUserRestrictionOnAnyUser",
                "hasUserRestrictionForUser",
                "isSettingRestrictedForUser",
                "isSameProfileGroup",
                "isAdminUser",
                "isSystemUser",
                "isPrimaryUser",
                "isSameUser"
        ))),

        SignatureCheckMethods(new HashSet<>(Arrays.asList(
                "compareSignatures",
                "checkSignatures",
                "checkSignature"
        ))),

        PackageCheckMethods(new HashSet<>(Arrays.asList(
                "checkPackage",
                "getInstantAppPackageName",
                "getPackageUid",
                "getPackageName",
                "checkPackageName",
                "getPackageNameForUid",
                "getPackageFromAppProcesses",
                "verifyCallingPackage"
        )));

        public final HashSet<String> methodNames;
        MethodSet(HashSet<String> methodNames) {
            this.methodNames = methodNames;
        }
    }

    private static final HashSet<String> accessControlProperties = new HashSet<>(Arrays.asList("appid", "uid", "change", "modify", "access", "update", "permission", "isolated", "note", "gid", "pid", "user", "havepackage", "haspackage", "package", "forpackage", "signature", "system", "blacklist", "access", "identity", "caller", "isolate", "privilege", "security", "foreground", "background", "binder", "sameapp",
                                                                                                "profile", "name"));

    public static List<MethodSet> checkingMethods = Arrays.asList(
            MethodSet.CheckUidMethods,
            MethodSet.GetAppIdMethods,
            MethodSet.CheckGidMethods,
            MethodSet.CheckAppTypeMethods,
            MethodSet.CheckUserHandleMethods,
            MethodSet.CheckUserStatusMethods,
            MethodSet.SignatureCheckMethods,
            MethodSet.PackageCheckMethods
    );

    public static List<MethodSet> gettingMethods = Arrays.asList(
            MethodSet.GetUidMethods,
            MethodSet.GetGidMethods,
            MethodSet.GetAppIdMethods,
            MethodSet.GetPidMethods,
            MethodSet.GetTidMethods,
            MethodSet.GetUserHandleMethods,
            MethodSet.GetUserIdMethods,
            MethodSet.GetActionMethods
    );

    /**
     * Determines whether inputted SSAAbstractInvokeInstruction's target is
     * an access control-related method contained in the passed MethodSet.
     * @param i SSAAbstractInvokeInstruction of interest.
     * @param ms MethodSet representing some subset of access control-related methods.
     * @return A boolean indicating whether the inputted SSAAbstractInvokeInstruction's target is
     * found in the passed MethodSet.
     */
    public static boolean containsTarget(SSAAbstractInvokeInstruction i, MethodSet ms) {
        return ms.methodNames.contains(i.getDeclaredTarget().getSelector().getName().toString());
    }

    /**
     * Determines whether inputted SSAAbstractInvokeInstruction's target is
     * a permission enforcing access control-related method.
     * @param i SSAAbstractInvokeInstruction of interest.
     * @return A boolean indicating whether the inputted SSAAbstractInvokeInstruction is a permission
     * enforcing method.
     */
    public static boolean isPermissionEnforcingMethod(SSAAbstractInvokeInstruction i) {
        for (String enforce : MethodSet.PermissionEnforcementMethods.methodNames) {
            if(i.getDeclaredTarget().getName().toString().equals(enforce)) {// &&
                //i.getDeclaredTarget().getDeclaringClass().getName().toString().contains("Context")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Determines whether inputted SSAAbstractInvokeInstruction's target is
     * a permission checking access control-related method.
     * @param i SSAAbstractInvokeInstruction of interest.
     * @return A boolean indicating whether the inputted SSAAbstractInvokeInstruction is a permission
     * checking method.
     */
    public static boolean isPermissionCheckingMethod(SSAAbstractInvokeInstruction i) {
        for (String check : MethodSet.PermissionCheckingMethods.methodNames) {
            if(i.getDeclaredTarget().getName().toString().equals(check)) {// &&
                //i.getDeclaredTarget().getDeclaringClass().getName().toString().contains("Context")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines whether inputted SSAAbstractInvokeInstruction is checking.
     * @param i SSAAbstractInvokeInstruction of interest.
     * @return A boolean indicating whether the inputted SSAAbstractInvokeInstruction's target is checking.
     */
    public static boolean isCheckingMethod(SSAAbstractInvokeInstruction i) {
        for (MethodSet check : checkingMethods) {
            if (containsTarget(i, check)) return true;
        }
        return false;
    }

    /**
     * Determines whether inputted SSAAbstractInvokeInstruction is getting.
     * @param i SSAAbstractInvokeInstruction of interest.
     * @return A boolean indicating whether the inputted SSAAbstractInvokeInstruction's target is getting.
     */
    public static boolean isGettingMethod(SSAAbstractInvokeInstruction i) {
        for (MethodSet get : gettingMethods) {
            if (containsTarget(i, get)) return true;
        }
        return false;
    }

    public static boolean nameRelatedToAccessControl(String s) {
        if(s.toLowerCase().contains("access") && s.toLowerCase().contains("$")){
            return true;
        }

        if(s.toLowerCase().contains("this") && s.toLowerCase().contains("$")){
            return true;
        }

        return highConfidenceStartingName(s) || (hasCheckingName(s) && nameRelatedToAccessControlProperties(s));
    }

    public static boolean nameRelatedToAccessControlGet(String s) {
        if(s.toLowerCase().contains("access") && s.toLowerCase().contains("$")){
            return true;
        }

        if(s.toLowerCase().contains("this") && s.toLowerCase().contains("$")){
            return true;
        }

        return hasGettingName(s) && nameRelatedToAccessControlProperties(s);
    }


    public static boolean isAccessCheckInvoke(SSAAbstractInvokeInstruction i){
        return isPermissionCheckingMethod(i) || isPermissionEnforcingMethod(i) || isCheckingMethod(i) || isGettingMethod(i) || nameRelatedToAccessControl(i.getDeclaredTarget().getName().toString())
                || nameRelatedToAccessControlGet(i.getDeclaredTarget().getName().toString());
    }

    public static boolean isAccessCheckInvokeLight(SSAAbstractInvokeInstruction i){
        return isPermissionCheckingMethod(i) || isPermissionEnforcingMethod(i) || isCheckingMethod(i) || isGettingMethod(i);
    }

    private static boolean hasCheckingName(String targetName) {
        return targetName.toLowerCase().startsWith("is") ||
                targetName.toLowerCase().startsWith("can") && !targetName.toLowerCase().startsWith("cancel") ||
                targetName.toLowerCase().startsWith("my") ||
                targetName.toLowerCase().startsWith("called") ||
                targetName.toLowerCase().contains("valid") ||
                targetName.toLowerCase().startsWith("enforce") ||
                targetName.toLowerCase().startsWith("ensure") ||
                targetName.toLowerCase().startsWith("check") ||
                targetName.toLowerCase().startsWith("restrict") ||
                targetName.toLowerCase().startsWith("has") ||
                targetName.toLowerCase().startsWith("verify") ||
                targetName.toLowerCase().startsWith("note") ||
                (targetName.toLowerCase().contains("does") && targetName.toLowerCase().contains("have"));
    }

    private static boolean highConfidenceStartingName(String targetName){
        return
                targetName.toLowerCase().contains("valid") ||
                targetName.toLowerCase().startsWith("enforce") ||
                targetName.toLowerCase().startsWith("ensure") ||
                targetName.toLowerCase().startsWith("check") ||
                targetName.toLowerCase().startsWith("verify") ||
                targetName.toLowerCase().startsWith("restrict");
    }

    private static boolean hasGettingName(String targetName) {
        return targetName.toLowerCase().startsWith("get") ||
                targetName.toLowerCase().startsWith("my");
    }

    private static boolean nameRelatedToAccessControlProperties(String methodName) {
        return accessControlProperties.stream().anyMatch(p -> methodName.toLowerCase().contains(p));
    }
}
