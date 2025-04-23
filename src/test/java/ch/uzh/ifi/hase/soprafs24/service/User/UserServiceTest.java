package ch.uzh.ifi.hase.soprafs24.service.User;

import ch.uzh.ifi.hase.soprafs24.constant.ColorID;
import ch.uzh.ifi.hase.soprafs24.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs24.entity.User;
import ch.uzh.ifi.hase.soprafs24.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs24.service.UserService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.List;

class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User testUser;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);

        // given
        testUser = new User();
        testUser.setId(1L);
        testUser.setName("user_testUsername");
        testUser.setUsername("testUsername");
        testUser.setPassword("12345");

        // when -> any object is being saved in the userRepository -> return the dummy
        // testUser
        Mockito.when(userRepository.save(Mockito.any())).thenReturn(testUser);
    }

    @Test
    void createUser_validInputs_success() {
        User createdUser = userService.createUser(testUser);

        Mockito.verify(userRepository, Mockito.times(1)).save(Mockito.any());

        assertEquals(testUser.getId(), createdUser.getId());
        assertEquals(testUser.getName(), createdUser.getName());
        assertEquals(testUser.getUsername(), createdUser.getUsername());
        assertEquals(testUser.getPassword(), createdUser.getPassword());
        assertNotNull(createdUser.getToken());
        assertEquals(UserStatus.ONLINE, createdUser.getStatus());
    }

    @Test
    void createUser_duplicateUsername_throwsException() {
        // given -> a first user has already been created
        userService.createUser(testUser);

        // when -> setup additional mocks for UserRepository
        Mockito.when(userRepository.findByUsername(Mockito.any())).thenReturn(testUser);

        // then -> attempt to create second user with same user -> check that an error is thrown
        assertThrows(ResponseStatusException.class, () -> userService.createUser(testUser));
    }

    @Test
    void getUsers_returnsListOfUsers() {
        // given
        User secondUser = new User();
        secondUser.setId(2L);
        secondUser.setUsername("secondUser");
        Mockito.when(userRepository.findAll()).thenReturn(Arrays.asList(testUser, secondUser));

        // when
        List<User> users = userService.getUsers();

        // then
        assertEquals(2, users.size());
        assertEquals(testUser, users.get(0));
        assertEquals(secondUser, users.get(1));
    }

    @Test
    void getUserById_validId_returnsUser() {
        // when
        Mockito.when(userRepository.findUserById(testUser.getId())).thenReturn(testUser);
        User foundUser = userService.getUserById(testUser.getId());

        // then
        assertEquals(testUser, foundUser);
    }

    @Test
    void getUserById_invalidId_throwsException() {
        // when
        Mockito.when(userRepository.findUserById(999L)).thenReturn(null);

        // then
        assertThrows(ResponseStatusException.class, () -> userService.getUserById(999L));
    }

    @Test
    void loginUser_validCredentials_success() {
        // given
        User loginUser = new User();
        loginUser.setUsername("testUsername");
        loginUser.setPassword("12345");

        Mockito.when(userRepository.findByUsername(loginUser.getUsername())).thenReturn(testUser);

        // when
        User loggedInUser = userService.loginUser(loginUser);

        // then
        assertEquals(UserStatus.ONLINE, loggedInUser.getStatus());
        assertEquals(testUser.getUsername(), loggedInUser.getUsername());
    }

    @Test
    void loginUser_invalidUsername_throwsException() {
        // given
        User loginUser = new User();
        loginUser.setUsername("invalidUsername");
        loginUser.setPassword("12345");

        Mockito.when(userRepository.findByUsername(loginUser.getUsername())).thenReturn(null);

        // when / then
        assertThrows(ResponseStatusException.class, () -> userService.loginUser(loginUser));
    }

    @Test
    void loginUser_invalidPassword_throwsException() {
        // given
        User loginUser = new User();
        loginUser.setUsername("testUsername");
        loginUser.setPassword("wrongPassword");

        Mockito.when(userRepository.findByUsername(loginUser.getUsername())).thenReturn(testUser);

        // when / then
        assertThrows(ResponseStatusException.class, () -> userService.loginUser(loginUser));
    }

    @Test
    void logoffUser_validUser_success() {
        // given
        User logoffUser = new User();
        logoffUser.setUsername("testUsername");

        Mockito.when(userRepository.findByUsername(logoffUser.getUsername())).thenReturn(testUser);

        // when
        userService.logoffUser(logoffUser);

        // then
        assertEquals(UserStatus.OFFLINE, testUser.getStatus());
    }

    @Test
    void logoffUser_invalidUser_throwsException() {
        // given
        User logoffUser = new User();
        logoffUser.setUsername("invalidUsername");

        Mockito.when(userRepository.findByUsername(logoffUser.getUsername())).thenReturn(null);

        // when / then
        assertThrows(ResponseStatusException.class, () -> userService.logoffUser(logoffUser));
    }

    @Test
    void updateUser_validUpdate_success() {
        // given
        User inputUser = new User();
        inputUser.setId(1L);
        inputUser.setUsername("newUsername");
        inputUser.setColor(ColorID.C1);
        
        // Mock repository methods
        Mockito.when(userRepository.findUserById(inputUser.getId())).thenReturn(testUser);
        Mockito.when(userRepository.findByUsername(inputUser.getUsername())).thenReturn(null);
        
        // when
        userService.updateUser(inputUser);
        
        // then
        assertEquals("newUsername", testUser.getUsername());
        assertEquals("user_newUsername", testUser.getName());
        assertEquals(ColorID.C1, testUser.getColor());
        
        // Verify save was called with the updated user
        Mockito.verify(userRepository).save(testUser);
    }


    @Test
    void updateUser_duplicateUsername_throwsException() {
        // given
        User existingUser = new User();
        existingUser.setUsername("existingUsername");
        Mockito.when(userRepository.findByUsername("existingUsername")).thenReturn(existingUser);

        testUser.setUsername("existingUsername");

        // when / then
        assertThrows(ResponseStatusException.class, () -> userService.updateUser(testUser));
    }

    @Test
    void deleteUser_validId_success() {
        // given
        Mockito.when(userRepository.findUserById(testUser.getId())).thenReturn(testUser);

        // when
        userService.deleteUser(testUser.getId());

        // then
        Mockito.verify(userRepository, Mockito.times(1)).delete(testUser);
    }

    @Test
    void deleteUser_invalidId_throwsException() {
        // given
        Mockito.when(userRepository.findUserById(999L)).thenReturn(null);

        // when / then
        assertThrows(ResponseStatusException.class, () -> userService.deleteUser(999L));
    }

    @Test
    void verifyToken_validToken_success() {
        String token = "Bearer validToken";
        String result = UserService.verifyToken(token);
        assertEquals("validToken", result);
    }

    @Test
    void verifyToken_invalidToken_throwsException() {
        String token = "invalidToken";
        assertThrows(ResponseStatusException.class, () -> UserService.verifyToken(token));
    }

    @Test
    void validateToken_validToken_returnsTrue() {
        // given
        String token = "validToken";
        Mockito.when(userRepository.findByToken(token)).thenReturn(testUser);
        testUser.setStatus(UserStatus.ONLINE);

        // when
        boolean result = userService.validateToken(token);

        // then
        assertTrue(result);
    }

    @Test
    void validateToken_invalidToken_returnsFalse() {
        // given
        String token = "invalidToken";
        Mockito.when(userRepository.findByToken(token)).thenReturn(null);

        // when
        boolean result = userService.validateToken(token);

        // then
        assertFalse(result);
    }

    @Test
    void findIDforToken_validToken_returnsId() {
        // given
        String token = "validToken";
        Mockito.when(userRepository.findByToken(token)).thenReturn(testUser);

        // when
        Long result = userService.findIDforToken(token);

        // then
        assertEquals(testUser.getId(), result);
    }

    @Test
    void findIDforToken_invalidToken_returnsNull() {
        // given
        String token = "invalidToken";
        Mockito.when(userRepository.findByToken(token)).thenReturn(null);

        // when
        Long result = userService.findIDforToken(token);

        // then
        assertNull(result);
    }
}
