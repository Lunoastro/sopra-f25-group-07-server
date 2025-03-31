package ch.uzh.ifi.hase.soprafs24.rest.dto.Task;
import java.util.Date;
import ch.uzh.ifi.hase.soprafs24.constant.ColorID;

public class TaskPutDTO {
    
    private String taskName;
    private String taskDescription;
    private Date deadline;
    private ColorID taskColor;
    private boolean activeStatus;

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

    public ColorID getTaskColor() {
        return taskColor;
    }

    public void setTaskColor(String taskColor) {
        try {
            this.taskColor = ColorID.valueOf(taskColor);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid taskColor value: " + taskColor);
        }
    }

    public boolean isActiveStatus() {
        return activeStatus;
    }

    public void setActiveStatus(boolean activeStatus) {
        this.activeStatus = activeStatus;
    }
}
