package ch.uzh.ifi.hase.soprafs24.rest.dto.task;
import java.util.Date;
import ch.uzh.ifi.hase.soprafs24.constant.ColorID;

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
public class TaskPutDTO {
    private String name;
    private Long isAssignedTo;
    private String description;
    private Date deadline;
    private ColorID color;
    private boolean activeStatus;
}
