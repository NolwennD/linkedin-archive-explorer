package fr.craft.linkedinarchiveexplorer.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AppTest {

  private static String[] argumentsOf(String line) {
    return line.isBlank() ? new String[0] : line.split(" ");
  }

  private static List<String> expected(String line) {
    return line.isBlank() ? List.of() : List.of(line.split(" "));
  }

  @Nested
  class Routing {

    @ParameterizedTest(name = "[{0}] → {1} [{2}]")
    @CsvSource({
      "'',                       SERVE, ''",
      "'serve',                  SERVE, ''",
      "'serve --port 9000',      SERVE, '--port 9000'",
      "'serve --no-browser',     SERVE, '--no-browser'",
      "'kotlin',                 CLI,   'kotlin'",
      "'-i kotlin',              CLI,   '-i kotlin'",
      "'--archive x.zip kotlin', CLI,   '--archive x.zip kotlin'",
    })
    void sendsNothingAndServeToTheWebAndEverythingElseToTheTerminal(
        String line, App.Target target, String passed) {
      App.Route route = App.route(argumentsOf(line));

      assertEquals(target, route.target());
      assertEquals(expected(passed), route.arguments());
    }

    @Test
    void doesNotTryToGuessThatAServeOptionMeantTheServer() {
      // The rule is deliberately blunt: only "serve" first, or nothing at all, opens the
      // page. Reading the other arguments would mean duplicating both UIs' grammars here.
      App.Route route = App.route(new String[] {"--no-browser"});

      assertEquals(App.Target.CLI, route.target());
      assertEquals(List.of("--no-browser"), route.arguments());
    }

    @Test
    void onlyTreatsServeAsASubCommandWhenItComesFirst() {
      assertEquals(App.Target.CLI, App.route(new String[] {"kotlin", "serve"}).target());
    }
  }
}
