package com.aacr.helpers.serializers;

import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class MethodReferenceSerializer {

  public static void write(ObjectOutputStream output, MethodReference object) throws IOException {
    output.writeObject(object.getDeclaringClass().getName().toString());
    output.writeObject(object.getName().toString());
    output.writeObject(object.getDescriptor().toString());
  }

  public static MethodReference read(ObjectInputStream input) throws IOException, ClassNotFoundException {
    String typeName = (String) input.readObject();
    String methodReferenceName = (String) input.readObject();
    String methodReferenceDescriptor = (String) input.readObject();
    TypeReference typeReference = TypeReference.findOrCreate(ClassLoaderReference.Application, typeName);
    return MethodReference.findOrCreate(typeReference, methodReferenceName, methodReferenceDescriptor);
  }
}