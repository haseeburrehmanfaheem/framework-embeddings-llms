package com.aacr.helpers.detectors.framework.akka;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.*;

import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.aacr.helpers.detectors.framework.akka.Monitor.UpdateChildInfo;
import com.aacr.helpers.detectors.framework.akka.Monitor.UpdateJobInfo;
import java.util.ArrayList;

/**
 * This class defines the Manager Akka Actor, which delegates access control detection work to
 * worker actors.
 */
public class Manager extends AbstractBehavior<Manager.Message> {

  public interface Message {};
  interface Reply {};

  /**
   * This class defines a message that can be sent to the Manager actor specifying the framework
   * entrypoints.
   */
  public static final class SendEntrypoints implements Message {

    public final AnalysisScope scope;
    public final ClassHierarchy cha;
    public final int totalEntrypoints;
    public final ArrayList<ArrayList<DefaultEntrypoint>> entrypointLists;
    public final ActorRef<MonitorInformation> replyTo;

    /**
     * SendEntrypoints Constructor.
     *
     * @param scope            AnalysisScope.
     * @param cha              ClassHierarchy.
     * @param totalEntrypoints Number of total entrypoints to analyze.
     * @param entrypointLists  Partitioned entrypoints (in the form of lists of lists).
     * @param replyTo          Reference specifying to where a response should be sent.
     */
    public SendEntrypoints(AnalysisScope scope, ClassHierarchy cha, int totalEntrypoints,
        ArrayList<ArrayList<DefaultEntrypoint>> entrypointLists,
        ActorRef<MonitorInformation> replyTo) {
      this.scope = scope;
      this.cha = cha;
      this.totalEntrypoints = totalEntrypoints;
      this.entrypointLists = entrypointLists;
      this.replyTo = replyTo;
    }
  }

  /**
   * This class defines a reply that can the Manager can send. It contains information about the
   * Monitor actor, which the Manager creates.
   */
  public static final class MonitorInformation implements Manager.Reply {

    public final ActorRef<Monitor.Message> monitor;

    /**
     * MonitorInformation constructor.
     * @param monitor Reference to monitor actor.
     */
    public MonitorInformation(ActorRef<Monitor.Message> monitor) {
      this.monitor = monitor;
    }
  }

  /**
   * Manager constructor.
   * @param context Provides access to the actor's identity and the ActorSystem.
   */
  public Manager(ActorContext<Message> context) {
    super(context);
  }

  /**
   * Sets up the manager.
   * @return A Behavior.
   */
  public static Behavior<Message> create() {
    return Behaviors.supervise(Behaviors.setup(Manager::new))
        .onFailure(Exception.class, SupervisorStrategy.resume());
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
   * Handles different types of incoming messages. If the message is a SendEntrypoint,  then as many
   * children as subLists in the message are created. The Manager sends information about the
   * workload to the Monitor, and then sends workload information to the child actors. Finally, it
   * gives the Monitor information about the child actors.
   *
   * @param message Message.
   * @return Behavior.
   */
  private Behavior<Message> onMessage(
      Message message) {

    if (message instanceof Manager.SendEntrypoints) {
      ArrayList<ActorRef<Worker.Message>> childrenList = new ArrayList<>();
      ActorRef<Monitor.Message> monitorRef =
          getContext().spawn(Monitor.create(), "monitor");
      monitorRef.tell(new UpdateJobInfo(((SendEntrypoints) message).totalEntrypoints));

      int counter = 0;
      for (ArrayList<DefaultEntrypoint> entrypointList : ((SendEntrypoints) message).entrypointLists) {
        String childName = "child" + counter;
        counter++;
        ActorRef<Worker.Message> childActorRef =
            getContext().spawn(Worker.create(), childName);
        childrenList.add(childActorRef);
        childActorRef.tell(
            new Worker.SendPartitionedEntrypoints(((SendEntrypoints) message).scope,
                ((SendEntrypoints) message).cha, entrypointList, monitorRef));
        ((SendEntrypoints) message).replyTo.tell(new MonitorInformation(monitorRef));
      }
      monitorRef.tell(new UpdateChildInfo(childrenList));
    }
    return this;
  }
}