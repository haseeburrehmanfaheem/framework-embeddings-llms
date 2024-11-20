package com.aacr.helpers.accesscontrol.framework;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.SSAInstruction;


public abstract class AccessControlEnforcement implements IAccessControlEnforcement {

    private int index;
    private SSAInstruction instruction;

    /**
     * Called by inheriting classes to set the index and instruction
     * @see com.aacr.helpers.utils.InstructionUtils#getIndexForInstruction(SSAInstruction, CGNode)
     * @param index The instruction's index.
     * @param instruction The instruction associated with the access control enforcement.
     */
    public void setUpEnforcement(int index, SSAInstruction instruction) {
        this.index = index;
        this.instruction = instruction;
    }

    /**
     * Sets the index representing the instruction's location in the instruction array.
     * @param index The instruction's index.
     */
    public void setIndex(int index) {
        this.index = index;
    }

    /**
     * @return The index representing the instruction's location in the instruction array.
     */
    public int getIndex() {
        return this.index;
    }

    /**
     * Sets the instruction associated with the access control enforcement.
     * @param instruction The instruction.
     */
    public void setInstruction(SSAInstruction instruction) {
        this.instruction = instruction;
    }

    /**
     * @return The instruction associated with the access control enforcement.
     */
    public SSAInstruction getInstruction() {
        return this.instruction;
    }
}

