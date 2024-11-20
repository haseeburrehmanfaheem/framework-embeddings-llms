package com.aacr.helpers.serializers;

import com.aacr.helpers.accesscontrol.framework.ComparativeAccessControlCheck.ACType;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class ACTypeSerializer {

  public static void write(ObjectOutputStream output, ACType object) throws IOException {
    output.writeInt(object.ordinal());
  }

  public static ACType read(ObjectInputStream input) throws IOException, ClassNotFoundException {
    return ACType.values()[input.readInt()];
  }
}
