package ch.uzh.ifi.hase.soprafs24.rest.dto.user;
import ch.uzh.ifi.hase.soprafs24.constant.ColorID;
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
public class UserPutDTO {
    private String username;
    private ColorID color;
    private Long id;
    private Date birthDate;
}
