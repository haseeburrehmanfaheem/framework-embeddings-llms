package com.aacr.helpers.utils;

import com.ibm.wala.cfg.cdg.ControlDependenceGraph;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAGetCaughtExceptionInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.util.graph.EdgeManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

/**
 * This class contains utility methods for Control Dependence Graphs (CDGs).
 */
public class ControlDependenceUtils {

  /**
   * This method relies on a Control Dependence Graph's edge manager to partition
   * instructions within a node into control blocks. For instance, instructions within
   * @param nd
   * @param bb
   * @param i
   * @param cdg
   * @return
   */
  public static HashMap<Integer, ArrayList<SSAInstruction>> associateInstructionsWithBlocks(
      CGNode nd, ISSABasicBlock bb, SSAInstruction i, ControlDependenceGraph<ISSABasicBlock> cdg) {
    HashMap<Integer, ArrayList<SSAInstruction>> blocksMap = new HashMap<>();
    if(i instanceof SSAConditionalBranchInstruction || i instanceof SSAGetCaughtExceptionInstruction) {
      EdgeManager<ISSABasicBlock> em = cdg.getEdgeManager();
      Iterator<ISSABasicBlock> succNodes = em.getSuccNodes(bb);
      int succCount = 0;
      while(succNodes.hasNext()) {
        ISSABasicBlock currentBB = succNodes.next();
        ArrayList<SSAInstruction> blockInstructions = getSingleBlockInstructions(nd, currentBB, em);
        blocksMap.put(succCount, blockInstructions);
        succCount++;
      }
    }
    return blocksMap;
  }

  private static ArrayList<SSAInstruction> getSingleBlockInstructions(CGNode nd, ISSABasicBlock startBB, EdgeManager<ISSABasicBlock> em) {
    ArrayList<SSAInstruction> instructionsInBlock = new ArrayList<>();
    Iterator<ISSABasicBlock> succNodes = em.getSuccNodes(startBB);
    while(succNodes.hasNext()) {
      ISSABasicBlock nextBB = succNodes.next();
      Iterator<SSAInstruction> instructions = nextBB.iterator();
      while (instructions.hasNext()) {
        SSAInstruction currentInstruction = instructions.next();
        if (currentInstruction != null) {
          instructionsInBlock.add(currentInstruction);
        }
      }
    }
    return instructionsInBlock;
  }
}