package ch.uzh.ifi.hase.soprafs24.entity;

import javax.persistence.*;
import java.io.Serializable;
import ch.uzh.ifi.hase.soprafs24.constant.ColorID;
import java.util.Date;

/**
 * Internal Task Representation
 * This class defines how the Task entity is stored in the database.
 * Every variable is mapped to a database field using @Column.
 */
@Entity
@Table(name = "TASK")
public class Task implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue
    private Long taskId;

    @Column(nullable = false)
    private String taskName;

    @Column(nullable = true)
    private String taskDescription;

    @Column(nullable = false)
    private boolean isPaused;

    @Column(nullable = true)
    private Date pausedDate;

    @Column(nullable = true)
    private Date unpausedDate;

    @Column(nullable = false)
    private Date deadline;

    @Column(nullable = false)
    private Date taskCreationDate;

    @Column(nullable = false)
    private int value;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private ColorID taskColor;

    @Column(nullable = false)
    private boolean activeStatus;

    @Column(nullable = true)
    private Long isAssignedTo;

    // Getters and Setters
    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
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

    public boolean isPaused() {
        return isPaused;
    }

    public void setPaused(boolean paused) {
        isPaused = paused;
    }

    public Date getPausedDate() {
        return pausedDate;
    }

    public void setPausedDate(Date pausedDate) {
        this.pausedDate = pausedDate;
    }

    public Date getUnpausedDate() {
        return unpausedDate;
    }

    public void setUnpausedDate(Date unpausedDate) {
        this.unpausedDate = unpausedDate;
    }

    public Date getDeadline() {
        return deadline;
    }

    public void setDeadline(Date deadline) {
        this.deadline = deadline;
    }

    public Date getTaskCreationDate() {
        return taskCreationDate;
    }

    public void setTaskCreationDate(Date taskCreationDate) {
        this.taskCreationDate = taskCreationDate;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public ColorID getTaskColor() {
        return taskColor;
    }

    public void setTaskColor(ColorID taskColor) {
        this.taskColor = taskColor;
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
