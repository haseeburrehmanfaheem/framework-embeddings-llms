package com.aacr.helpers.serializers;

import com.ibm.wala.shrike.shrikeBT.IConditionalBranchInstruction;
import com.ibm.wala.shrike.shrikeBT.IConditionalBranchInstruction.Operator;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class IConditionalBranchOperatorSerializer {

  public static void write(ObjectOutputStream output, Operator object) throws IOException {
    output.writeInt(object.ordinal());
  }

  public static Operator read(ObjectInputStream input) throws IOException, ClassNotFoundException {
    return IConditionalBranchInstruction.Operator.values()[input.readInt()];
  }
}
