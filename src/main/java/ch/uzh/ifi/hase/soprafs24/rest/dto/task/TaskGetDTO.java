package ch.uzh.ifi.hase.soprafs24.rest.dto.task;
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
public class TaskGetDTO {
    private Long id;
    private Long creatorId;
    private Long isAssignedTo;
    private String name;
    private String description;
    private String deadline;
    private String color;
    private Boolean activeStatus;
    private Integer value;
    private String googleEventId;
}
