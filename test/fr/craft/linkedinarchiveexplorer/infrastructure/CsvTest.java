package fr.craft.linkedinarchiveexplorer.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CsvTest {

  @Nested
  class Fields {

    @Test
    void splitsUnquotedFieldsOnComma() {
      assertEquals(List.of(List.of("a", "b", "c")), Csv.parseRecords("a,b,c"));
    }

    @Test
    void preservesSurroundingSpaces() {
      assertEquals(List.of(List.of("a ", " b")), Csv.parseRecords("a , b"));
    }

    @Test
    void keepsEmptyFields() {
      assertEquals(List.of(List.of("a", "", "c")), Csv.parseRecords("a,,c"));
    }
  }

  @Nested
  class Quoting {

    @Test
    void unwrapsAQuotedField() {
      String csv = """
          "a"\
          """;
      assertEquals(List.of(List.of("a")), Csv.parseRecords(csv));
    }

    @Test
    void keepsCommasInsideQuotes() {
      String csv = """
          "a,b",c\
          """;
      assertEquals(List.of(List.of("a,b", "c")), Csv.parseRecords(csv));
    }

    @Test
    void keepsNewlinesInsideQuotes() {
      String csv =
          """
          "line1
          line2"
          """;
      assertEquals(List.of(List.of("line1\nline2")), Csv.parseRecords(csv));
    }

    @Test
    void unescapesDoubledQuotes() {
      String csv = """
          "he said ""hi""\"\
          """;
      assertEquals(List.of(List.of("he said \"hi\"")), Csv.parseRecords(csv));
    }
  }

  @Nested
  class Records {

    @ParameterizedTest(name = "separator [{0}]")
    @ValueSource(strings = {"a\nb", "a\r\nb"})
    void splitsRecordsOnLfAndCrlf(String content) {
      assertEquals(List.of(List.of("a"), List.of("b")), Csv.parseRecords(content));
    }

    @Test
    void acceptsAFinalRecordWithoutTrailingNewline() {
      assertEquals(List.of(List.of("a", "b")), Csv.parseRecords("a,b"));
    }

    @Test
    void ignoresATrailingNewline() {
      assertEquals(List.of(List.of("a"), List.of("b")), Csv.parseRecords("a\nb\n"));
    }

    @Test
    void skipsBlankLines() {
      assertEquals(List.of(List.of("a"), List.of("b")), Csv.parseRecords("a\n\nb"));
    }

    @Test
    void yieldsNoRecordForEmptyInput() {
      assertTrue(Csv.parseRecords("").isEmpty());
    }
  }

  @Nested
  class TableView {

    private final CsvTable table =
        Csv.parse(
            """
            Date,Message
            2024-01-01,"hello, world"
            """);

    @Test
    void mapsCellsByHeaderName() {
      CsvRow row = table.rows().get(0);

      assertEquals("2024-01-01", row.get("Date"));
      assertEquals("hello, world", row.get("Message"));
    }

    @Test
    void exposesTheHeader() {
      assertEquals(List.of("Date", "Message"), table.headers());
    }

    @Test
    void rejectsAnUnknownColumn() {
      assertThrows(IllegalArgumentException.class, () -> table.rows().get(0).get("Nope"));
    }
  }
}
