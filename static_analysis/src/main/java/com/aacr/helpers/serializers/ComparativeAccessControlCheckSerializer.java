package com.aacr.helpers.serializers;

import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.aacr.helpers.accesscontrol.framework.ComparativeAccessControlCheck;
import com.aacr.helpers.accesscontrol.framework.ComparativeAccessControlCheck.ACType;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class ComparativeAccessControlCheckSerializer {

  public static void write(ObjectOutputStream output, ComparativeAccessControlCheck object)
      throws IOException {
    output.writeInt(object.getIndex());
    ACTypeSerializer.write(output, object.getType());
    MethodReferenceSerializer.write(output, object.getCallingMethod());
    TypeReferenceSerializer.write(output, object.getCallingMethodClass());

    SSAConditionalBranchInstructionSerializer.write(output,
        (SSAConditionalBranchInstruction) object.getInstruction());
    SSAAbstractInvokeInstructionSerializer.write(output, object.getAccessControlInstruction());

    output.writeObject(object.getOperator());

    Object otherValue = object.getOtherValue();
    if(otherValue == null) {
      output.writeObject("NULL");
    } else {
      if(otherValue instanceof SSAAbstractInvokeInstruction) {
        output.writeObject("INVOKE");
        SSAAbstractInvokeInstructionSerializer.write(output,
            (SSAAbstractInvokeInstruction) otherValue);
      } else if(otherValue instanceof Integer) {
        output.writeObject("NUMBER");
        output.writeInt((Integer) otherValue);
      } else if(otherValue instanceof String) {
        output.writeObject("STRING");
        output.writeObject(otherValue);
      } else {
        output.writeObject("NULL");
      }
    }
  }

  public static ComparativeAccessControlCheck read(ObjectInputStream input)
      throws IOException, ClassNotFoundException {
    int index = input.readInt();
    ACType acType = ACTypeSerializer.read(input);
    MethodReference callingMethod = MethodReferenceSerializer.read(input);
    TypeReference callingMethodClass = TypeReferenceSerializer.read(input);

    SSAConditionalBranchInstruction conditionalInstruction = SSAConditionalBranchInstructionSerializer.read(input);
    SSAAbstractInvokeInstruction accessControlCheck = SSAAbstractInvokeInstructionSerializer.read(input);

    String operator = (String) input.readObject();


    String otherValueStatus = (String) input.readObject();
    Object otherValue = null;
    if(otherValueStatus.equals("INVOKE")) {
      otherValue = SSAAbstractInvokeInstructionSerializer.read(input);
    } else if(otherValueStatus.equals("NUMBER")) {
      otherValue = input.readInt();
    } else if(otherValueStatus.equals("STRING")) {
      otherValue = input.readObject();
    }
    return new ComparativeAccessControlCheck(index, acType, callingMethod, callingMethodClass, conditionalInstruction, accessControlCheck, operator, otherValue);
  }
}