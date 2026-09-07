package tcf.canada.scraping.model;

public class Task {
    private int number;       // 1, 2, or 3
    private String content;   // full text of the task prompt
    private String wordLimit; // e.g. "60 mots minimum/120 mots maximum"

    public Task() {}

    public Task(int number, String content, String wordLimit) {
        this.number = number;
        this.content = content;
        this.wordLimit = wordLimit;
    }

    public int getNumber() { return number; }
    public void setNumber(int number) { this.number = number; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getWordLimit() { return wordLimit; }
    public void setWordLimit(String wordLimit) { this.wordLimit = wordLimit; }
}
