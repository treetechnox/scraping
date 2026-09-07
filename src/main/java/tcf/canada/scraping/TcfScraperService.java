package tcf.canada.scraping;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import tcf.canada.scraping.model.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TcfScraperService {

    private static final String BASE_URL = "https://reussir-tcfcanada.com/";

    private static final Pattern COMBINAISON_PATTERN =
        Pattern.compile("^\\s*combinaison\\s+(\\d+)\\s*$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern TACHE_PATTERN =
        Pattern.compile("^\\s*t[aâ]che\\s+(\\d+)\\s*$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern WORD_LIMIT_PATTERN =
        Pattern.compile("(\\d+\\s*mots\\s+(?:minimum|max(?:imum)?).*)", Pattern.CASE_INSENSITIVE);

    private static final List<MonthEntry> MONTH_INDEX = buildMonthIndex();

    // ── Public API ──────────────────────────────────────────────────────────

    public List<MonthEntry> getMonthIndex() {
        return MONTH_INDEX;
    }

    public MonthResult scrapeBySlug(String slug) throws IOException {
        MonthEntry entry = MONTH_INDEX.stream()
                .filter(e -> e.getSlug().equals(slug))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown slug: " + slug));
        return scrapeMonth(entry);
    }

    public MonthResult scrapeByUrl(String url) throws IOException {
        MonthEntry entry = MONTH_INDEX.stream()
                .filter(e -> e.getUrl().equals(url) || url.contains(e.getSlug()))
                .findFirst()
                .orElseGet(() -> {
                    MonthEntry e = new MonthEntry("Unknown", "unknown", 0);
                    e.setUrl(url);
                    return e;
                });
        return scrapeMonth(entry);
    }

    /** Debug: returns every text line Jsoup sees, with its tag and depth */
    public List<String> debugRawLines(String slug) throws IOException {
        MonthEntry entry = MONTH_INDEX.stream()
                .filter(e -> e.getSlug().equals(slug))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown slug: " + slug));

        Document doc = fetchDocument(entry.getUrl());
        List<String> lines = new ArrayList<>();
        Element body = doc.body();
        collectTextLines(body, 0, lines);
        return lines;
    }

    // ── Core scraping ───────────────────────────────────────────────────────

    MonthResult scrapeMonth(MonthEntry entry) throws IOException {
        Document doc = fetchDocument(entry.getUrl());
        MonthResult result = new MonthResult(entry.getLabel(), entry.getSlug(), entry.getYear(), entry.getUrl());
        parseByTextWalk(doc.body(), result);
        return result;
    }

    private Document fetchDocument(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                         + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .referrer("https://www.google.com")
                .timeout(20_000)
                .get();
    }

    /**
     * Text-first walk: collect every visible text node in document order,
     * then scan for Combinaison/Tâche markers purely by content.
     *
     * This is resilient to Elementor/WordPress div-soup because it doesn't
     * rely on semantic tag structure.
     */
    private void parseByTextWalk(Element root, MonthResult result) {

        // Collect all leaf-ish text lines in document order
        List<String> lines = new ArrayList<>();
        collectTextLines(root, 0, lines);

        Combination currentCombo = null;
        Task currentTask = null;
        StringBuilder content = new StringBuilder();
        String wordLimit = "";

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            // ── Combinaison N ──────────────────────────────────────────
            Matcher cm = COMBINAISON_PATTERN.matcher(line);
            if (cm.matches()) {
                // flush current task
                if (currentTask != null && currentCombo != null) {
                    finalizeTask(currentTask, content.toString(), wordLimit);
                    currentCombo.addTask(currentTask);
                    currentTask = null;
                    content.setLength(0);
                    wordLimit = "";
                }
                // flush current combination
                if (currentCombo != null && !currentCombo.getTasks().isEmpty()) {
                    result.addCombination(currentCombo);
                }
                currentCombo = new Combination(Integer.parseInt(cm.group(1)));
                continue;
            }

            if (currentCombo == null) continue;

            // ── Tâche N ────────────────────────────────────────────────
            Matcher tm = TACHE_PATTERN.matcher(line);
            if (tm.matches()) {
                if (currentTask != null) {
                    finalizeTask(currentTask, content.toString(), wordLimit);
                    currentCombo.addTask(currentTask);
                    content.setLength(0);
                    wordLimit = "";
                }
                currentTask = new Task();
                currentTask.setNumber(Integer.parseInt(tm.group(1)));
                continue;
            }

            if (currentTask == null) continue;

            // ── Word limit line ────────────────────────────────────────
            Matcher wm = WORD_LIMIT_PATTERN.matcher(line);
            if (wm.find() && line.length() < 120) {
                wordLimit = line.replaceAll("[()\\[\\]]", "").trim();
                continue;
            }

            // ── Skip noise ─────────────────────────────────────────────
            if (isNoise(line)) continue;

            // ── Accumulate content ─────────────────────────────────────
            if (content.length() > 0) content.append("\n\n");
            content.append(line);
        }

        // Flush last task and combo
        if (currentTask != null && currentCombo != null) {
            finalizeTask(currentTask, content.toString(), wordLimit);
            currentCombo.addTask(currentTask);
        }
        if (currentCombo != null && !currentCombo.getTasks().isEmpty()) {
            result.addCombination(currentCombo);
        }
    }

    /**
     * Recursively collect every distinct text string visible in the document.
     * We walk children first; if an element has no children (leaf) we emit its
     * own text. If it has children we recurse — this avoids duplicating parent
     * text that is just the concatenation of its children.
     */
    private void collectTextLines(Element el, int depth, List<String> out) {
        // Skip invisible / script / style nodes
        String tag = el.tagName().toLowerCase();
        if (tag.equals("script") || tag.equals("style") || tag.equals("noscript")
                || tag.equals("head") || tag.equals("nav") || tag.equals("footer")) {
            return;
        }

        Elements children = el.children();

        if (children.isEmpty()) {
            // Leaf node — emit own text
            String text = el.ownText().trim();
            if (!text.isEmpty()) out.add(text);
            return;
        }

        // Non-leaf: check if this element itself carries meaningful standalone text
        // (i.e. text that is NOT just whitespace wrapping its children)
        String ownText = el.ownText().trim();
        if (!ownText.isEmpty()) {
            out.add(ownText);
        }

        // Recurse into children
        for (Element child : children) {
            collectTextLines(child, depth + 1, out);
        }
    }

    private void finalizeTask(Task task, String rawContent, String wordLimit) {
        task.setContent(rawContent.trim());
        task.setWordLimit(wordLimit.trim());
    }

    private boolean isNoise(String line) {
        if (line.length() < 3) return true;
        String l = line.toLowerCase();
        return l.equals("attention!")
            || l.contains("skip to")
            || l.contains("bienvenue sur")
            || l.contains("réussir tcf canada")
            || l.contains("se connecter")
            || l.contains("s'inscrire")
            || l.contains("formations")
            || l.contains("consultation")
            || l.contains("cabinet d'immigration")
            || l.contains("pour partager les sujets")
            || l.contains("les pages")
            || l.contains("voir la source")
            || l.contains("consignes")
            || l.contains("exemples corrigés")
            || l.contains("réussir l'expression")
            || l.contains("compréhension écrite")
            || l.contains("compréhension orale")
            || l.contains("expression orale")
            || l.contains("expression écrite")
            || l.contains("méthodologie")
            || l.equals("blog")
            || l.equals("tarifs")
            || l.equals("actualité");
    }

    // ── Month index ─────────────────────────────────────────────────────────

    private static List<MonthEntry> buildMonthIndex() {
        List<MonthEntry> index = new ArrayList<>();
        index.add(new MonthEntry("Mars 2026",      "mars-2026-expression-ecrite",       2026));
        index.add(new MonthEntry("Février 2026",   "fevrier-2026-expression-ecrite",    2026));
        index.add(new MonthEntry("Janvier 2026",   "janvier-2026-expression-ecrite",    2026));
        index.add(new MonthEntry("Décembre 2025",  "decembre-2025-expression-ecrite",   2025));
        index.add(new MonthEntry("Novembre 2025",  "novembre-2025-expression-ecrite",   2025));
        index.add(new MonthEntry("Octobre 2025",   "octobre-2025-expression-ecrite",    2025));
        index.add(new MonthEntry("Septembre 2025", "septembre-2025-expression-ecrite",  2025));
        index.add(new MonthEntry("Août 2025",      "aout-2025-expression-ecrite",       2025));
        index.add(new MonthEntry("Juillet 2025",   "juillet-2025-expression-ecrite",    2025));
        index.add(new MonthEntry("Juin 2025",      "juin-2025-expression-ecrite",       2025));
        index.add(new MonthEntry("Mai 2025",       "mai-2025-expression-ecrite",        2025));
        index.add(new MonthEntry("Avril 2025",     "avril-2025-expression-ecrite",      2025));
        index.add(new MonthEntry("Mars 2025",      "mars-2025-expression-ecrite",       2025));
        index.add(new MonthEntry("Février 2025",   "fevrier-2025-expression-ecrite",    2025));
        index.add(new MonthEntry("Janvier 2025",   "janvier-2025-expression-ecrite",    2025));
        index.add(new MonthEntry("Décembre 2024",  "decembre-2024-expression-ecrite",   2024));
        index.add(new MonthEntry("Novembre 2024",  "novembre-2024-expression-ecrite",   2024));
        index.add(new MonthEntry("Octobre 2024",   "octobre-2024-expression-ecrite",    2024));
        index.add(new MonthEntry("Septembre 2024", "septembre-2024-expression-ecrite",  2024));
        index.add(new MonthEntry("Août 2024",      "aout-2024-expression-ecrite",       2024));
        index.add(new MonthEntry("Juillet 2024",   "juillet-2024-expression-ecrite",    2024));
        index.add(new MonthEntry("Juin 2024",      "juin-2024-expression-ecrite",       2024));
        index.add(new MonthEntry("Mai 2024",       "mai-2024-expression-ecrite",        2024));
        index.add(new MonthEntry("Avril 2024",     "avril-2024-expression-ecrite",      2024));
        index.add(new MonthEntry("Mars 2024",      "mars-2024-expression-ecrite",       2024));
        index.add(new MonthEntry("Février 2024",   "fevrier-2024-expression-ecrite",    2024));
        index.add(new MonthEntry("Janvier 2024",   "janvier-2024-expression-ecrite",    2024));
        index.add(new MonthEntry("Décembre 2023",  "decembre-2023-expression-ecrite",   2023));
        index.add(new MonthEntry("Novembre 2023",  "novembre-2023-expression-ecrite",   2023));
        index.add(new MonthEntry("Octobre 2023",   "octobre-2023-expression-ecrite",    2023));
        index.add(new MonthEntry("Septembre 2023", "septembre-2023-expression-ecrite",  2023));
        index.add(new MonthEntry("Août 2023",      "aout-2023-expression-ecrite",       2023));
        index.add(new MonthEntry("Juillet 2023",   "juil-2023-expression-ecrite",       2023));
        index.add(new MonthEntry("Juin 2023",      "juin-2023-expression-ecrite",       2023));
        index.add(new MonthEntry("Mai 2023",       "mai-2023-expression-ecrite",        2023));
        index.add(new MonthEntry("Avril 2023",     "avril-2023-expression-ecrite",      2023));
        index.add(new MonthEntry("Mars 2023",      "mars-2023-expression-ecrite",       2023));
        index.add(new MonthEntry("Février 2023",   "fevrier-2023-expression-ecrite",    2023));
        index.add(new MonthEntry("Janvier 2023",   "janvier-2023-expression-ecrite",    2023));
        index.add(new MonthEntry("Décembre 2022",  "decembre-2022-expression-ecrite",   2022));
        index.add(new MonthEntry("Novembre 2022",  "november-2022-expression-ecrite",   2022));
        index.add(new MonthEntry("Octobre 2022",   "octobre-2022-expression-ecrite",    2022));
        index.add(new MonthEntry("Septembre 2022", "september-2022-expression-ecrite",  2022));
        index.add(new MonthEntry("Août 2022",      "aout-2022-expression-ecrite",       2022));
        return index;
    }
}
