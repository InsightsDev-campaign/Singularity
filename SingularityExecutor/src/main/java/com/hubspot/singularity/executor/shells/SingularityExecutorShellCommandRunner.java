package com.hubspot.singularity.executor.shells;

import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.hubspot.singularity.SingularityTaskShellCommandRequest;
import com.hubspot.singularity.SingularityTaskShellCommandUpdate.UpdateType;
import com.hubspot.singularity.executor.config.SingularityExecutorConfiguration;
import com.hubspot.singularity.executor.task.SingularityExecutorTask;

public class SingularityExecutorShellCommandRunner {

  private final SingularityTaskShellCommandRequest shellRequest;
  private final SingularityExecutorTask task;
  private final ListeningExecutorService shellCommandExecutorService;
  private final SingularityExecutorShellCommandUpdater shellCommandUpdater;
  private final SingularityExecutorConfiguration executorConfiguration;

  @SuppressWarnings("serial")
  private static class InvalidShellCommandException extends RuntimeException {

    public InvalidShellCommandException(String message) {
      super(message);
    }

  }

  public SingularityExecutorShellCommandRunner(SingularityTaskShellCommandRequest shellRequest, SingularityExecutorConfiguration executorConfiguration, SingularityExecutorTask task,
      ListeningExecutorService shellCommandExecutorService, SingularityExecutorShellCommandUpdater shellCommandUpdater) {
    this.shellRequest = shellRequest;
    this.executorConfiguration = executorConfiguration;
    this.task = task;
    this.shellCommandUpdater = shellCommandUpdater;
    this.shellCommandExecutorService = shellCommandExecutorService;
  }

  public SingularityTaskShellCommandRequest getShellRequest() {
    return shellRequest;
  }

  public SingularityExecutorTask getTask() {
    return task;
  }

  public ProcessBuilder buildProcessBuilder(List<String> command) {
    return new ProcessBuilder(command);
  }

  public void start() {
    List<String> command = null;

    try {
      command = buildCommand();
    } catch (InvalidShellCommandException isce) {
      shellCommandUpdater.sendUpdate(UpdateType.INVALID, Optional.of(isce.getMessage()));
      return;
    }

    shellCommandUpdater.sendUpdate(UpdateType.ACKED, Optional.of(Joiner.on(" ").join(command)));

    SingularityExecutorShellCommandRunnerCallable callable = new SingularityExecutorShellCommandRunnerCallable(task.getLog(), shellCommandUpdater, buildProcessBuilder(command));

    ListenableFuture<Integer> shellFuture = shellCommandExecutorService.submit(callable);

    Futures.addCallback(shellFuture, new FutureCallback<Integer>() {

      @Override
      public void onSuccess(Integer result) {
        task.getLog().info("ShellRequest {} finished with {}", shellRequest, result);

        shellCommandUpdater.sendUpdate(UpdateType.FINISHED, Optional.of(String.format("Finished with code %s", result)));
      }

      @Override
      public void onFailure(Throwable t) {
        task.getLog().warn("ShellRequest {} failed", shellRequest, t);

        shellCommandUpdater.sendUpdate(UpdateType.FAILED, Optional.of(String.format("Failed - %s (%s)", t.getClass().getSimpleName(), t.getMessage())));
      }

    });
  }

  private List<String> buildCommand() {
    Optional<SingularityExecutorShellCommandDescriptor> matchingShellCommandDescriptor = Iterables.tryFind(executorConfiguration.getShellCommands(), new Predicate<SingularityExecutorShellCommandDescriptor>() {

      @Override
      public boolean apply(SingularityExecutorShellCommandDescriptor input) {
        return input.getName().equals(shellRequest.getShellCommand().getName());
      }

    });

    if (!matchingShellCommandDescriptor.isPresent()) {
      throw new InvalidShellCommandException(String.format("%s not found in matching commands %s", shellRequest.getShellCommand().getName(), executorConfiguration.getShellCommands()));
    }

    final SingularityExecutorShellCommandDescriptor shellCommandDescriptor = matchingShellCommandDescriptor.get();

    List<String> command = new ArrayList<>(shellCommandDescriptor.getCommand());

    for (SingularityExecutorShellCommandOptionDescriptor option : shellCommandDescriptor.getOptions()) {
      if (shellRequest.getShellCommand().getOptions().contains(option.getName())) {
        command.add(option.getFlag());
      }
    }

    return command;
  }


}
