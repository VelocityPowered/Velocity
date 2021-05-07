/*
 * Copyright (C) 2018 Velocity Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.velocitypowered.proxy.command;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.RawCommand;
import com.velocitypowered.api.command.SimpleCommand;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class CommandTests extends CommandTestSuite {

  // The following tests don't depend on the Command implementation being used.
  // If adding more tests, try to mix it up by using the different implementations
  // provided by Velocity.

  // Execution

  @Test
  void testAliasIsCaseInsensitive() {
    final CommandMeta meta = manager.metaBuilder("hello").build();
    manager.register(meta, (SimpleCommand) invocation -> {
      assertEquals("hello", invocation.alias());
    });

    assertNotForwarded("Hello");
  }

  @Test
  void testUnknownAliasIsForwarded() {
    assertForwarded("");
    assertForwarded("foo");
  }

  @Test
  void testExecuteInputIsTrimmed() {
    final CommandMeta meta = manager.metaBuilder("hello").build();
    manager.register(meta, new RawCommand() {
      @Override
      public void execute(final Invocation invocation) {
        assertEquals("hello", invocation.alias());
        assertEquals("", invocation.arguments());
      }
    });

    assertNotForwarded(" hello");
    assertNotForwarded("  hello");
    assertNotForwarded("hello ");
    assertNotForwarded("hello   ");
  }

  @Test
  void testExecuteAsyncCompletesExceptionallyIfExecuteThrows() {
    final RuntimeException expected = new RuntimeException();
    final CommandMeta meta = manager.metaBuilder("hello").build();
    manager.register(meta, (SimpleCommand) invocation -> {
      throw expected;
    });

    try {
      manager.executeAsync(source, "hello").join();
    } catch (final CompletionException e) {
      assertSame(expected, e.getCause().getCause());
    }
  }

  @Test
  void testExecuteThrowsIfHasPermissionThrows() {
    final RuntimeException expected = new RuntimeException();
    final CommandMeta meta = manager.metaBuilder("hello").build();
    manager.register(meta, new RawCommand() {
      @Override
      public boolean hasPermission(final Invocation invocation) {
        throw expected;
      }
    });

    final Exception e = assertThrows(RuntimeException.class, () -> {
      manager.execute(source, "hello");
    });

    assertSame(expected, e.getCause());
  }

  @Test
  void testExecuteAfterPrimaryAliasIsUnregistered() {
    final AtomicInteger callCount = new AtomicInteger();

    final CommandMeta meta = manager.metaBuilder("foo").aliases("bar").build();
    manager.register(meta, (SimpleCommand) invocation -> {
      callCount.incrementAndGet();
    });
    manager.unregister("foo");

    assertNotForwarded("bar");
    assertEquals(1, callCount.get());
  }

  // Suggestions

  static class DummyCommand implements SimpleCommand {

    @Override
    public void execute(final Invocation invocation) {
      fail();
    }

    @Override
    public List<String> suggest(final Invocation invocation) {
      return fail();
    }
  }

  @Test
  void testAliasSuggestions() {
    manager.register(manager.metaBuilder("foo").build(), new DummyCommand());
    manager.register(manager.metaBuilder("bar").build(), new DummyCommand());
    manager.register(manager.metaBuilder("baz").build(), new DummyCommand());

    assertSuggestions("", "bar", "baz", "foo"); // in alphabetical order
  }

  @Test
  void testPartialAliasSuggestions() {
    manager.register(manager.metaBuilder("foo").build(), new DummyCommand());
    manager.register(manager.metaBuilder("bar").build(), new DummyCommand());

    assertSuggestions("f", "foo");
  }

  @Test
  void testNoSuggestionsIfFullAlias() {
    manager.register(manager.metaBuilder("hello").build(), new DummyCommand());

    assertSuggestions("hello");
  }

  @Test
  void testNoAliasSuggestionIfImpermissible() {
    final CommandMeta meta = manager.metaBuilder("hello").build();
    manager.register(meta, new RawCommand() {
      @Override
      public void execute(final Invocation invocation) {
        fail();
      }

      @Override
      public boolean hasPermission(final Invocation invocation) {
        assertEquals("hello", invocation.alias());
        assertEquals("", invocation.arguments());
        return false;
      }

      @Override
      public List<String> suggest(final Invocation invocation) {
        return fail();
      }
    });

    assertSuggestions("");
    assertSuggestions("hel");
  }

  @Test
  void testOfferSuggestionsCompletesExceptionallyIfSuggestThrows() {
    final RuntimeException expected = new RuntimeException();
    final CommandMeta meta = manager.metaBuilder("hello").build();
    manager.register(meta, new SimpleCommand() {
      @Override
      public void execute(final Invocation invocation) {
        fail();
      }

      @Override
      public List<String> suggest(final Invocation invocation) {
        throw expected;
      }
    });

    try {
      manager.offerSuggestions(source, "hello ").join();
      fail();
    } catch (final CompletionException e) {
      assertSame(expected, e.getCause());
    }
  }

  // (Secondary) aliases
  // The following tests check for inconsistencies between the primary alias node and
  // a secondary alias literal.

  @Test
  void testExecutedWithAlias() {
    final AtomicInteger callCount = new AtomicInteger();

    final CommandMeta meta = manager.metaBuilder("foo")
            .aliases("bar")
            .build();
    manager.register(meta, new RawCommand() {
      @Override
      public void execute(final Invocation invocation) {
        assertEquals("bar", invocation.alias());
        assertEquals("", invocation.arguments());
        callCount.incrementAndGet();
      }
    });

    assertNotForwarded("bar");
    assertEquals(1, callCount.get());
  }

  @Test
  void testExecutedWithAliasAndArguments() {
    final AtomicInteger callCount = new AtomicInteger();

    final CommandMeta meta = manager.metaBuilder("foo")
            .aliases("bar")
            .build();
    manager.register(meta, (SimpleCommand) invocation -> {
      assertEquals("bar", invocation.alias());
      assertArrayEquals(new String[] { "baz" }, invocation.arguments());
      callCount.incrementAndGet();
    });

    assertNotForwarded("bar baz");
    assertEquals(1, callCount.get());
  }

  @Test
  void testNotExecutedWithImpermissibleAlias() {
    final CommandMeta meta = manager.metaBuilder("foo")
            .aliases("bar")
            .build();
    manager.register(meta, new RawCommand() {
      @Override
      public void execute(final Invocation invocation) {
        fail();
      }

      @Override
      public boolean hasPermission(final Invocation invocation) {
        assertEquals("bar", invocation.alias());
        assertEquals("", invocation.arguments());
        return false;
      }
    });

    assertForwarded("bar");
  }

  @Test
  void testNotExecutedWithImpermissibleAliasAndArguments() {
    final CommandMeta meta = manager.metaBuilder("foo")
            .aliases("bar")
            .build();
    manager.register(meta, new SimpleCommand() {
      @Override
      public void execute(final Invocation invocation) {
        fail();
      }

      @Override
      public boolean hasPermission(final Invocation invocation) {
        assertEquals("bar", invocation.alias());
        assertArrayEquals(new String[] { "baz" }, invocation.arguments());
        return false;
      }
    });

    assertNotForwarded("bar baz");
  }

  @Test
  void testAllAliasesAreSuggested() {
    final CommandMeta meta = manager.metaBuilder("foo")
            .aliases("bar", "baz")
            .build();
    manager.register(meta, new DummyCommand());

    assertSuggestions("", "bar", "baz", "foo");
  }

  @Test
  void testOnlyPermissibleAliasesAreSuggested() {
    final CommandMeta meta = manager.metaBuilder("hello")
            .aliases("greetings", "howdy", "bonjour")
            .build();
    manager.register(meta, new RawCommand() {
      @Override
      public void execute(final Invocation invocation) {
        fail();
      }

      @Override
      public boolean hasPermission(final Invocation invocation) {
        return invocation.alias().equals("greetings") || invocation.alias().equals("howdy");
      }
    });

    assertSuggestions("", "greetings", "howdy");
  }

  @Test
  void testArgumentSuggestionsViaAlias() {
    final CommandMeta meta = manager.metaBuilder("foo")
            .aliases("bar")
            .build();
    manager.register(meta, new SimpleCommand() {
      @Override
      public void execute(final Invocation invocation) {
        fail();
      }

      @Override
      public List<String> suggest(final Invocation invocation) {
        return Collections.singletonList("baz");
      }
    });

    assertSuggestions("bar ", "baz");
  }

  // Hinting

  @Test
  void testExecuteViaHint() {
    final AtomicInteger callCount = new AtomicInteger();

    final CommandNode<CommandSource> hint = LiteralArgumentBuilder
            .<CommandSource>literal("people")
            .build();
    final CommandMeta meta = manager.metaBuilder("hello")
            .hint(hint)
            .build();
    manager.register(meta, new RawCommand() {
      @Override
      public void execute(final Invocation invocation) {
        assertEquals("world", invocation.arguments());
        callCount.incrementAndGet();
      }
    });

    assertNotForwarded("hello world");
    assertEquals(1, callCount.get());
  }

  @Test
  void testExecuteViaLiteralChildOfHint() {
    final AtomicInteger callCount = new AtomicInteger();

    final CommandNode<CommandSource> hint = LiteralArgumentBuilder
            .<CommandSource>literal("bar")
            .then(LiteralArgumentBuilder.literal("baz"))
            .build();
    final CommandMeta meta = manager.metaBuilder("foo")
            .hint(hint)
            .build();
    manager.register(meta, (SimpleCommand) invocation -> {
      assertArrayEquals(new String[] { "bar", "baz" }, invocation.arguments());
      callCount.incrementAndGet();
    });

    assertNotForwarded("foo bar baz");
    assertEquals(1, callCount.get());
  }

  @Test
  void testExecuteViaArgumentChildOfHint() {
    // Hints should be wrapped recursively
    final AtomicInteger callCount = new AtomicInteger();
    final CommandNode<CommandSource> hint = LiteralArgumentBuilder
            .<CommandSource>literal("bar")
            .then(LiteralArgumentBuilder
                    .<CommandSource>literal("baz")
                    .then(RequiredArgumentBuilder
                            .argument("number", IntegerArgumentType.integer())))
            .build();
    final CommandMeta meta = manager.metaBuilder("foo")
            .hint(hint)
            .build();
    manager.register(meta, new RawCommand() {
      @Override
      public void execute(final Invocation invocation) {
        assertEquals("bar baz 123", invocation.arguments());
        callCount.incrementAndGet();
      }
    });

    assertNotForwarded("foo bar baz 123");
    assertEquals(1, callCount.get());
  }

  @Test
  void testNotExecutedViaHintWithImpermissibleArguments() {
    final AtomicInteger callCount = new AtomicInteger();

    final CommandNode<CommandSource> hint = LiteralArgumentBuilder
            .<CommandSource>literal("bar")
            .build();
    final CommandMeta meta = manager.metaBuilder("foo")
            .hint(hint)
            .build();
    manager.register(meta, new RawCommand() {
      @Override
      public void execute(final Invocation invocation) {
        fail();
      }

      @Override
      public boolean hasPermission(final Invocation invocation) {
        assertEquals("bar", invocation.arguments());
        callCount.incrementAndGet();
        return false;
      }
    });

    assertNotForwarded("foo bar");
    assertEquals(1, callCount.get());
  }

  @Test
  void testSuggestionsAreMergedWithHints() {
    final CommandNode<CommandSource> createHint = LiteralArgumentBuilder
            .<CommandSource>literal("deposit")
            .build();
    final CommandNode<CommandSource> removeHint = LiteralArgumentBuilder
            .<CommandSource>literal("withdraw")
            .build();
    final CommandMeta meta = manager.metaBuilder("bank")
            .hint(createHint)
            .hint(removeHint)
            .build();
    manager.register(meta, new SimpleCommand() {
      @Override
      public void execute(final Invocation invocation) {
        fail();
      }

      @Override
      public List<String> suggest(final Invocation invocation) {
        return ImmutableList.of("balance", "interest");
      }
    });

    assertSuggestions("bank ",
            "balance", "deposit", "interest", "withdraw");
  }

  @Test
  void testNoHintSuggestionsIfImpermissible_empty() {
    final AtomicInteger callCount = new AtomicInteger();

    final CommandNode<CommandSource> hint = LiteralArgumentBuilder
            .<CommandSource>literal("bar")
            .build();
    final CommandMeta meta = manager.metaBuilder("foo")
            .hint(hint)
            .build();
    manager.register(meta, new RawCommand() {
      @Override
      public void execute(final Invocation invocation) {
        fail();
      }

      @Override
      public boolean hasPermission(final Invocation invocation) {
        assertEquals("", invocation.arguments());
        callCount.incrementAndGet();
        return false;
      }
    });

    assertSuggestions("foo ");
    assertEquals(1, callCount.get());
  }

  @Test
  void testNoHintSuggestionsIfImpermissible_partialLiteral() {
    final AtomicInteger callCount = new AtomicInteger();

    final CommandNode<CommandSource> hint = LiteralArgumentBuilder
            .<CommandSource>literal("bar")
            .build();
    final CommandMeta meta = manager.metaBuilder("foo")
            .hint(hint)
            .build();
    manager.register(meta, new SimpleCommand() {
      @Override
      public void execute(final Invocation invocation) {
        fail();
      }

      @Override
      public boolean hasPermission(final Invocation invocation) {
        assertArrayEquals(new String[] { "ba" }, invocation.arguments());
        callCount.getAndIncrement();
        return false;
      }
    });

    assertSuggestions("foo ba");
    assertEquals(1, callCount.get());
  }

  @Test
  void testNoHintSuggestionsIfImpermissible_parsedHint() {
    final AtomicInteger callCount = new AtomicInteger();

    final CommandNode<CommandSource> hint = LiteralArgumentBuilder
            .<CommandSource>literal("bar")
            .build();
    final CommandMeta meta = manager.metaBuilder("foo")
            .hint(hint)
            .build();
    manager.register(meta, new SimpleCommand() {
      @Override
      public void execute(final Invocation invocation) {
        fail();
      }

      @Override
      public boolean hasPermission(final Invocation invocation) {
        assertArrayEquals(new String[] { "bar" }, invocation.arguments());
        callCount.getAndIncrement();
        return false;
      }
    });

    assertSuggestions("foo bar");
    assertEquals(1, callCount.get());
  }

  @Test
  void testHintSuggestionViaAlias() {
    final CommandNode<CommandSource> hint = LiteralArgumentBuilder
            .<CommandSource>literal("world")
            .build();
    final CommandMeta meta = manager.metaBuilder("greetings")
            .aliases("hello")
            .hint(hint)
            .build();
    manager.register(meta, new RawCommand() {
      @Override
      public void execute(final Invocation invocation) {
        fail();
      }
    });

    assertSuggestions("hello ", "world");
  }

  @Test
  void testLiteralChildOfHintSuggestionViaAlias() {
    final CommandNode<CommandSource> hint = LiteralArgumentBuilder
            .<CommandSource>literal("bar")
            .then(LiteralArgumentBuilder.literal("baz"))
            .build();
    final CommandMeta meta = manager.metaBuilder("alias")
            .aliases("foo")
            .hint(hint)
            .build();
    manager.register(meta, (SimpleCommand) invocation -> {
      fail();
    });

    assertSuggestions("foo bar ", "baz");
  }

  @Test
  void testArgumentChildOfHintSuggestionViaAlias() {
    final CommandNode<CommandSource> hint = LiteralArgumentBuilder
            .<CommandSource>literal("bar")
            .then(LiteralArgumentBuilder
                    .<CommandSource>literal("baz")
                    .then(RequiredArgumentBuilder
                            .<CommandSource, String>argument("word", StringArgumentType.word())
                            .suggests((context, builder) -> {
                              builder.suggest("qux");
                              return builder.buildFuture();
                            })
                    )
            )
            .build();
    final CommandMeta meta = manager.metaBuilder("alias")
            .aliases("foo")
            .hint(hint)
            .build();
    manager.register(meta, new RawCommand() {
      @Override
      public void execute(final Invocation invocation) {
        fail();
      }
    });

    assertSuggestions("foo bar baz ", "qux");
  }
}