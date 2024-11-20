package com.aacr.helpers.detectors.framework.akka;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.pattern.StatusReply;
import com.aacr.wala.workshop.analyzers.DataCollection;
import com.aacr.wala.workshop.utils.PropertyUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * This class defines the Monitor Akka Actor, which tracks the status of the access control detection.
 */
public class Monitor extends AbstractBehavior<Monitor.Message>{

  public interface Message {}
  interface Reply {}


  private static int entrypoints;
  private static HashMap<ActorRef<Worker.Message>, Integer> childRefsCount;
  private static HashMap<String, HashSet<String>> accessControlList = new HashMap<>();

  /**
   * This class defines a message that can be sent to the Monitor actor specifying the total number of entrypoints.
   */
  public static final class UpdateJobInfo implements Message {
    public final int entrypoints;

    /**
     * UpdateJobInfo constructor.
     * @param entrypoints Number of entrypoints.
     */
    public UpdateJobInfo(int entrypoints) {
      this.entrypoints = entrypoints;
    }
  }

  /**
   * This class defines a message that can be sent to the Monitor actor specifying
   * the existing worker actors.
   */
  public static final class UpdateChildInfo implements Message {
    public final HashMap<ActorRef<Worker.Message>, Integer> childRefsCount;

    /**
     * This method takes in a list of references to worker actors and populates the hashmap with an initial
     * value of 0, representing how many entrypoints each actor has completed.
     * @param childRefs List of References to worker actors
     */
    public UpdateChildInfo(ArrayList<ActorRef<Worker.Message>> childRefs) {
      this.childRefsCount = new HashMap<>();
      for(ActorRef<Worker.Message> childRef : childRefs) {
        this.childRefsCount.put(childRef, 0);
      }
    }
  }

  /**
   * This class defines a message that can be sent to the Monitor actor specifying
   * a worker actor's final output.
   */
  public static final class UpdateAccessControlInfo implements Message {
    public final HashMap<String, HashSet<String>> accessControlInfo;

    /**
     * Takes in an access control map corresponding to the partitioned list of entrypoints analyzed by
     * one of the worker actors.
     * @param accessControlInfo One worker actor's final outputted access control map.
     */
    public UpdateAccessControlInfo(HashMap<String, HashSet<String>> accessControlInfo) {
      this.accessControlInfo = accessControlInfo;
    }
  }

  /**
   * This class defines a message that can be sent to the Monitor actor specifying
   * an actor to respond to. The Monitor will send back status information upon receiving this message.
   */
  public static final class GetFineGrainedStatus implements Message {
    public final ActorRef<FineGrainedStatusInformation> replyTo;

    /**
     * GetFineGrainedStatus constructor.
     * @param replyTo Actor to which the response should be sent.
     */
    public GetFineGrainedStatus(ActorRef<FineGrainedStatusInformation> replyTo) {
      this.replyTo = replyTo;
    }
  }

  /**
   * This class defines a message that is sent to the Monitor actor when another actor
   * would like a status update on the set-up process.
   */
  public static final class CheckSetUp implements Message {
    public final ActorRef<StatusReply> replyTo;

    /**
     * CheckSetUp constructor.
     * @param replyTo Actor to which the response should be sent.
     */
    public CheckSetUp(ActorRef<StatusReply> replyTo) {
      this.replyTo = replyTo;
    }
  }

  /**
   * This class defines a message that is sent to the Monitor actor when another actor
   * would like to receive the final access control information gathered by all the worker actors.
   */
  public static final class GetCompleteAccessControlInformation implements Message {
    public final ActorRef<CompleteAccessControlInformation> replyTo;

    /**
     * GetCompleteAccessControlInformation constructor.
     * @param replyTo Actor to which the response should be sent.
     */
    public GetCompleteAccessControlInformation(ActorRef<CompleteAccessControlInformation> replyTo) {
      this.replyTo = replyTo;
    }
  }

  /**
   * This class defines a message that is sent to the Monitor actor when another actor
   * would like to update the entrypoint count for a particular worker actor.
   */
  public static final class UpdateChildCount implements Message {
    public final ActorRef<Worker.Message> childRef;
    public final int count;

