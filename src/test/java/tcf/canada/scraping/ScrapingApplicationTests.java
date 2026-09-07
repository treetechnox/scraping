package tcf.canada.scraping;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tcf.canada.scraping.model.Combination;
import tcf.canada.scraping.model.MonthEntry;
import tcf.canada.scraping.model.MonthResult;
import tcf.canada.scraping.model.Task;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real integration tests that hit the live reussir-tcfcanada.com website.
 *
 * These tests verify:
 * 1. The month index is populated with all expected months
 * 2. A month page can be scraped successfully
 * 3. Combinations are correctly separated and identified
 * 4. Each combination contains exactly 3 tasks (Tâche 1, 2, 3)
 * 5. Task content is non-empty and meaningful
 * 6. Multiple months can be scraped correctly
 */
@SpringBootTest
class ScrapingApplicationTests {

    @Autowired
    private TcfScraperService scraperService;

    // ---------------------------------------------------------------
    // Context load
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Spring context loads successfully")
    void contextLoads() {
        assertNotNull(scraperService, "TcfScraperService should be autowired");
    }

    // ---------------------------------------------------------------
    // Month Index tests
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Month index contains all 44 expected months")
    void monthIndex_containsAllMonths() {
        List<MonthEntry> index = scraperService.getMonthIndex();
        assertNotNull(index);
        assertFalse(index.isEmpty(), "Month index should not be empty");
        assertEquals(44, index.size(), "Should have 44 months in the index");
    }

    @Test
    @DisplayName("Month index first entry is Mars 2026")
    void monthIndex_firstEntryIsMars2026() {
        List<MonthEntry> index = scraperService.getMonthIndex();
        MonthEntry first = index.get(0);
        assertEquals("Mars 2026", first.getLabel());
        assertEquals("mars-2026-expression-ecrite", first.getSlug());
        assertEquals(2026, first.getYear());
        assertTrue(first.getUrl().contains("reussir-tcfcanada.com"), "URL should point to reussir-tcfcanada.com");
    }

    @Test
    @DisplayName("Month index last entry is Août 2022")
    void monthIndex_lastEntryIsAout2022() {
        List<MonthEntry> index = scraperService.getMonthIndex();
        MonthEntry last = index.get(index.size() - 1);
        assertEquals("Août 2022", last.getLabel());
        assertEquals(2022, last.getYear());
    }

    @Test
    @DisplayName("All month entries have valid slugs and URLs")
    void monthIndex_allEntriesHaveValidSlugsAndUrls() {
        List<MonthEntry> index = scraperService.getMonthIndex();
        for (MonthEntry entry : index) {
            assertNotNull(entry.getSlug(), "Slug should not be null for " + entry.getLabel());
            assertFalse(entry.getSlug().isEmpty(), "Slug should not be empty for " + entry.getLabel());
            assertNotNull(entry.getUrl(), "URL should not be null for " + entry.getLabel());
            assertTrue(entry.getUrl().startsWith("https://reussir-tcfcanada.com/"),
                    "URL should start with base URL for " + entry.getLabel());
            assertTrue(entry.getYear() >= 2022 && entry.getYear() <= 2026,
                    "Year should be between 2022 and 2026 for " + entry.getLabel());
        }
    }

    // ---------------------------------------------------------------
    // Mars 2026 — Full scraping test (known structure)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Scrape Mars 2026 — returns a valid MonthResult")
    void scrapeMars2026_returnsValidResult() throws IOException {
        MonthResult result = scraperService.scrapeBySlug("mars-2026-expression-ecrite");

        assertNotNull(result, "Result should not be null");
        assertEquals("Mars 2026", result.getLabel());
        assertEquals("mars-2026-expression-ecrite", result.getSlug());
        assertEquals(2026, result.getYear());
        assertNotNull(result.getUrl());
    }

    @Test
    @DisplayName("Scrape Mars 2026 — contains multiple combinations")
    void scrapeMars2026_containsMultipleCombinations() throws IOException {
        MonthResult result = scraperService.scrapeBySlug("mars-2026-expression-ecrite");

        List<Combination> combinations = result.getCombinations();
        assertNotNull(combinations, "Combinations list should not be null");
        assertFalse(combinations.isEmpty(), "Should have at least one combination");
        assertTrue(combinations.size() >= 3,
                "Mars 2026 should have at least 3 combinations, found: " + combinations.size());
    }

