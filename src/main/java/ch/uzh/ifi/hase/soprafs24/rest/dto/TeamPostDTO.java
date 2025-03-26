package ch.uzh.ifi.hase.soprafs24.rest.dto;

public class TeamPostDTO {
  
    private String teamName;
    private Long userId;
    private String teamCode;  // Optional field for the team code, if it is not provided in the body the string will be null


    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTeamCode() {
        return teamCode;
    }

    public void setTeamCode(String teamCode) {
        this.teamCode = teamCode;
    }
}
