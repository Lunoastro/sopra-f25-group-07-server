package ch.uzh.ifi.hase.soprafs24.rest.dto.team;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamGetDTO {
  
    private Long teamId;
    private String teamName;
    private int teamXP;
    private int teamLevel;
    private String teamCode;
    private List<Long> teamMembers;
}