    @Test
    @DisplayName("Scrape Mars 2026 — combinations are numbered consecutively from 1")
    void scrapeMars2026_combinationsAreNumberedConsecutively() throws IOException {
        MonthResult result = scraperService.scrapeBySlug("mars-2026-expression-ecrite");

        List<Combination> combinations = result.getCombinations();
        for (int i = 0; i < combinations.size(); i++) {
            assertEquals(i + 1, combinations.get(i).getNumber(),
                    "Combination at index " + i + " should have number " + (i + 1));
        }
    }

    @Test
    @DisplayName("Scrape Mars 2026 — each combination contains exactly 3 tasks")
    void scrapeMars2026_eachCombinationHasThreeTasks() throws IOException {
        MonthResult result = scraperService.scrapeBySlug("mars-2026-expression-ecrite");

        for (Combination combo : result.getCombinations()) {
            assertEquals(3, combo.getTasks().size(),
                    "Combinaison " + combo.getNumber() + " should have exactly 3 tasks, found: "
                            + combo.getTasks().size());
        }
    }

    @Test
    @DisplayName("Scrape Mars 2026 — tasks are numbered 1, 2, 3 in order")
    void scrapeMars2026_tasksAreNumberedCorrectly() throws IOException {
        MonthResult result = scraperService.scrapeBySlug("mars-2026-expression-ecrite");

        for (Combination combo : result.getCombinations()) {
            List<Task> tasks = combo.getTasks();
            assertEquals(1, tasks.get(0).getNumber(),
                    "First task in Combinaison " + combo.getNumber() + " should be Tâche 1");
            assertEquals(2, tasks.get(1).getNumber(),
                    "Second task in Combinaison " + combo.getNumber() + " should be Tâche 2");
            assertEquals(3, tasks.get(2).getNumber(),
                    "Third task in Combinaison " + combo.getNumber() + " should be Tâche 3");
        }
    }

    @Test
    @DisplayName("Scrape Mars 2026 — all tasks have non-empty content")
    void scrapeMars2026_allTasksHaveContent() throws IOException {
        MonthResult result = scraperService.scrapeBySlug("mars-2026-expression-ecrite");

        for (Combination combo : result.getCombinations()) {
            for (Task task : combo.getTasks()) {
                assertNotNull(task.getContent(),
                        "Task content should not be null in Combinaison " + combo.getNumber()
                                + " Tâche " + task.getNumber());
                assertFalse(task.getContent().trim().isEmpty(),
                        "Task content should not be empty in Combinaison " + combo.getNumber()
                                + " Tâche " + task.getNumber());
                assertTrue(task.getContent().length() > 20,
                        "Task content should be meaningful (>20 chars) in Combinaison " + combo.getNumber()
                                + " Tâche " + task.getNumber());
            }
        }
    }

    @Test
    @DisplayName("Scrape Mars 2026 — Tâche 1 and Tâche 2 have word limits")
    void scrapeMars2026_tache1And2HaveWordLimits() throws IOException {
        MonthResult result = scraperService.scrapeBySlug("mars-2026-expression-ecrite");

        for (Combination combo : result.getCombinations()) {
            Task tache1 = combo.getTasks().get(0);
            Task tache2 = combo.getTasks().get(1);

            assertNotNull(tache1.getWordLimit(),
                    "Tâche 1 should have a word limit in Combinaison " + combo.getNumber());
            assertFalse(tache1.getWordLimit().isEmpty(),
                    "Tâche 1 word limit should not be empty in Combinaison " + combo.getNumber());
            assertTrue(tache1.getWordLimit().toLowerCase().contains("mots"),
                    "Tâche 1 word limit should mention 'mots' in Combinaison " + combo.getNumber());

            assertNotNull(tache2.getWordLimit(),
                    "Tâche 2 should have a word limit in Combinaison " + combo.getNumber());
            assertFalse(tache2.getWordLimit().isEmpty(),
                    "Tâche 2 word limit should not be empty in Combinaison " + combo.getNumber());
            assertTrue(tache2.getWordLimit().toLowerCase().contains("mots"),
                    "Tâche 2 word limit should mention 'mots' in Combinaison " + combo.getNumber());
        }
    }

