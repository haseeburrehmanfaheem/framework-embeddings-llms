package com.aacr.helpers.detectors.framework.akka;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.aacr.helpers.detectors.framework.ResourceMapper_1;
import com.aacr.helpers.detectors.framework.akka.Monitor.UpdateChildCount;
import com.aacr.helpers.detectors.framework.akka.Worker.Message;
import com.aacr.helpers.utils.PrintUtils;
import com.aacr.wala.workshop.analyzers.DataCollection;
import com.aacr.wala.workshop.analyzers.FrameworkAnalyzer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * This class defines the Worker Akka Actor, which detects the access control for a subset of the total
 * framework entrypoints.
 */
public class Worker extends AbstractBehavior<Message> {

  interface Message {};

  /**
   * This class defines a message that can be sent to the Worker actor specifying the
   * entrypoints that it must analyze.
   */
  public static final class SendPartitionedEntrypoints implements Message {
    public final AnalysisScope scope;
    public final ClassHierarchy cha;
    public final ArrayList<DefaultEntrypoint> entrypointList;
    public final ActorRef<Monitor.Message> monitor;

    /**
     * SendPartitionedEntrypoints constructor.
     * @param scope AnalysisScope.
     * @param cha ClassHierarchy.
     * @param entrypointList List of entrypoints to be analyzed by this worker.
     * @param monitor Reference to the Monitor actor.
     */
    public SendPartitionedEntrypoints(AnalysisScope scope, ClassHierarchy cha, ArrayList<DefaultEntrypoint> entrypointList, ActorRef<Monitor.Message> monitor) {
      this.scope = scope;
      this.cha = cha;
      this.entrypointList = entrypointList;
      this.monitor = monitor;
    }
  }

  /**
   * Worker constructor.
   * @param context Provides access to the actor's identity and the ActorSystem.
   */
  public Worker(ActorContext<Message> context) {
    super(context);
  }

  /**
   * Sets up the worker.
   * @return A Behavior.
   */
  public static Behavior<Message> create() {
    return Behaviors.supervise(Behaviors.setup(Worker::new)).onFailure(Exception.class, SupervisorStrategy.resume());
  }

  /**
   * Specifies that when a message is received, onMessage(..) should be called.
   * @return Receive object.
   */
  @Override
  public Receive<Message> createReceive() {
    return newReceiveBuilder().onMessage(Message.class, this::onMessage).build();
  }

  /**
   * Handles the one type of incoming message, which is SendPartitionedEntrypoints.
   * Upon receipt of the entrypoints, the worker begins the analysis. When the analysis is complete,
   * the worker sends the results to the monitor.
   * @param message Message.
   * @return Behavior.
   */
//  private Behavior<Message> onMessage(
//      Message message) {
//    if(message instanceof SendPartitionedEntrypoints) {
//      HashMap<DefaultEntrypoint, HashMap<String, ArrayList<HashMap<String, String>>>> accessControlMap = detectAccessControlForFrameworkEntrypointsAkka(((SendPartitionedEntrypoints) message).scope, ((SendPartitionedEntrypoints) message).cha, ((SendPartitionedEntrypoints) message).entrypointList, ((SendPartitionedEntrypoints) message).monitor, getContext().getSelf());
//      ((SendPartitionedEntrypoints) message).monitor.tell(new Monitor.UpdateAccessControlInfo(accessControlMap));
//      return Behaviors.stopped();
//    }
//    return this;
//  }
  private Behavior<Message> onMessage(
          Message message) {
    if(message instanceof SendPartitionedEntrypoints) {
      HashMap<String, HashSet<String>> paths = parallelReconstructAkka(((SendPartitionedEntrypoints) message).scope, ((SendPartitionedEntrypoints) message).cha, ((SendPartitionedEntrypoints) message).entrypointList, ((SendPartitionedEntrypoints) message).monitor, getContext().getSelf());
      ((SendPartitionedEntrypoints) message).monitor.tell(new Monitor.UpdateAccessControlInfo(paths));
      return Behaviors.stopped();
    }
    return this;
  }

