package tcf.canada.scraping.model;

import java.util.ArrayList;
import java.util.List;

public class Combination {
    private int number;           // e.g. 1, 2, 3, 4, 5
    private List<Task> tasks = new ArrayList<>();

    public Combination() {}

    public Combination(int number) {
        this.number = number;
    }

    public int getNumber() { return number; }
    public void setNumber(int number) { this.number = number; }

    public List<Task> getTasks() { return tasks; }
    public void setTasks(List<Task> tasks) { this.tasks = tasks; }

    public void addTask(Task task) { this.tasks.add(task); }
}
