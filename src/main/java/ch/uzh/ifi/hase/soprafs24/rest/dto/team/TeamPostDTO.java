package ch.uzh.ifi.hase.soprafs24.rest.dto.team;
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
public class TeamPostDTO {
    private String name;
    private String code;  // Optional field for the team code, if it is not provided in the body the string will be null
}
