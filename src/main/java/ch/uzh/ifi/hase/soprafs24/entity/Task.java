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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = true)
    private String description;

    @Column(nullable = false)
    private boolean isPaused;

    @Column(nullable = true)
    private Date pausedDate;

    @Column(nullable = true)
    private Date unpausedDate;

    @Column(nullable = false)
    private Date deadline;

    @Column(nullable = false)
    private Date creationDate;

    @Column(nullable = false)
    private int value;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private ColorID color;

    @Column(nullable = false)
    private boolean activeStatus;

    @Column(nullable = false)
    private Long CreatorId;

    @Column(nullable = true)
    private Long isAssignedTo;

    @Column(nullable = true)
    private Integer frequency;


    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public Date getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public ColorID getColor() {
        return color;
    }

    public void setColor(ColorID color) {
        this.color = color;
    }

    public boolean getActiveStatus() {
        return activeStatus;
    }

    public void setActiveStatus(boolean activeStatus) {
        this.activeStatus = activeStatus;
    }

    public Long getCreatorId() {
        return CreatorId;
    }
    public void setCreatorId(Long creatorId) {
        CreatorId = creatorId;
    }

    public Long getIsAssignedTo() {
        return isAssignedTo;
    }
    public void setIsAssignedTo(Long isAssignedTo) {
        this.isAssignedTo = isAssignedTo;
    }

    public Integer getFrequency() {
        return frequency;
    }

    public void setFrequency(Integer frequency) {
        this.frequency = frequency;
    }
}
