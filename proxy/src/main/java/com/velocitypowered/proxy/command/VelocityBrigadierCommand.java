package com.velocitypowered.proxy.command;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.tree.CommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.BrigadierCommandInvocation;
import com.velocitypowered.api.command.CommandSource;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

final class VelocityBrigadierCommand implements BrigadierCommand {

  private final VelocityCommandManager manager;
  //private final CommandNode<CommandSource> node;

  private VelocityBrigadierCommand(final VelocityCommandManager manager/*, final CommandNode<CommandSource> node*/) {
    this.manager = manager;
    //this.node = node;
  }

  @Override
  public void execute(final BrigadierCommandInvocation invocation) {
    try {
      manager.getDispatcher().execute(invocation.parsed());
    } catch (final CommandSyntaxException e) {
      throw new RuntimeException("Valid parse results threw syntax exception", e);
    }
  }

  @Override
  public CompletableFuture<List<String>> suggestAsync(final BrigadierCommandInvocation invocation) {
    return manager.getDispatcher().getCompletionSuggestions(invocation.parsed())
            // The client infers the position of the suggestions
            .thenApply(suggestions -> Lists.transform(suggestions.getList(), Suggestion::getText));
  }

  @Override
  public boolean hasPermission(final BrigadierCommandInvocation invocation) {
    for (ParsedCommandNode<CommandSource> node : invocation.parsed().getContext().getNodes()) {
      if (!node.getNode().canUse(invocation.source())) {
        return false;
      }
    }

    return true;
  }

  final static class Builder extends AbstractCommandBuilder<BrigadierCommand, BrigadierCommand.Builder>
          implements BrigadierCommand.Builder {

    Builder(final VelocityCommandManager manager) {
      super(manager);
    }

    @Override
    public BrigadierCommand register(final LiteralArgumentBuilder<CommandSource> builder) {
      Preconditions.checkNotNull(builder, "builder");
      return register(builder.build());
    }

    @Override
    public BrigadierCommand register(final CommandNode<CommandSource> node) {
      Preconditions.checkNotNull(node, "node");
      final BrigadierCommand command = new VelocityBrigadierCommand(manager);
      final String alias = node.getName().toLowerCase(Locale.ENGLISH);
      manager.register(alias, command);

      aliases.remove(alias); // prevent self-redirect
      for (final String alias1 : aliases) {
        manager.getDispatcher().register(BrigadierCommand.argumentBuilder(alias1).redirect(node));
      }

      return command;
    }

    @Override
    protected BrigadierCommand.Builder self() {
      return this;
    }
  }
}
