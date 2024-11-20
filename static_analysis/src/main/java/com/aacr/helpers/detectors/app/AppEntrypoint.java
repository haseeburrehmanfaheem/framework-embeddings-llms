package com.aacr.helpers.detectors.app;

import com.ibm.wala.analysis.typeInference.ConeType;
import com.ibm.wala.analysis.typeInference.PrimitiveType;
import com.ibm.wala.analysis.typeInference.TypeAbstraction;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.impl.AbstractRootMethod;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.shrike.shrikeBT.IInvokeInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;

public class AppEntrypoint extends DefaultEntrypoint {

    private final TypeReference curType;

    public AppEntrypoint(IMethod method, IClassHierarchy cha, TypeReference curType) {
        super(method, cha);
        this.curType = curType;
    }

    public AppEntrypoint(MethodReference method, IClassHierarchy cha, TypeReference curType) {
        super(method, cha);
        this.curType = curType;
    }

    @Override
    protected int makeArgument(AbstractRootMethod m, int i) {
        TypeReference[] p = getParameterTypes(i);
        switch (p.length) {
            case 0:
                return -1;
            case 1:
                if (p[0].isPrimitiveType()) {
                    return m.addLocal();
                } else {
                    SSANewInstruction n;
                    if (method.isStatic())
                        n = processAllocation(m, p[0]);
                    else
                        n = processAllocation(m, curType);
                    return (n == null) ? -1 : n.getDef();
                }
            default:
                int[] values = new int[p.length];
                int countErrors = 0;
                for (int j = 0; j < p.length; j++) {
                    SSANewInstruction n;
                    if (!method.isStatic() && j==0)
                        n = processAllocation(m, curType);
                    else
                        n = processAllocation(m, p[j]);
                    int value = (n == null) ? -1 : n.getDef();
                    if (value == -1) {
                        countErrors++;
                    } else {
                        values[j - countErrors] = value;
                    }
                }
                if (countErrors > 0) {
                    int[] oldValues = values;
                    values = new int[oldValues.length - countErrors];
                    System.arraycopy(oldValues, 0, values, 0, values.length);
                }

                TypeAbstraction a;
                if (p[0].isPrimitiveType()) {
                    a = PrimitiveType.getPrimitive(p[0]);
                    for (i = 1; i < p.length; i++) {
                        a = a.meet(PrimitiveType.getPrimitive(p[i]));
                    }
                } else {
                    IClassHierarchy cha = m.getClassHierarchy();
                    IClass p0 = cha.lookupClass(p[0]);
                    a = new ConeType(p0);
                    for (i = 1; i < p.length; i++) {
                        IClass pi = cha.lookupClass(p[i]);
                        a = a.meet(new ConeType(pi));
                    }
                }

                return m.addPhi(values);
        }
    }

    private SSANewInstruction processAllocation(AbstractRootMethod m, TypeReference typeReference) {
        IClass klass = getCha().lookupClass(typeReference);

        // If not an inner class, simply add a default allocation and move on
        if (klass == null || klass.getName() == null || !klass.getName().toString().contains("$"))
            return m.addAllocation(typeReference);

        String outerClassStr = typeReference.getName().toString().substring(0, typeReference.getName().toString().indexOf('$'));
        if (typeReference.getName().toString().contains("-$$Lambda$")) {
            outerClassStr = typeReference.getName().toString().replace("-$$Lambda$", "");
            outerClassStr = outerClassStr.substring(0, outerClassStr.lastIndexOf('$'));
        }
        TypeReference outerClzzT = TypeReference.find(getCha().getScope().getApplicationLoader(), outerClassStr);

        if (outerClzzT == null)
            return m.addAllocation(typeReference);

        m.addAllocation(outerClzzT);
        int outerClassInstance = m.nextLocal-1;
        SSANewInstruction ret = m.addAllocationWithoutCtor(typeReference);
        int innerClassInstance = m.nextLocal-1;

        // invoke constructor
        IMethod ctor = getCha().resolveMethod(klass, MethodReference.initSelector);

        boolean foundValidCtor = false;
        if (ctor != null) {
            if (!ctor.getDeclaringClass().getName().toString().equals(klass.getName().toString())) {
                for (IMethod im : klass.getAllMethods()) {
                    if (im.getDeclaringClass().getName().toString().equals(klass.getName().toString())
                            && im.getSelector()
                            .getName()
                            .toString()
                            .equals(MethodReference.initAtom.toString())) {
                        ctor = im;
                        // found a default constructor that takes only the outer class as a parameter
                        if (im.getDescriptor().getNumberOfParameters() == 1) {
                            foundValidCtor = true;
                            break;
                        }
                    }
                }
            }
        }

        if (!foundValidCtor)
            return ret;

        int[] params = new int[] {innerClassInstance, outerClassInstance};

        m.addInvocation(params, CallSiteReference.make(m.statements.size(), ctor.getReference(), IInvokeInstruction.Dispatch.SPECIAL));

        return ret;
    }
}
