package com.aacr.helpers.serializers;

import com.ibm.wala.classLoader.JavaLanguage;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.types.FieldReference;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class SSAPutInstructionSerializer {

  public static void write(ObjectOutputStream output, SSAPutInstruction object) throws IOException {
    output.writeInt(object.iIndex());
    output.writeInt(object.getRef());
    output.writeInt(object.getVal());
    FieldReferenceSerializer.write(output, object.getDeclaredField());
  }

  public static SSAPutInstruction read(ObjectInputStream input) throws IOException, ClassNotFoundException {
    int iindex = input.readInt();
    int ref = input.readInt();
    int value = input.readInt();
    FieldReference fieldReference = FieldReferenceSerializer.read(input);

    JavaLanguage jl = new JavaLanguage();
    return jl.instructionFactory().PutInstruction(iindex, ref, value, fieldReference);
  }
}
