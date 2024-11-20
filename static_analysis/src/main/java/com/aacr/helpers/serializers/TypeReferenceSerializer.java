package com.aacr.helpers.serializers;

import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeReference;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class TypeReferenceSerializer {

  public static void write(ObjectOutputStream output, TypeReference object) throws IOException {
    output.writeObject(object.getName().toString());
  }

  public static TypeReference read(ObjectInputStream input)
      throws IOException, ClassNotFoundException {
   return TypeReference.findOrCreate(ClassLoaderReference.Application, (String) input.readObject());
  }
}
