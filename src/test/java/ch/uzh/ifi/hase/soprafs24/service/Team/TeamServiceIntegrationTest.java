package ch.uzh.ifi.hase.soprafs24.service.Team;

import ch.uzh.ifi.hase.soprafs24.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs24.entity.Team;
import ch.uzh.ifi.hase.soprafs24.entity.User;
import ch.uzh.ifi.hase.soprafs24.repository.TeamRepository;
import ch.uzh.ifi.hase.soprafs24.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs24.service.TeamService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;




@WebAppConfiguration
@SpringBootTest
@TestPropertySource("classpath:application-dev.properties")
class TeamServiceIntegrationTest {

    @Qualifier("teamRepository")
    @Autowired
    private TeamRepository teamRepository;

    @Qualifier("userRepository")
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamService teamService;

    private Team testTeam;
    private User testUser;

    @BeforeEach
    void setup() {
        userRepository.deleteAll();
        teamRepository.deleteAll();

        // Create test user and set the creationDate field
        testUser = new User();
        testUser.setName("Test User");
        testUser.setUsername("testuser");
        testUser.setPassword("password");
        testUser.setToken("sampletoken");
        testUser.setStatus(UserStatus.ONLINE); // Ensure the status is set to a valid value
        testUser.setCreationDate(new Date()); // Set the creationDate to the current date
        testUser.setXp(0);
        testUser.setLevel(1);

        userRepository.save(testUser);

        // Create test team
        testTeam = new Team();
        testTeam.setName("Test Team");
        testTeam.setCode("ABC123");
        testTeam.setXp(0);
        testTeam.setLevel(1);

        teamRepository.save(testTeam);

        // Add user to the team
        testTeam.setMembers(new ArrayList<>());
        testTeam.getMembers().add(testUser.getId());
        teamRepository.save(testTeam);
    }


    @Test
    void createTeam_validInputs_success() {
        Team newTeam = new Team();
        newTeam.setName("New Test Team");
        newTeam.setCode("");
        newTeam.setXp(0);
        newTeam.setLevel(1);

        Team createdTeam = teamService.createTeam(testUser.getId(), newTeam);

        assertNotNull(createdTeam.getId());
        assertEquals("New Test Team", createdTeam.getName());
        assertNotNull(createdTeam.getCode()); 
        assertTrue(createdTeam.getCode().length() == 6);
    }

    @Test
    void createTeam_duplicateTeamName_throwsException() {
        Team newTeam = new Team();
        newTeam.setName(testTeam.getName());
        newTeam.setCode("NEWCODE");
        newTeam.setXp(0);
        newTeam.setLevel(1);

        assertThrows(ResponseStatusException.class, () -> teamService.createTeam(testUser.getId(), newTeam));
    }

    @Test
    void createTeam_duplicateTeamCode_throwsException() {
        Team newTeam = new Team();
        newTeam.setName("Another Test Team");
        newTeam.setCode(testTeam.getCode());
        newTeam.setXp(0);
        newTeam.setLevel(1);

        assertThrows(ResponseStatusException.class, () -> teamService.createTeam(testUser.getId(), newTeam));
    }

    @Test
    void joinTeam_validInput_teamJoined() {
        testUser.setTeamId(null);

        teamService.joinTeam(testUser.getId(), "ABC123");

        User updatedUser = userRepository.findById(testUser.getId()).orElse(null);
        assertNotNull(updatedUser);
        assertNotNull(updatedUser.getTeamId());
        assertEquals(testTeam.getId(), updatedUser.getTeamId());
    }

    @Test
    void joinTeam_invalidCode_throwsException() {
        assertThrows(ResponseStatusException.class, () -> teamService.joinTeam(testUser.getId(), "INVALID_CODE"));
    }

    @Test
    void updateTeamName_validInput_teamNameUpdated() {
        teamService.updateTeamName(testTeam.getId(), testUser.getId(), "Updated Team Name");

        Team updatedTeam = teamRepository.findTeamById(testTeam.getId());
        assertEquals("Updated Team Name", updatedTeam.getName());
    }

    @Test
    void updateTeamName_invalidTeamId_throwsException() {
        assertThrows(ResponseStatusException.class, () -> teamService.updateTeamName(999L, testUser.getId(), "Invalid Team Name"));
    }

    @Test
    void updateTeamName_userNotAuthorized_throwsException() {
        User unauthorizedUser = new User();
        unauthorizedUser.setId(2L);
        unauthorizedUser.setName("Unauthorized User");
        unauthorizedUser.setUsername("unauthorizeduser");
        unauthorizedUser.setPassword("password");
        unauthorizedUser.setToken(UUID.randomUUID().toString()); 
        unauthorizedUser.setStatus(UserStatus.ONLINE); 
        unauthorizedUser.setCreationDate(new Date()); 
        unauthorizedUser.setXp(0);
        unauthorizedUser.setLevel(1);
    
        userRepository.save(unauthorizedUser);
    
        assertThrows(ResponseStatusException.class, () -> 
            teamService.updateTeamName(testTeam.getId(), unauthorizedUser.getId(), "New Name"));
    }
    
    @Test
    void quitTeam_validInput_userQuitTeam() {
        testUser.setTeamId(testTeam.getId());
        userRepository.save(testUser);
        
        assertTrue(testTeam.getMembers().contains(testUser.getId()));

        teamService.quitTeam(testUser.getId(), testTeam.getId());

        User updatedUser = userRepository.findById(testUser.getId()).orElse(null);
        assertNotNull(updatedUser, "User should exist after quitting team");
        assertNull(updatedUser.getTeamId(), "User should not have teamId after quitting");
        
        Team updatedTeam = teamRepository.findTeamById(testTeam.getId());
        
        if (updatedTeam == null) {
            // Team was deleted because it had no members left
        } else {
            // Team still exists, verify user was removed from members list
            assertFalse(updatedTeam.getMembers().contains(testUser.getId()), 
                        "User should be removed from team members");
        }
    }


    @Test
    void quitTeam_userNotInTeam_throwsException() {
        testTeam.setMembers(new ArrayList<>());
        teamRepository.save(testTeam);

        assertThrows(ResponseStatusException.class, () -> teamService.quitTeam(testUser.getId(), testTeam.getId()));
    }

    @Test
    void quitTeam_invalidTeamId_throwsException() {
        assertThrows(ResponseStatusException.class, () -> teamService.quitTeam(testUser.getId(), 999L));
    }
}
