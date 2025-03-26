package ch.uzh.ifi.hase.soprafs24.rest.dto;

import java.util.List;

public class TeamGetDTO {
  
    private Long teamId;
    private String teamName;
    private int teamXP;
    private int teamLevel;
    private String teamCode;
    private List<Long> teamMembers;

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

    public int getTeamLevel() {
        return teamLevel;
    }

    public void setTeamLevel(int teamLevel) {
        this.teamLevel = teamLevel;
    }

    public String getTeamCode() {
        return teamCode;
    }

    public void setTeamCode(String teamCode) {
        this.teamCode = teamCode;
    }

    public List<Long> getTeamMembers() {
        return teamMembers;
    }

    public void setTeamMembers(List<Long> teamMembers) {
        this.teamMembers = teamMembers;
    }
}
