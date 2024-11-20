package com.aacr.helpers.serializers;

import com.aacr.helpers.accesscontrol.framework.AccessControlEnforcement;
import com.aacr.helpers.accesscontrol.framework.AccessControlWrapper;
import com.aacr.helpers.accesscontrol.framework.ComparativeAccessControlCheck;
import com.aacr.helpers.accesscontrol.framework.PermissionCheck;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

public class AccessControlListSerializer {

  public static void write(ObjectOutputStream output, ArrayList<AccessControlEnforcement> object) throws IOException {
    output.writeInt(object.size());
    for(AccessControlEnforcement ac : object) {
      if(ac instanceof PermissionCheck) {
        output.writeObject("P");
        PermissionCheckSerializer.write(output, (PermissionCheck) ac);
      } else if(ac instanceof ComparativeAccessControlCheck){
        output.writeObject("C");
        ComparativeAccessControlCheckSerializer.write(output, (ComparativeAccessControlCheck) ac);
      } else {
        output.writeObject("W");
        ACWrapperSerializer.write(output, (AccessControlWrapper) ac);
      }
    }
  }

  public static ArrayList<AccessControlEnforcement> read(ObjectInputStream input) throws IOException, ClassNotFoundException {
    int numAccessControl = input.readInt();
    ArrayList<AccessControlEnforcement> acList = new ArrayList<>();
    for(int i = 0; i < numAccessControl; i++) {
      String acType = (String) input.readObject();
      if(acType.equals("P")) {
        acList.add(PermissionCheckSerializer.read(input));
      } else if(acType.equals("C")) {
        acList.add(ComparativeAccessControlCheckSerializer.read(input));
      } else {
        acList.add(ACWrapperSerializer.read(input));
      }
    }
    return acList;
  }
}
