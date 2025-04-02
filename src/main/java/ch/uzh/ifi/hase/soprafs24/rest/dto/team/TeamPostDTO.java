package ch.uzh.ifi.hase.soprafs24.rest.dto.team;

public class TeamPostDTO {
  
    private String teamName;
    private String teamCode;  // Optional field for the team code, if it is not provided in the body the string will be null


    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public String getTeamCode() {
        return teamCode;
    }

    public void setTeamCode(String teamCode) {
        this.teamCode = teamCode;
    }
}
