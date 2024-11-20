package com.aacr.helpers.detectors.framework;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.AskPattern;
import akka.pattern.StatusReply;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.aacr.helpers.detectors.framework.akka.Manager;
import com.aacr.helpers.detectors.framework.akka.Manager.MonitorInformation;
import com.aacr.helpers.detectors.framework.akka.Manager.SendEntrypoints;
import com.aacr.helpers.detectors.framework.akka.Monitor;
import com.aacr.helpers.detectors.framework.akka.Monitor.*;
import com.aacr.helpers.utils.PrintUtils;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;


/**
 * This class provides parallel framework access control detection functionality and relies on Akka actors for parallelization.
 * Note that an entrypoint may be associated with duplicate access control checks. This occurs when the same type of access control enforcement
 * is performed multiple times. We keep a record of each occurrence, as this is useful when performing
 * a more fine-grained access control enforcement at the sink-level.
 */
public class ParallelFrameworkAccessControlDetector {

  /**
   * This method sets-up the analysis, waits for result availability, and returns the completed access control map.
   * @param scope AnalysisScope.
   * @param cha ClassHierarchy.
   * @param frameworkEntrypoints HashSet of DefaultEntrypoints, each one a framework entrypoint.
   * @return HashMap linking methods to their access control enforcement.
   * @throws Exception
   */
  public static HashMap<String, HashSet<String>> detectFrameworkAccessControlParallel(AnalysisScope scope, ClassHierarchy cha, HashSet<DefaultEntrypoint> frameworkEntrypoints) throws Exception {
    final ActorSystem<Manager.Message> system = ActorSystem.create(
        Manager.create(), "accessControlDetector");
    ActorRef<Message> monitorRef = getMonitor(scope, cha, frameworkEntrypoints, system);
    waitForSetUp(system, monitorRef);
    monitor(system, monitorRef, frameworkEntrypoints);

    CompletionStage<Monitor.CompleteAccessControlInformation> accessControlResult =
        AskPattern.ask(
            monitorRef,
            replyTo -> new GetCompleteAccessControlInformation(replyTo),
            Duration.ofHours(2),
            system.scheduler()
        );

    system.terminate();
    return accessControlResult.toCompletableFuture().get().accessControlMap;
  }

  public static HashMap<String, HashSet<String>> parallelReconstruct(AnalysisScope scope, ClassHierarchy cha, HashSet<DefaultEntrypoint> frameworkEntrypoints) throws Exception {

    if(frameworkEntrypoints.size() > 0){
      final ActorSystem<Manager.Message> system = ActorSystem.create(
              Manager.create(), "parallelReconstruct");

      ActorRef<Message> monitorRef = getMonitor(scope, cha, frameworkEntrypoints, system);
      waitForSetUp(system, monitorRef);
      monitor(system, monitorRef, frameworkEntrypoints);

    CompletionStage<Monitor.CompleteAccessControlInformation> accessControlResult =
            AskPattern.ask(
                    monitorRef,
                    replyTo -> new GetCompleteAccessControlInformation(replyTo),
                    Duration.ofHours(2),
                    system.scheduler()
            );

      system.terminate();
    return accessControlResult.toCompletableFuture().get().accessControlMap;
    }else{
      PrintUtils.print("Not Enough EntryPoints");
    }

    return null;
  }




  /**
   * This method returns a reference to the MonitorActor, which will give us real-time information on the status of the analysis.
   * @param scope AnalysisScope.
   * @param cha ClassHierarchy.
   * @param frameworkEntrypoints HashSet of DefaultEntrypoints, each one a framework entrypoint.
   * @param system ActorSystem.
   * @return Reference to the Monitor Actor.
   * @throws ExecutionException
   * @throws InterruptedException
   */
  private static ActorRef<Message> getMonitor(AnalysisScope scope, ClassHierarchy cha, HashSet<DefaultEntrypoint> frameworkEntrypoints, ActorSystem<Manager.Message> system)
      throws ExecutionException, InterruptedException {
    CompletionStage<MonitorInformation> monitorInfo =
        AskPattern.ask(
            system,
            replyTo -> new SendEntrypoints(scope, cha, frameworkEntrypoints.size(),
                FrameworkAccessControlDetector.partitionEntrypoints(frameworkEntrypoints), replyTo),
            Duration.ofHours(2),
            system.scheduler()
        );
    return monitorInfo.toCompletableFuture().get().monitor;
  }

  /**
   * Waits for set-up to complete.
   * @param system ActorSystem.
   * @param monitorRef Reference to Monitor Actor.
   * @throws ExecutionException
   * @throws InterruptedException
   */
  private static void waitForSetUp(ActorSystem<Manager.Message> system, ActorRef<Message> monitorRef) throws ExecutionException, InterruptedException {
    boolean setUpComplete = false;
    while (!setUpComplete) {
      CompletionStage<StatusReply> setUpStatus =
          AskPattern.ask(
              monitorRef,
              replyTo -> new CheckSetUp(replyTo),
              Duration.ofHours(2),
              system.scheduler()
          );
      StatusReply status = setUpStatus.toCompletableFuture().get();
      setUpComplete = status.isSuccess();
    }
  }

  /**
   * Monitors status of the access control detection and updates progress bar.
   * @param system ActorSystem.
   * @param monitorRef Reference to Monitor Actor.
   * @param frameworkEntrypoints HashSet of DefaultEntrypoints, each one a framework entrypoint.
   * @throws ExecutionException
   * @throws InterruptedException
   */
  private static void monitor(ActorSystem<Manager.Message> system, ActorRef<Message> monitorRef, HashSet<DefaultEntrypoint> frameworkEntrypoints)
      throws ExecutionException, InterruptedException {
    int completedEntrypoints = 0;
    int totalEntrypoints = frameworkEntrypoints.size();

    ProgressBarBuilder pbb = new ProgressBarBuilder()
        .setInitialMax(frameworkEntrypoints.size())
        .setStyle(ProgressBarStyle.UNICODE_BLOCK)
        .setTaskName("Access Control Detection")
        .setUnit(" entrypoints", 1)
        .setMaxRenderedLength(100)
        .setUpdateIntervalMillis(1000);
    ProgressBar pb = pbb.build();
    pb.step();

    while (completedEntrypoints < totalEntrypoints) {
      CompletionStage<Monitor.FineGrainedStatusInformation> statusReply =
          AskPattern.ask(
              monitorRef,
              replyTo -> new GetFineGrainedStatus(replyTo),
              Duration.ofHours(2),
              system.scheduler());

      FineGrainedStatusInformation statusInformation = statusReply.toCompletableFuture().get();
      completedEntrypoints = statusInformation.completedEntrypoints;
      pb.stepTo(completedEntrypoints);
      Thread.sleep(2000);
    }
  }
}