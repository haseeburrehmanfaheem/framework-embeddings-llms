package com.aacr.helpers.serializers;

import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.aacr.helpers.accesscontrol.framework.AccessControlWrapper;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashSet;

public class ACWrapperSerializer {

  public static void write(ObjectOutputStream output, AccessControlWrapper object)
      throws IOException {
    output.writeInt(object.getIndex());
    MethodReferenceSerializer.write(output, object.getCallingMethod());
    TypeReferenceSerializer.write(output, object.getCallingMethodClass());
    SSAAbstractInvokeInstructionSerializer.write(output,
        (SSAAbstractInvokeInstruction) object.getInstruction());
    int numStrings = object.getCheckingFor().size();
    output.writeInt(numStrings);
    HashSet<String> checkedStrings = object.getCheckingFor();
    for(String checkedString : checkedStrings) {
      output.writeObject(checkedString);
    }
  }

  public static AccessControlWrapper read(ObjectInputStream input)
      throws IOException, ClassNotFoundException {
    int index = input.readInt();
    MethodReference callingMethod = MethodReferenceSerializer.read(input);
    TypeReference callingMethodClass = TypeReferenceSerializer.read(input);
    SSAAbstractInvokeInstruction accessControlCheck = SSAAbstractInvokeInstructionSerializer.read(input);
    int numStrings = input.readInt();
    HashSet<String> checkedStrings = new HashSet<>();
    for(int i = 0; i < numStrings; i++) {
      String checkedString = (String) input.readObject();
      checkedStrings.add(checkedString);
    }
    return new AccessControlWrapper(index, callingMethod, callingMethodClass, accessControlCheck, checkedStrings);
  }
}
