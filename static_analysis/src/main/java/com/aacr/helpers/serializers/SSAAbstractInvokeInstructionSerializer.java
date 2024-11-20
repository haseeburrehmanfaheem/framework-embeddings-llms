package com.aacr.helpers.serializers;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.JavaLanguage;
import com.ibm.wala.shrike.shrikeBT.IInvokeInstruction.IDispatch;
import com.ibm.wala.shrike.shrikeCT.BootstrapMethodsReader.BootstrapMethod;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInvokeDynamicInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.types.MethodReference;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class SSAAbstractInvokeInstructionSerializer {

  public static void write(ObjectOutputStream output, SSAAbstractInvokeInstruction object)
      throws IOException {
    output.writeInt(object.iIndex());
    int result = object.getNumberOfUses();
    output.writeInt(result);

    if(result != 0) {
      for (int i = 0; i < result; i++) {
        output.writeInt(((SSAInvokeInstruction) object).getUse(i));
      }
    }
    output.writeInt(object.getException());
    output.writeInt(object.getProgramCounter());
    MethodReferenceSerializer.write(output, object.getCallSite().getDeclaredTarget());
    IInvokeInstructionIDispatchSerializer.write(output, object.getCallSite().getInvocationCode());

    if(object instanceof SSAInvokeDynamicInstruction) {
      output.writeInt(1);
      output.writeObject(((SSAInvokeDynamicInstruction)object).getBootstrap());
    } else {
      output.writeInt(0);
    }
  }

  public static SSAAbstractInvokeInstruction read(ObjectInputStream input) throws IOException, ClassNotFoundException {
    int iindex = input.readInt();
    int result = input.readInt();

    int[] param = null;
    if (result != 0) {
      param = new int[result];
      for (int i = 0; i < result; i++) {
        param[i] = input.readInt();
      }
    }

    int exception = input.readInt();
    int pc = input.readInt();
    MethodReference methodReference = MethodReferenceSerializer.read(input);
    IDispatch dispatch = IInvokeInstructionIDispatchSerializer.read(input);
    CallSiteReference callSiteReference = CallSiteReference.make(pc, methodReference, dispatch);

    BootstrapMethod bm = null;
    int bootStrapStatus = input.readInt();
    if(bootStrapStatus == 1) {
      bm = (BootstrapMethod) input.readObject();
    }

    JavaLanguage jl = new JavaLanguage();
    return jl.instructionFactory().InvokeInstruction(iindex, result, param, exception, callSiteReference, bm);
  }
}
