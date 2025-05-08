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
import java.util.Optional;

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
        testUser.setStatus(UserStatus.ONLINE);
        testUser.setXp(0);
        testUser.setLevel(1);

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

    @Test
    void addExperiencePoints_userFound_success() {
        // given
        Mockito.when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        
        // when
        userService.addExperiencePoints(testUser.getId(), 50);
        
        // then
        assertEquals(50, testUser.getXp());
        assertEquals(1, testUser.getLevel()); // Should still be level 1
        Mockito.verify(userRepository, Mockito.times(1)).save(testUser);
        Mockito.verify(userRepository, Mockito.times(1)).flush();
    }

    @Test
    void addExperiencePoints_userNotFound_throwsException() {
        // given
        Long nonExistentUserId = 999L;
        Mockito.when(userRepository.findById(nonExistentUserId)).thenReturn(Optional.empty());
        
        // when/then
        assertThrows(ResponseStatusException.class, () -> userService.addExperiencePoints(nonExistentUserId, 50));
    }

    @Test
    void addExperiencePoints_enoughForLevelUp_success() {
        // given
        Mockito.when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        
        // Level 2 requires 100*2^1.5 = ~283 XP
        int xpForLevelUp = 300;
        
        // when
        userService.addExperiencePoints(testUser.getId(), xpForLevelUp);
        
        // then
        assertEquals(xpForLevelUp, testUser.getXp());
        assertEquals(2, testUser.getLevel()); // Should level up to 2
        Mockito.verify(userRepository, Mockito.times(1)).save(testUser);
        Mockito.verify(userRepository, Mockito.times(1)).flush();
    }

    @Test
    void addExperiencePoints_enoughForMultipleLevelUps_success() {
        // given
        Mockito.when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        
        // Add enough XP for multiple level ups (to level 4)
        // Level 2: ~283 XP
        // Level 3: ~520 XP
        // Level 4: ~800 XP
        int xpForMultipleLevelUps = 900;
        
        // when
        userService.addExperiencePoints(testUser.getId(), xpForMultipleLevelUps);
        
        // then
        assertEquals(xpForMultipleLevelUps, testUser.getXp());
        assertEquals(4, testUser.getLevel()); // Should level up to 4
        Mockito.verify(userRepository, Mockito.times(1)).save(testUser);
        Mockito.verify(userRepository, Mockito.times(1)).flush();
    }

    @Test
    void deductExperiencePoints_userFound_success() {
        // given
        testUser.setXp(100); // Start with some XP
        Mockito.when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        
        // when
        userService.deductExperiencePoints(testUser.getId(), 50);
        
        // then
        assertEquals(50, testUser.getXp());
        assertEquals(1, testUser.getLevel()); // Level should remain the same
        Mockito.verify(userRepository, Mockito.times(1)).save(testUser);
        Mockito.verify(userRepository, Mockito.times(1)).flush();
    }

    @Test
    void deductExperiencePoints_userNotFound_throwsException() {
        // given
        Long nonExistentUserId = 999L;
        Mockito.when(userRepository.findById(nonExistentUserId)).thenReturn(Optional.empty());
        
        // when/then
        assertThrows(ResponseStatusException.class, () -> userService.deductExperiencePoints(nonExistentUserId, 50));
    }

    @Test
    void deductExperiencePoints_preventNegativeXp_success() {
        // given
        testUser.setXp(30);
        Mockito.when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        
        // when - try to deduct more than available
        userService.deductExperiencePoints(testUser.getId(), 50);
        
        // then - should be capped at 0
        assertEquals(0, testUser.getXp());
        assertEquals(1, testUser.getLevel()); // Minimum level
        Mockito.verify(userRepository, Mockito.times(1)).save(testUser);
        Mockito.verify(userRepository, Mockito.times(1)).flush();
    }

    @Test
    void deductExperiencePoints_causesLevelDown_success() {
        // given
        // Set user to level 3 with just enough XP for level 3
        testUser.setLevel(3);
        int xpForLevel3 = 520; // Approximate based on the formula
        testUser.setXp(xpForLevel3);
        
        Mockito.when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        
        // when - deduct enough to drop to level 2
        userService.deductExperiencePoints(testUser.getId(), 300);
        
        assertTrue(testUser.getXp() < xpForLevel3);
        assertEquals(1, testUser.getLevel()); 
        Mockito.verify(userRepository, Mockito.times(1)).save(testUser);
        Mockito.verify(userRepository, Mockito.times(1)).flush();
    }

    @Test
    void deductExperiencePoints_causeMultipleLevelDowns_success() {
        // given
        // Set user to level 4 with just enough XP for level 4
        testUser.setLevel(4);
        int xpForLevel4 = 800; // Approximate based on the formula
        testUser.setXp(xpForLevel4);
        
        Mockito.when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        
        // when - deduct enough XP to drop to level 1
        userService.deductExperiencePoints(testUser.getId(), 750);
        
        // then
        assertEquals(xpForLevel4 - 750, testUser.getXp());
        assertEquals(1, testUser.getLevel()); // Should drop to level 1
        Mockito.verify(userRepository, Mockito.times(1)).save(testUser);
        Mockito.verify(userRepository, Mockito.times(1)).flush();
    }

    @Test
    void deductExperiencePoints_levelCannotGoBelowOne_success() {
        // given
        testUser.setXp(50);
        testUser.setLevel(1);
        
        Mockito.when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        
        // when - deduct all XP
        userService.deductExperiencePoints(testUser.getId(), 50);
        
        // then
        assertEquals(0, testUser.getXp());
        assertEquals(1, testUser.getLevel()); // Level should stay at 1
        Mockito.verify(userRepository, Mockito.times(1)).save(testUser);
        Mockito.verify(userRepository, Mockito.times(1)).flush();
    }

    @Test
    void getXpForLevel_calculatesCorrectly() {
        // This is testing a private method through reflection
        try {
            java.lang.reflect.Method method = UserService.class.getDeclaredMethod("getXpForLevel", int.class);
            method.setAccessible(true);
            
            // Calculate expected values using the formula: baseXP * level^exponent
            int baseXP = 100;
            double exponent = 1.5;
            
            // Test for multiple levels
            for (int level = 1; level <= 5; level++) {
                int expected = (int)(baseXP * Math.pow(level, exponent));
                int actual = (int) method.invoke(userService, level);
                assertEquals(expected, actual, "XP calculation for level " + level + " is incorrect");
            }
        } catch (Exception e) {
            fail("Exception while testing private method: " + e.getMessage());
        }
    }
}
