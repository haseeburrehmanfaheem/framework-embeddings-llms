package com.aacr.helpers.serializers;

import com.ibm.wala.shrike.shrikeBT.IInvokeInstruction;
import com.ibm.wala.shrike.shrikeBT.IInvokeInstruction.IDispatch;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class IInvokeInstructionIDispatchSerializer {

  public static void write(ObjectOutputStream output, IDispatch object) throws IOException {
    output.writeInt(((IInvokeInstruction.Dispatch)object).ordinal());
  }

  public static IDispatch read(ObjectInputStream input) throws IOException {
    return IInvokeInstruction.Dispatch.values()[input.readInt()];
  }
}
