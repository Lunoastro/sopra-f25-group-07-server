package ch.uzh.ifi.hase.soprafs24.service;

import ch.uzh.ifi.hase.soprafs24.constant.ColorID;
import ch.uzh.ifi.hase.soprafs24.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs24.entity.User;
import ch.uzh.ifi.hase.soprafs24.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for the UserResource REST resource.
 *
 * @see UserService
 */
@WebAppConfiguration
@SpringBootTest
 class UserServiceIntegrationTest {

  @Qualifier("userRepository")
  @Autowired
  private UserRepository userRepository;

  @Autowired
  private UserService userService;

  @BeforeEach
   void setup() {
    userRepository.deleteAll();
  }

  @Test
   void createUser_validInputs_success() {
    // given
    assertNull(userRepository.findByUsername("testUsername"));

    User testUser = new User();
    testUser.setName("user_testUsername");
    testUser.setUsername("testUsername");
    testUser.setPassword("12345");

    testUser.setXP(0); //later on this will be done in the service
    testUser.setLevel(1);
    testUser.setColor(ColorID.C1);

    // when
    User createdUser = userService.createUser(testUser);

    // then
    assertEquals(testUser.getId(), createdUser.getId());
    assertEquals(testUser.getName(), createdUser.getName());
    assertEquals(testUser.getUsername(), createdUser.getUsername());
    assertEquals(testUser.getPassword(), createdUser.getPassword());
    assertNotNull(createdUser.getToken());
    assertEquals(UserStatus.ONLINE, createdUser.getStatus());
    assertEquals(testUser.getXP(), createdUser.getXP());
    assertEquals(testUser.getLevel(), createdUser.getLevel());
    assertEquals(testUser.getColor(), createdUser.getColor());
  }

  @Test
   void createUser_duplicateUsername_throwsException() {
    assertNull(userRepository.findByUsername("testUsername"));

    User testUser = new User();
    testUser.setName("user_testUsername");
    testUser.setUsername("testUsername");
    testUser.setPassword("12345");

    testUser.setXP(0); //later on this will be done in the service
    testUser.setLevel(1);
    testUser.setColor(ColorID.C1);

    User createdUser = userService.createUser(testUser);

    // attempt to create second user with same username
    User testUser2 = new User();

    // change the name but forget about the username
    testUser2.setName("user_testUsername2");
    testUser2.setUsername("testUsername");
    testUser.setPassword("12345");

    testUser.setXP(0); //later on this will be done in the service
    testUser.setLevel(1);
    testUser.setColor(ColorID.C1);

    // check that an error is thrown
    assertThrows(ResponseStatusException.class, () -> userService.createUser(testUser2));
  }
}
