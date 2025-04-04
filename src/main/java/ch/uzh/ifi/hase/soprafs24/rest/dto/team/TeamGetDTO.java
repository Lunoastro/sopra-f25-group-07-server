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
    private Long id;
    private String name;
    private int xp;
    private int level;
    private String code;
    private List<Long> members;
}
