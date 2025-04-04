package ch.uzh.ifi.hase.soprafs24.rest.dto.task;

public class TaskGetDTO {
    private Long taskId;
    private Long isAssignedTo;
    private String taskName;
    private String taskDescription;
    private String deadline;
    private String taskColor;
    private boolean activeStatus;

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }
    public Long getIsAssignedTo() {
        return isAssignedTo;
    }
    public void setIsAssignedTo(Long isAssignedTo) {
        this.isAssignedTo = isAssignedTo;
    }

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

    public String getDeadline() {
        return deadline;
    }

    public void setDeadline(String deadline) {
        this.deadline = deadline;
    }

    public String getTaskColor() {
        return taskColor;
    }

    public void setTaskColor(String taskColor) {
        this.taskColor = taskColor;
    }

    public boolean isActiveStatus() {
        return activeStatus;
    }

    public void setActiveStatus(boolean activeStatus) {
        this.activeStatus = activeStatus;
    }
}
