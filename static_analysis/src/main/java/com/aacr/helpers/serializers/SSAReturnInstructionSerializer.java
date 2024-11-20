package com.aacr.helpers.serializers;

import com.ibm.wala.classLoader.JavaLanguage;
import com.ibm.wala.ssa.SSAReturnInstruction;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class SSAReturnInstructionSerializer {

  public static void write(ObjectOutputStream output, SSAReturnInstruction object) throws IOException {
    output.writeInt(object.iIndex());
    output.writeInt(object.getResult());
    output.writeBoolean(object.returnsPrimitiveType());
  }

  public static SSAReturnInstruction read(ObjectInputStream input) throws IOException, ClassNotFoundException {
    int iindex = input.readInt();
    int result = input.readInt();
    boolean isPrimitive = input.readBoolean();

    JavaLanguage jl = new JavaLanguage();
    return jl.instructionFactory().ReturnInstruction(iindex, result, isPrimitive);
  }
}
