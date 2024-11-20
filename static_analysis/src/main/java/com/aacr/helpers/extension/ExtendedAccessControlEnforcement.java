package com.aacr.helpers.extension;

import com.aacr.helpers.accesscontrol.framework.AccessControlEnforcement;

import java.util.ArrayList;

public class ExtendedAccessControlEnforcement {
    public enum Location{
        PRESENT,
        REACHABLE;
    }

    private ArrayList<AccessControlEnforcement> present;
    private ArrayList<AccessControlEnforcement> reachable;

    public ExtendedAccessControlEnforcement() {
        this.present = new ArrayList<>();
        this.reachable = new ArrayList<>();
    }

    public ExtendedAccessControlEnforcement(ArrayList<AccessControlEnforcement> present, ArrayList<AccessControlEnforcement> reachable) {
        this.present = present;
        this.reachable = reachable;
    }


    public void addAccessControl(AccessControlEnforcement ac, Location location) {
        if(location == Location.PRESENT) {
            this.present.add(ac);
        } else {
            this.reachable.add(ac);
        }
    }

    public void addPresentAccessControl(AccessControlEnforcement ac) {
        this.present.add(ac);
    }

    public void addPresentAccessControl(ArrayList<AccessControlEnforcement> acList) {
        this.present.addAll(acList);
    }

    public void addReachableAccessControl(AccessControlEnforcement ac) {
        this.reachable.add(ac);
    }

    public void addReachableAccessControl(ArrayList<AccessControlEnforcement> acList) {
        this.reachable.addAll(acList);
    }

    public ArrayList<AccessControlEnforcement> getPresentAccessControl() {
        return this.present;
    }

    public ArrayList<AccessControlEnforcement> getReachableAccessControl() {
        return this.reachable;
    }

    @Override
    public String toString() {
        String returnString = "Present Access Control: ";
        for(AccessControlEnforcement pAC : this.present) {
            returnString += "\n" + pAC.toString();
        }

        returnString += "\n\nReachable Access Control: ";
        for(AccessControlEnforcement rAC : this.reachable) {
            returnString += "\n" + rAC.toString();
        }
        return returnString;
    }
}
