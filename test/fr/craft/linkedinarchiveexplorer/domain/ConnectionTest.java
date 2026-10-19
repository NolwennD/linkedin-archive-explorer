package fr.craft.linkedinarchiveexplorer.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ConnectionTest {

  private static final Connection ADA =
      new Connection("Ada", "Lovelace", "https://li/in/ada", "Analytical Engines", "Software Engineer", Optional.empty());

  private static List<ConnectionField> fieldsFound(String term, Connection connection) {
    return connection
        .find(SearchTerm.literal(term))
        .map(hit -> assertInstanceOf(ConnectionHit.class, hit).matches().stream().map(ConnectionMatch::field).toList())
        .orElse(List.of());
  }

  @Nested
  class Matching {

    @Test
    void findsTheFullNameAcrossFirstAndLastName() {
      assertEquals(List.of(ConnectionField.NAME), fieldsFound("Ada Lovelace", ADA));
    }

    @Test
    void findsTheCompany() {
      assertEquals(List.of(ConnectionField.COMPANY), fieldsFound("Engines", ADA));
    }

    @Test
    void findsThePosition() {
      assertEquals(List.of(ConnectionField.POSITION), fieldsFound("Software", ADA));
    }

    @Test
    void reportsEveryFieldTheTermOccursIn() {
      Connection adaOfAda =
          new Connection("Ada", "Lovelace", "https://li/in/ada", "Ada Labs", "Founder", Optional.empty());

      assertEquals(List.of(ConnectionField.NAME, ConnectionField.COMPANY), fieldsFound("Ada", adaOfAda));
    }

    @Test
    void doesNotMatchAcrossTwoFields() {
      assertTrue(fieldsFound("LovelaceAnalytical", ADA).isEmpty());
      assertTrue(fieldsFound("EnginesSoftware", ADA).isEmpty());
    }

    @Test
    void isCaseAndAccentSensitiveLikeEverythingElse() {
      assertTrue(fieldsFound("ada", ADA).isEmpty());
    }

    @Test
    void honoursTheSearchOptions() {
      SearchTerm term = new SearchTerm("ada", CaseSensitivity.INSENSITIVE, WordScope.WHOLE_WORD);

      assertTrue(ADA.find(term).isPresent());
    }
  }

  @Nested
  class Hit {

    @Test
    void carriesTheConnectionDate() {
      Connection dated =
          new Connection("Ada", "Lovelace", "https://li/in/ada", "", "", Optional.of(java.time.LocalDate.of(2025, 3, 4)));

      SearchHit hit = dated.find(SearchTerm.literal("Ada")).orElseThrow();

      assertEquals(Optional.of(java.time.LocalDate.of(2025, 3, 4)), hit.date());
    }
  }

  @Test
  void aConnectionCannotExistWithoutAProfileLink() {
    assertThrows(
        IllegalArgumentException.class, () -> new Connection("Ada", "Lovelace", " ", "", "", Optional.empty()));
  }
}