    @Test
    @DisplayName("Scrape Mars 2026 — Tâche 3 contains Document 1 and Document 2")
    void scrapeMars2026_tache3ContainsDocuments() throws IOException {
        MonthResult result = scraperService.scrapeBySlug("mars-2026-expression-ecrite");

        for (Combination combo : result.getCombinations()) {
            Task tache3 = combo.getTasks().get(2);
            String content = tache3.getContent();
            assertTrue(content.contains("Document"),
                    "Tâche 3 should contain 'Document' in Combinaison " + combo.getNumber()
                            + ". Actual: " + content.substring(0, Math.min(200, content.length())));
        }
    }

    @Test
    @DisplayName("Scrape Mars 2026 — Combinaison 1 Tâche 1 matches known live content")
    void scrapeMars2026_combinaison1_tache1_matchesKnownContent() throws IOException {
        MonthResult result = scraperService.scrapeBySlug("mars-2026-expression-ecrite");

        // Based on the real page, Combinaison 1 Tâche 1 is about Cédric's wedding at a château
        Combination combo1 = result.getCombinations().stream()
                .filter(c -> c.getNumber() == 1)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Combinaison 1 not found"));

        Task tache1 = combo1.getTasks().get(0);
        String content = tache1.getContent().toLowerCase();

        assertTrue(content.contains("cédric") || content.contains("cedric") || content.contains("château"),
                "Tâche 1 of Combinaison 1 should mention Cédric or the château. Actual: "
                        + tache1.getContent().substring(0, Math.min(300, tache1.getContent().length())));
    }

    @Test
    @DisplayName("Scrape Mars 2026 — Combinaison 5 Tâche 1 mentions anniversary")
    void scrapeMars2026_combinaison5_tache1_matchesKnownContent() throws IOException {
        MonthResult result = scraperService.scrapeBySlug("mars-2026-expression-ecrite");

        Combination combo5 = result.getCombinations().stream()
                .filter(c -> c.getNumber() == 5)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Combinaison 5 not found"));

        Task tache1 = combo5.getTasks().get(0);
        String content = tache1.getContent().toLowerCase();

        assertTrue(content.contains("anniversaire") || content.contains("cadeau") || content.contains("ami"),
                "Tâche 1 of Combinaison 5 should be about an anniversary gift. Actual: "
                        + tache1.getContent().substring(0, Math.min(300, tache1.getContent().length())));
    }

    // ---------------------------------------------------------------
    // Parameterized test — multiple recent months
    // ---------------------------------------------------------------

    @ParameterizedTest(name = "Scrape [{0}] — returns valid combinations with tasks")
    @ValueSource(strings = {
        "mars-2026-expression-ecrite",
        "fevrier-2026-expression-ecrite",
        "janvier-2026-expression-ecrite"
    })
    @DisplayName("Recent months all have valid combinations and tasks")
    void scrapeRecentMonths_returnValidStructure(String slug) throws IOException {
        MonthResult result = scraperService.scrapeBySlug(slug);

        assertNotNull(result, "Result should not be null for slug: " + slug);
        assertNotNull(result.getLabel(), "Label should not be null for slug: " + slug);
        assertFalse(result.getCombinations().isEmpty(),
                "Should have at least 1 combination for slug: " + slug);

        for (Combination combo : result.getCombinations()) {
            assertFalse(combo.getTasks().isEmpty(),
                    "Combination " + combo.getNumber() + " in [" + slug + "] should have tasks");
            assertEquals(3, combo.getTasks().size(),
                    "Combination " + combo.getNumber() + " in [" + slug + "] should have exactly 3 tasks");
            for (Task task : combo.getTasks()) {
                assertFalse(task.getContent().trim().isEmpty(),
                        "Task " + task.getNumber() + " content should not be empty in [" + slug + "]");
            }
        }
    }

    // ---------------------------------------------------------------
    // Scrape by URL
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Scrape by URL returns same combination count as scrape by slug")
    void scrapeByUrl_returnsSameAsSlug() throws IOException {
        String url = "https://reussir-tcfcanada.com/mars-2026-expression-ecrite/";
        MonthResult byUrl   = scraperService.scrapeByUrl(url);
        MonthResult bySlug  = scraperService.scrapeBySlug("mars-2026-expression-ecrite");

        assertNotNull(byUrl);
        assertNotNull(bySlug);
        assertEquals(bySlug.getCombinations().size(), byUrl.getCombinations().size(),
                "Both methods should return the same number of combinations");
    }

    // ---------------------------------------------------------------
    // Error handling
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Unknown slug throws IllegalArgumentException")
    void unknownSlug_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> scraperService.scrapeBySlug("this-slug-does-not-exist"),
                "Should throw IllegalArgumentException for unknown slug");
    }
}
