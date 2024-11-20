package com.aacr.helpers.serializers;

import com.ibm.wala.classLoader.JavaLanguage;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.types.FieldReference;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class SSAGetInstructionSerializer {

  public static void write(ObjectOutputStream output, SSAGetInstruction object) throws IOException {
    output.writeInt(object.iIndex()); //iindex
    output.writeInt(object.getDef()); //result
    output.writeInt(object.getRef()); //ref
    FieldReferenceSerializer.write(output, object.getDeclaredField()); //fieldreference
  }

  public static SSAGetInstruction read(ObjectInputStream input) throws IOException, ClassNotFoundException {
    int iindex = input.readInt();
    int result = input.readInt();
    int ref = input.readInt();
    FieldReference fieldReference = FieldReferenceSerializer.read(input);

    JavaLanguage jl = new JavaLanguage();
    return jl.instructionFactory().GetInstruction(iindex, result, ref, fieldReference);
  }
}