package tcf.canada.scraping;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tcf.canada.scraping.model.MonthEntry;
import tcf.canada.scraping.model.MonthResult;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/tcf")
@CrossOrigin
public class TcfController {

    private final TcfScraperService scraperService;

    public TcfController(TcfScraperService scraperService) {
        this.scraperService = scraperService;
    }

    /** GET /api/tcf/months — full month index */
    @GetMapping("/months")
    public ResponseEntity<List<MonthEntry>> getMonthIndex() {
        return ResponseEntity.ok(scraperService.getMonthIndex());
    }

    /** GET /api/tcf/months/{slug} — scrape one month */
    @GetMapping("/months/{slug}")
    public ResponseEntity<?> scrapeBySlug(@PathVariable String slug) {
        try {
            return ResponseEntity.ok(scraperService.scrapeBySlug(slug));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Unknown slug: " + slug);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Scraping failed: " + e.getMessage());
        }
    }

    /** GET /api/tcf/scrape?url=... — scrape by direct URL */
    @GetMapping("/scrape")
    public ResponseEntity<?> scrapeByUrl(@RequestParam String url) {
        try {
            return ResponseEntity.ok(scraperService.scrapeByUrl(url));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Scraping failed: " + e.getMessage());
        }
    }

    /**
     * GET /api/tcf/debug/{slug}
     *
     * Returns every text line Jsoup sees in document order, prefixed with
     * whether it matches Combinaison/Tâche patterns.
     * Use this to diagnose parsing issues: open in browser and search for
     * "COMBINAISON" or "TACHE" to verify detection.
     */
    @GetMapping("/debug/{slug}")
    public ResponseEntity<?> debug(@PathVariable String slug) {
        try {
            List<String> lines = scraperService.debugRawLines(slug);
            StringBuilder sb = new StringBuilder();
            for (String line : lines) {
                String trimmed = line.trim();
                String prefix = "";
                if (trimmed.matches("(?i)\\s*combinaison\\s+\\d+\\s*")) prefix = ">>> COMBINAISON DETECTED: ";
                else if (trimmed.matches("(?i)\\s*t[aâ]che\\s+\\d+\\s*"))  prefix = ">>> TACHE DETECTED: ";
                sb.append(prefix).append(trimmed).append("\n");
            }
            return ResponseEntity.ok()
                    .header("Content-Type", "text/plain;charset=UTF-8")
                    .body(sb.toString());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Debug failed: " + e.getMessage());
        }
    }
}
