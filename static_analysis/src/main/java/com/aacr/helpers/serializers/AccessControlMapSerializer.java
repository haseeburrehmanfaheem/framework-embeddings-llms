package com.aacr.helpers.serializers;

import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.aacr.helpers.accesscontrol.framework.AccessControlEnforcement;
import com.aacr.helpers.extension.ExtendedAccessControlEnforcement;
import com.aacr.helpers.extension.ExtendedMethodReference;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;

public class AccessControlMapSerializer {

  public static void write(ObjectOutputStream output, HashMap<ExtendedMethodReference, ExtendedAccessControlEnforcement> object) throws IOException {
    output.writeInt(object.size());
    for (HashMap.Entry<ExtendedMethodReference, ExtendedAccessControlEnforcement> entry : object.entrySet()){
      TypeReferenceSerializer.write(output, entry.getKey().getClassReference());
      MethodReferenceSerializer.write(output, entry.getKey().getMethodReference());
      AccessControlListSerializer.write(output, entry.getValue().getPresentAccessControl());
      AccessControlListSerializer.write(output, entry.getValue().getReachableAccessControl());
    }
  }

  public static HashMap<ExtendedMethodReference, ExtendedAccessControlEnforcement> read(ObjectInputStream input) throws IOException, ClassNotFoundException {
    HashMap<ExtendedMethodReference, ExtendedAccessControlEnforcement> accessControlMap = new HashMap<>();
    int numEntries = input.readInt();
    int entriesRead = 0;
    while(entriesRead < numEntries) {
      TypeReference typeReference =  TypeReferenceSerializer.read(input);
      MethodReference methodReference = MethodReferenceSerializer.read(input);
      ArrayList<AccessControlEnforcement> presentList = AccessControlListSerializer.read(input);
      ArrayList<AccessControlEnforcement> reachableList = AccessControlListSerializer.read(input);
      accessControlMap.put(new ExtendedMethodReference(typeReference, methodReference), new ExtendedAccessControlEnforcement(presentList, reachableList));
      entriesRead++;
    }
    return accessControlMap;
  }
}