    /**
     * This method takes in a reference to a worker actor and an integer representing its current
     * completed entrypoints. It then and populates the hashmap with the updated information.
     * @param childRef References to worker actor.
     * @param count Number of entrypoints analyzed by the worker actor.
     */
    public UpdateChildCount(ActorRef<Worker.Message> childRef, int count) {
      this.childRef = childRef;
      this.count = count;
    }
  }

  /**
   * This class defines a message that the Monitor actor sends as a status update.
   */
  public static final class FineGrainedStatusInformation implements Monitor.Reply {
    public final int completedEntrypoints;

    /**
     * FineGrainedStatusInformation constructor.
     * @param completedEntrypoints Number of completed entrypoints.
     */
    public FineGrainedStatusInformation(int completedEntrypoints) {
      this.completedEntrypoints = completedEntrypoints;
    }
  }

  /**
   * This class defines a message that the Monitor actor sends to provide the complete
   * access control information gathered by all the worker actors.
   */
  public static final class CompleteAccessControlInformation implements Monitor.Reply {
    public final HashMap<String, HashSet<String>> accessControlMap;

    /**
     * CompleteAccessControlInformation constructor.
     * @param accessControlMap Completed access control map.
     */
    public CompleteAccessControlInformation(HashMap<String, HashSet<String>> accessControlMap) throws IOException {
      try{
        DataCollection.appendKeysToFile(accessControlMap, "alreadyAnalysed.txt");
      }catch (Exception e){
        // oops
      }

      String folder = PropertyUtils.getOut();
      DataCollection.writeDataToFile(accessControlMap, folder + PropertyUtils.getName() + ".txt");
      this.accessControlMap = accessControlMap;
    }
  }

  /**
   * Monitor constructor.
   * @param context Provides access to the actor's identity and the ActorSystem.
   */
  public Monitor(ActorContext<Message> context) {
    super(context);
  }

//  /share/smabbasz/decompiled_roms/samsung/13/SM-T636B/T636BXXU1BWB2_T636BOJM1BWB2_ILO/framework

//  
  /**
   * Sets up the monitor.
   * @return A Behavior.
   */
  public static Behavior<Message> create() {
    return Behaviors.setup(Monitor::new);
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
   * Handles different types of incoming messages. Usually sends back some sort of status information
   * to the requesting actor.
   * @param message Message.
   * @return Behavior.
   */
  private Behavior<Message> onMessage(
      Message message) throws ExecutionException, InterruptedException, IOException {

    if (message instanceof UpdateJobInfo) {
      Monitor.entrypoints = ((UpdateJobInfo) message).entrypoints;
    } else if(message instanceof UpdateChildInfo) {
      Monitor.childRefsCount = ((UpdateChildInfo) message).childRefsCount;
    } else if (message instanceof UpdateAccessControlInfo) {
      Monitor.accessControlList.putAll(
              (Map<? extends String, ? extends HashSet<String>>) ((UpdateAccessControlInfo) message).accessControlInfo);
    } else if (message instanceof GetCompleteAccessControlInformation) {
      ((GetCompleteAccessControlInformation) message).replyTo.tell(
          new CompleteAccessControlInformation(Monitor.accessControlList));
    } else if(message instanceof GetFineGrainedStatus) {
      int completedCount = 0;
      for(HashMap.Entry<ActorRef<Worker.Message>, Integer> childEntry : childRefsCount.entrySet()) {
        completedCount += childEntry.getValue();
      }
      ((GetFineGrainedStatus) message).replyTo.tell(new FineGrainedStatusInformation(completedCount));
    } else if(message instanceof CheckSetUp) {
      if(Monitor.childRefsCount != null) {
        ((CheckSetUp) message).replyTo.tell(StatusReply.success("Set-up completed!"));
      } else {
        ((CheckSetUp) message).replyTo.tell(StatusReply.error("Incomplete Set-Up."));
      }
    } else if(message instanceof UpdateChildCount) {
      if(this.childRefsCount != null) {
        this.childRefsCount.put(((UpdateChildCount) message).childRef, ((UpdateChildCount) message).count);
      }
    }
    return this;
  }
}
