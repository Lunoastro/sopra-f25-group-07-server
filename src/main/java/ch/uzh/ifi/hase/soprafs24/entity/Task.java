package ch.uzh.ifi.hase.soprafs24.entity;

import javax.persistence.*;
import java.io.Serializable;
import ch.uzh.ifi.hase.soprafs24.constant.ColorID;
import ch.uzh.ifi.hase.soprafs24.listener.TaskEntityListener;

import java.util.Date;

/**
 * Internal Task Representation
 * This class defines how the Task entity is stored in the database.
 * Every variable is mapped to a database field using @Column.
 */
@Entity
@Table(name = "TASK")
@EntityListeners(TaskEntityListener.class)
public class Task implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "task_sequence")
    @SequenceGenerator(name = "task_sequence", sequenceName = "task_sequence", allocationSize = 1)
    @Column(updatable = false)
    private Long id;

    @Column(name = "google_event_id", nullable = true)
    private String googleEventId;

    @Column(nullable = false)
    private String name;
    
    @Column(nullable = true)
    private String description;

    @Column(nullable = true)
    private boolean isPaused;

    @Column(nullable = true)
    private Date pausedDate;

    @Column(nullable = true)
    private Date unpausedDate;

    @Column(nullable = false)
    private Date deadline;

    @Column(nullable = false)
    private Date creationDate;

    @Column(nullable = true)
    private Date startDate;

    @Column(nullable = false)
    private Integer value;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private ColorID color;

    @Column(nullable = false)
    private Boolean activeStatus;

    @Column(nullable = false)
    private Long creatorId;

    @Column(nullable = true)
    private String creatorName;

    @Column(nullable = true)
    private Long isAssignedTo;

    @Column(nullable = true)
    private String assigneeName;

    @Column(nullable = true)
    private Integer frequency;

    @Column(nullable = true)
    private Integer daysVisible;

    @Column(nullable = true)
    private Long teamId;

    @Column(nullable = true)
    private Boolean luckyDraw;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getGoogleEventId() { 
        return googleEventId; 
    }
    
    public void setGoogleEventId(String googleEventId) { 
        this.googleEventId = googleEventId; 
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

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Integer getValue() {
        return value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }

    public ColorID getColor() {
        return color;
    }

    public void setColor(ColorID color) {
        this.color = color;
    }

    public Boolean getActiveStatus() {
        return activeStatus;
    }

    public void setActiveStatus(Boolean activeStatus) {
        this.activeStatus = activeStatus;
    }

    public Boolean getLuckyDraw() {
        return luckyDraw;
    }

    public void setLuckyDraw(Boolean luckyDraw) {
        this.luckyDraw = luckyDraw;
    }

    public Long getcreatorId() {
        return creatorId;
    }
    public void setcreatorId(Long creatorId) {
        this.creatorId = creatorId;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public void setCreatorName(String creatorName) {
        this.creatorName = creatorName;
    }

    public Long getIsAssignedTo() {
        return isAssignedTo;
    }
    public void setIsAssignedTo(Long isAssignedTo) {
        this.isAssignedTo = isAssignedTo;
    }

    public String getAssigneeName() {
        return assigneeName;
    }

    public void setAssigneeName(String assigneeName) {
        this.assigneeName = assigneeName;
    }

    public Integer getFrequency() {
        return frequency;
    }

    public void setFrequency(Integer frequency) {
        this.frequency = frequency;
    }

    public Integer getDaysVisible() {
        return daysVisible;
    }

    public void setDaysVisible(Integer daysVisible) {
        this.daysVisible = daysVisible;
    }
    public Long getTeamId() {
        return teamId;
    }
    public void setTeamId(Long teamId) {
        this.teamId = teamId;
    }
}
