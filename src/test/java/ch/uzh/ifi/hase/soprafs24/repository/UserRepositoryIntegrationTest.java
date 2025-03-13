package ch.uzh.ifi.hase.soprafs24.repository;

import ch.uzh.ifi.hase.soprafs24.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs24.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Date;

@DataJpaTest
public class UserRepositoryIntegrationTest {

  @Autowired
  private TestEntityManager entityManager;

  @Autowired
  private UserRepository userRepository;

  @Test
  public void findByUsername_success() {
    // given
    User user = new User();
    Date now = new Date(new Date().getTime() + 3600 * 1000);
    user.setName("user_firstname@lastname");
    user.setUsername("firstname@lastname");
    user.setPassword("1234");
    user.setStatus(UserStatus.OFFLINE);
    user.setToken("1");
    user.setCreationDate(now);
    user.setBirthDate(null);

    entityManager.persist(user);
    entityManager.flush();

    // when
    User found = userRepository.findByUsername(user.getUsername());

    // then
    assertNotNull(found.getId());
    assertNotNull(found.getPassword());
    assertEquals(found.getName(), user.getName());
    assertEquals(found.getUsername(), user.getUsername());
    assertEquals(found.getToken(), user.getToken());
    assertEquals(found.getStatus(), user.getStatus());
    assertEquals(found.getCreationDate(), user.getCreationDate());
    assertEquals(found.getBirthDate(), user.getBirthDate());
  }
}
