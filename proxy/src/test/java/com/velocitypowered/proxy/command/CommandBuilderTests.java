package com.velocitypowered.proxy.command;

public class CommandBuilderTests {

  // TODO Move execution tests to CommandManagerTests

  /*@Test
  void testBrigadierNoAliases() throws CommandSyntaxException {
    VelocityCommandManager manager = createManager();
    BrigadierCommand.Builder builder = manager.brigadierBuilder();

    LiteralCommandNode<CommandSource> node = BrigadierCommand
            .argumentBuilder("foo")
            .executes(context -> 36)
            .build();

    BrigadierCommand command = builder.register(node);
    assertNotNull(command);
    assertTrue(manager.getAllRegisteredCommands().contains("foo"));

    ParseResults<CommandSource> parse = manager.getBrigadierDispatcher().parse("foo", null);
    assertFalse(parse.getReader().canRead(), "Node is added to Brigadier dispatcher");
    assertEquals(36, manager.getBrigadierDispatcher().execute(parse));
  }

  @Test
  void testBrigadierAliases() throws CommandSyntaxException {
    VelocityCommandManager manager = createManager();
    BrigadierCommand.Builder builder = manager.brigadierBuilder();

    LiteralCommandNode<CommandSource> node = BrigadierCommand
            .argumentBuilder("foo")
            .executes(context -> 27)
            .build();

    builder.aliases("bar", "baz").register(node);
    assertTrue(manager.getAllRegisteredCommands().containsAll(
            ImmutableList.of("foo", "bar", "baz")));

    ParseResults<CommandSource> parse = manager.getBrigadierDispatcher().parse("bar", null);
    assertFalse(parse.getReader().canRead(), "Alias node is added to Brigadier dispatcher");
    assertEquals(27, manager.getBrigadierDispatcher().execute(parse),
            "Redirect executes command");
  }*/
}
