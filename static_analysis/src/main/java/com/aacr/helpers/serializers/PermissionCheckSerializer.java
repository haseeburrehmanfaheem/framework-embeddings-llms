package com.aacr.helpers.serializers;

import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.aacr.helpers.accesscontrol.framework.PermissionCheck;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

public class PermissionCheckSerializer {

  public static void write(ObjectOutputStream output, PermissionCheck object) throws IOException {
    output.writeInt(object.getIndex());
    SSAAbstractInvokeInstructionSerializer.write(output, (SSAAbstractInvokeInstruction) object.getInstruction());
    MethodReferenceSerializer.write(output, object.getCallingMethod());
    TypeReferenceSerializer.write(output ,object.getCallingMethodClass());
    output.writeObject(object.getEnforcement());
  }

  public static PermissionCheck read(ObjectInputStream input)
      throws IOException, ClassNotFoundException {
    int index = input.readInt();
    SSAAbstractInvokeInstruction invokeInstruction =  SSAAbstractInvokeInstructionSerializer.read(input);
    MethodReference callingMethod = MethodReferenceSerializer.read(input);
    TypeReference callingMethodClass = TypeReferenceSerializer.read(input);
    ArrayList<Object> enforcement = (ArrayList<Object>) input.readObject();
    return new PermissionCheck(index, invokeInstruction, callingMethod, callingMethodClass, enforcement);
  }
}