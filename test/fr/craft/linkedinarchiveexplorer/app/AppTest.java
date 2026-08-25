package fr.craft.linkedinarchiveexplorer.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

    @ParameterizedTest(name = "[{0}] → serve={1} [{2}]")
    @CsvSource({
      "'',                       true,  ''",
      "'serve',                  true,  ''",
      "'serve --port 9000',      true,  '--port 9000'",
      "'serve --no-browser',     true,  '--no-browser'",
      "'kotlin',                 false, 'kotlin'",
      "'-i kotlin',              false, '-i kotlin'",
      "'--archive x.zip kotlin', false, '--archive x.zip kotlin'",
    })
    void sendsNothingAndServeToTheWebAndEverythingElseToTheTerminal(
        String line, boolean serve, String passed) {
      App.Route route = App.route(argumentsOf(line));

      assertEquals(serve, route.serve());
      assertEquals(expected(passed), route.arguments());
    }

    @Test
    void doesNotTryToGuessThatAServeOptionMeantTheServer() {
      // The rule is deliberately blunt: only "serve" first, or nothing at all, opens the
      // page. Reading the other arguments would mean duplicating both UIs' grammars here.
      App.Route route = App.route(new String[] {"--no-browser"});

      assertFalse(route.serve());
      assertEquals(List.of("--no-browser"), route.arguments());
    }

    @Test
    void onlyTreatsServeAsASubCommandWhenItComesFirst() {
      assertFalse(App.route(new String[] {"kotlin", "serve"}).serve());
    }
  }
}
