package fr.craft.linkedinarchiveexplorer.web;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BrowserLauncherTest {

  @Nested
  class ChoosingTheCommand {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
      "'Linux',        'xdg-open http://localhost:8080'",
      "'Mac OS X',     'open http://localhost:8080'",
      "'Windows 11',   'cmd /c start  http://localhost:8080'",
      "'Windows 10',   'cmd /c start  http://localhost:8080'",
      "'FreeBSD',      'xdg-open http://localhost:8080'",
    })
    void picksTheOpenerOfTheSystem(String osName, String expected) {
      // Anything that is not macOS or Windows gets xdg-open: it is the freedesktop
      // convention, and a wrong guess costs an unopened window, never a failure.
      assertEquals(
          List.of(expected.split(" ")),
          SystemBrowser.commandFor(osName, "http://localhost:8080"));
    }
  }

  @Nested
  class Failing {

    @Test
    void swallowsACommandThatDoesNotExist() {
      // No graphical session, no xdg-open, a locked-down box: the server must stay up.
      // The opener is injected so that the suite never actually opens a browser.
      SystemBrowser browser = new SystemBrowser(List.of("definitely-not-a-command-42"));

      assertDoesNotThrow(() -> browser.open("http://localhost:8080"));
    }
  }

  @Nested
  class DoingNothing {

    @Test
    void theNoneLauncherOpensNothingAndSaysSo() {
      assertDoesNotThrow(() -> BrowserLauncher.NONE.open("http://localhost:8080"));
    }
  }
}
