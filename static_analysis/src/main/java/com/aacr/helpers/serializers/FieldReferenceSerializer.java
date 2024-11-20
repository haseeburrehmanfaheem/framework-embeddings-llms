package com.aacr.helpers.serializers;

import com.ibm.wala.core.util.strings.Atom;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.TypeReference;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class FieldReferenceSerializer {

  public static void write(ObjectOutputStream output, FieldReference object) throws IOException {
    TypeReferenceSerializer.write(output, object.getDeclaringClass());
    output.writeObject(object.getName());
    TypeReferenceSerializer.write(output, object.getFieldType());
  }

  public static FieldReference read(ObjectInputStream input) throws IOException, ClassNotFoundException {
    TypeReference classTypeReference = TypeReferenceSerializer.read(input);
    Atom fieldAtom = (Atom) input.readObject();
    TypeReference fieldTypeReference = TypeReferenceSerializer.read(input);
    return FieldReference.findOrCreate(classTypeReference, fieldAtom, fieldTypeReference);
  }
}
