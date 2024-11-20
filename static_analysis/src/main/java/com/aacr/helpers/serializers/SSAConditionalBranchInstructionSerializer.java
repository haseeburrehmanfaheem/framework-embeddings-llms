package com.aacr.helpers.serializers;

import com.ibm.wala.shrike.shrikeBT.IConditionalBranchInstruction;
import com.ibm.wala.shrike.shrikeBT.IConditionalBranchInstruction.Operator;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.types.TypeReference;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class SSAConditionalBranchInstructionSerializer {

  public static void write(ObjectOutputStream output, SSAConditionalBranchInstruction object)
      throws IOException {
    output.writeInt(object.iIndex());
    IConditionalBranchOperatorSerializer.write(output, (Operator) object.getOperator());
    TypeReferenceSerializer.write(output, object.getType());
    output.writeInt(object.getUse(0));
    output.writeInt(object.getUse(1));
    output.writeInt(object.getTarget());
  }

  public static SSAConditionalBranchInstruction read(ObjectInputStream input)
      throws IOException, ClassNotFoundException {
    int iindex = input.readInt();
    IConditionalBranchInstruction.Operator operator = IConditionalBranchOperatorSerializer.read(input);
    TypeReference typeReference = TypeReferenceSerializer.read(input);
    int val1 = input.readInt();
    int val2 = input.readInt();
    int target = input.readInt();
    return new SSAConditionalBranchInstruction(iindex, operator, typeReference, val1, val2, target);
  }
}
