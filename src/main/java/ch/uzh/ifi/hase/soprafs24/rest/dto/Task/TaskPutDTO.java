package ch.uzh.ifi.hase.soprafs24.rest.dto.Task;
import java.util.Date;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import ch.uzh.ifi.hase.soprafs24.constant.ColorID;

public class TaskPutDTO {
    
    private String taskName;
    private Long isAssignedTo;
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Incorrect color provided.");
        }
    }

    public boolean isActiveStatus() {
        return activeStatus;
    }

    public void setActiveStatus(boolean activeStatus) {
        this.activeStatus = activeStatus;
    }
    public Long getIsAssignedTo() {
        return isAssignedTo;
    }
    public void setIsAssignedTo(Long isAssignedTo) {
        this.isAssignedTo = isAssignedTo;
    }
}
