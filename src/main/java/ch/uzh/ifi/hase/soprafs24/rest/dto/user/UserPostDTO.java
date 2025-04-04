package ch.uzh.ifi.hase.soprafs24.rest.dto.user;
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
public class UserPostDTO {
  private String name;
  private String username;
  private String password;
}
