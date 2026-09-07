package tcf.canada.scraping.model;

public class MonthEntry {
    private String label;
    private String slug;
    private int year;
    private String url;

    public MonthEntry() {}

    public MonthEntry(String label, String slug, int year) {
        this.label = label;
        this.slug = slug;
        this.year = year;
        this.url = "https://reussir-tcfcanada.com/" + slug + "/";
    }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
}
