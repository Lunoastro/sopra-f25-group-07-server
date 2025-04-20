package ch.uzh.ifi.hase.soprafs24.rest.dto.task;
import java.util.Date;
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
public class TaskPostDTO {
    private String name;
    private String description;
    private Date startDate;
    private Date deadline;
    private Integer value;
    private Integer daysVisible;
    private Integer frequency;
}
