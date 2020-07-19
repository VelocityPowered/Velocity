package com.velocitypowered.api.command;

/**
 * A specialized sub-interface of {@code Command} which indicates that the proxy should pass
 * the command and its arguments directly without further processing.
 * This is useful for bolting on external command frameworks to Velocity.
 */
public interface RawCommand extends InvocableCommand<RawCommand.Invocation> {

  /**
   * Contains the invocation data for a raw command.
   */
  interface Invocation extends CommandInvocation<String> {

  }
}
