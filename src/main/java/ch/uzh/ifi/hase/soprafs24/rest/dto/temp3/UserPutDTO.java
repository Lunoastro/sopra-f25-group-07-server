package ch.uzh.ifi.hase.soprafs24.rest.dto.user;
import java.util.Date;


public class UserPutDTO {
    
    private String username;
    private Long id;
    private Date birthDate;

    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Date getBirthDate(){
        return birthDate;
      }
    
    public void setBirthDate(Date birthDate) {
        this.birthDate = birthDate;
      }

}
