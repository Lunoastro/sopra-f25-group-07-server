package ch.uzh.ifi.hase.soprafs24.rest.dto;
import java.util.Date;

public class TaskPostDTO {
    private String taskName;
    private String taskDescription;
    private Date deadline;

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getTaskDescription() {
        return taskDescription;
    }

    public void setTaskDescription(String taskDescription) {
        this.taskDescription = taskDescription;
    }

    public Date getDeadline() {
        return deadline;
    }

    public void setDeadline(Date deadline) {
        this.deadline = deadline;
    }
}
