package tcf.canada.scraping.model;

import java.util.ArrayList;
import java.util.List;

public class MonthResult {
    private String label;               // e.g. "Mars 2026"
    private String slug;                // e.g. "mars-2026-expression-ecrite"
    private int year;
    private String url;
    private List<Combination> combinations = new ArrayList<>();

    public MonthResult() {}

    public MonthResult(String label, String slug, int year, String url) {
        this.label = label;
        this.slug = slug;
        this.year = year;
        this.url = url;
    }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public List<Combination> getCombinations() { return combinations; }
    public void setCombinations(List<Combination> combinations) { this.combinations = combinations; }

    public void addCombination(Combination combination) { this.combinations.add(combination); }
}
