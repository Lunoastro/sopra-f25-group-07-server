package ch.uzh.ifi.hase.soprafs24.entity;

import javax.persistence.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Team Representation
 * This class represents a team in the system and how it is stored in the database.
 * Every variable will be mapped into a database field with the @Column annotation.
 */
@Entity
@Table(name = "TEAM")
public class Team implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue
    private Long teamId;

    @Column(nullable = false, unique = true)
    private String teamName;

    @Column(nullable = false)
    private int teamXP;

    @Column(nullable = false)
    private int teamLevel;

    @Column(nullable = false, unique = true)
    private String teamCode;

    @ElementCollection
    private List<Long> teamMembers = new ArrayList<>(); // Storing user IDs of the team members


    /*@ElementCollection
    teamTasks

    @ElementCollection
    teamPendingTasks*/


    // Getters and Setters
    public Long getTeamId() {
        return teamId;
    }

    public void setTeamId(Long teamId) {
        this.teamId = teamId;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public int getTeamXP() {
        return teamXP;
    }

    public void setTeamXP(int teamXP) {
        this.teamXP = teamXP;
    }

    public String getTeamCode() {
        return teamCode;
    }

    public void setTeamCode(String teamCode) {
        this.teamCode = teamCode;
    }

    public int getTeamLevel() {
        return teamLevel;
    }

    public void setTeamLevel(int teamLevel) {
        this.teamLevel = teamLevel;
    }

    public List<Long> getTeamMembers() {
        return teamMembers;
    }

    public void setTeamMembers(List<Long> teamMembers) {
        this.teamMembers = teamMembers;
    }

    /*public List<Long> getTeamTasks() {
        return teamTasks;
    }

    public void setTeamTasks(List<Long> teamTasks) {
        this.teamTasks = teamTasks;
    }

    public List<TaskStatus> getTeamPendingTasks() {
        return teamPendingTasks;
    }

    public void setTeamPendingTasks(List<TaskStatus> teamPendingTasks) {
        this.teamPendingTasks = teamPendingTasks;
    }*/
}

