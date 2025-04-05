package ch.uzh.ifi.hase.soprafs24.rest.dto.user;
import ch.uzh.ifi.hase.soprafs24.constant.UserStatus;
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
public class UserGetDTO {
  private Long id;
  private String name;
  private String username;
  private String token;
  private UserStatus status;
  private Date creationDate;
  private Date birthDate;
}