  public static void createEmptyFile(String fileName) {
    File file = new File(fileName);
    try {
      file.createNewFile();
//      System.out.println("File created successfully!");
    } catch (IOException e) {
//      System.out.println("An error occurred while creating the file: " + e.getMessage());
    }
  }

//  /**
//   * Begins processing entrypoints. The worker actor periodically sends an update message
//   * to the monitor actor, letting it know how many entrypoints it has completed.
//   * @param scope AnalysisScope.
//   * @param cha ClassHierarchy.
//   * @param entrypoints List of entrypoints to analyze.
//   * @param monitor Reference to the Monitor actor.
//   * @param self Reference to the current Worker actor.
//   * @return
//   */


  private static HashMap<String, HashSet<String>> parallelReconstructAkka(AnalysisScope scope, ClassHierarchy cha, ArrayList<DefaultEntrypoint> entrypoints, ActorRef<Monitor.Message> monitor, ActorRef<Message> self){
    int percentage = (int) Math.ceil(entrypoints.size() * 0.01);
    int count = 0;

    HashMap<String, HashSet<String>> ans = new HashMap<>();

    for (DefaultEntrypoint ep : entrypoints) {
      if(!scope.isApplicationLoader(ep.getMethod().getDeclaringClass().getClassLoader())) continue;
      try {

        String check = DataCollection.createEPString(ep);

        HashSet<String> paths = DataCollection.wrapperGetPaths(ep, new ArrayList<>());
        if(paths != null){
          if(paths.size() > 0){
            ans.put(check, paths);
          }
        }

      } catch (Exception e) {
        PrintUtils.print("AKKA Error!");
      } finally {
        count++;

        if(count % percentage == 0 || count == entrypoints.size()) {
          monitor.tell(new UpdateChildCount(self, count));
        }
      }
    }

//    monitor.tell(new UpdateChildCount(self, entrypoints.size()));
    return ans;

  }

  private static HashMap<DefaultEntrypoint, HashMap<String, ArrayList<HashMap<String, String>>>> detectAccessControlForFrameworkEntrypointsAkka(
      AnalysisScope scope, ClassHierarchy cha, ArrayList<DefaultEntrypoint> entrypoints,
      ActorRef<Monitor.Message> monitor,
      ActorRef<Message> self) {



    HashMap<DefaultEntrypoint, HashMap<String, ArrayList<HashMap<String, String>>>> accessControlMap = new HashMap<>();
    int percentage = (int) Math.ceil(entrypoints.size() * 0.01);
    int count = 0;
    for (DefaultEntrypoint ep : entrypoints) {
      if(!scope.isApplicationLoader(ep.getMethod().getDeclaringClass().getClassLoader())) continue;
      try {
//        Pair<HashMap<String, String>, Pair<Pair<String, String>, HashMap<String, ArrayList<HashMap<String, String>>>>> _temp = ResourceMapper_1.DFS(scope, cha, ep);
        String filename = FrameworkAnalyzer.fileName + "/accessControlEP_" + ep.getMethod().getName().toString();

        ResourceMapper_1.InvokeWrapper(scope, cha, ep);

        createEmptyFile(filename);
//                res.put(ep, _temp.snd);
//        if(_temp != null){
//          String filename = FrameworkAnalyzer.fileName + "/accessControlEP_" + ep.getMethod().getName().toString();
//          FrameworkAnalyzer.writeToFile2(filename, _temp.snd.snd, _temp.snd.fst, _temp.fst);
////          accessControlMap.put(ep, _temp.snd.snd);
//        }

      } catch (Exception e) {
        PrintUtils.print("Could not Write the method!");
        e.printStackTrace();
      } finally {
        count++;

        if(count % percentage == 0 || count == entrypoints.size()) {
          monitor.tell(new UpdateChildCount(self, count));
        }
      }
    }
    monitor.tell(new UpdateChildCount(self, entrypoints.size()));

    return accessControlMap;
  }
}