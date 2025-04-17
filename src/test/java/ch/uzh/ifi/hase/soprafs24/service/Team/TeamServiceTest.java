package ch.uzh.ifi.hase.soprafs24.service.Team;

import ch.uzh.ifi.hase.soprafs24.entity.Team;
import ch.uzh.ifi.hase.soprafs24.entity.User;
import ch.uzh.ifi.hase.soprafs24.repository.TeamRepository;
import ch.uzh.ifi.hase.soprafs24.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs24.service.TeamService;
import ch.uzh.ifi.hase.soprafs24.service.UserService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

class TeamServiceTest {

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserService userService; // Inject the UserService

    @InjectMocks
    private TeamService teamService;

    private Team testTeam;
    private User testUser;

    @BeforeEach
    void setup() {
    MockitoAnnotations.openMocks(this);

    testTeam = new Team();
    testTeam.setId(1L);
    testTeam.setName("Test Team");
    testTeam.setCode("ABC123");
    testTeam.setXp(0);
    testTeam.setLevel(1);

    List<Long> members = new ArrayList<>();
    members.add(1L);
    testTeam.setMembers(members);

    testUser = new User();
    testUser.setId(1L);
    testUser.setName("Test User");
    testUser.setUsername("testuser");
    testUser.setPassword("password");

    Mockito.when(teamRepository.save(Mockito.any())).thenReturn(testTeam);
    Mockito.when(teamRepository.findTeamById(Mockito.anyLong())).thenReturn(testTeam);
    Mockito.when(userRepository.findById(Mockito.anyLong())).thenReturn(java.util.Optional.of(testUser));
    Mockito.when(userService.getUserById(Mockito.anyLong())).thenReturn(testUser);
}


    @Test
    void createTeam_validInputs_success() {
        Mockito.when(teamRepository.findByName(Mockito.anyString())).thenReturn(null);
        Mockito.when(teamRepository.findByCode(Mockito.anyString())).thenReturn(null);

        Team createdTeam = teamService.createTeam(testUser.getId(), testTeam);

        Mockito.verify(teamRepository, Mockito.times(1)).save(Mockito.any());

        assertEquals(testTeam.getId(), createdTeam.getId());
        assertEquals(testTeam.getName(), createdTeam.getName());
        assertEquals(testTeam.getCode(), createdTeam.getCode());
        assertEquals(testTeam.getLevel(), createdTeam.getLevel());
        assertEquals(testTeam.getXp(), createdTeam.getXp());
    }

    @Test
    void createTeam_duplicateTeamName_throwsException() {
        Mockito.when(teamRepository.findByName(Mockito.anyString())).thenReturn(testTeam);

        assertThrows(ResponseStatusException.class, () -> teamService.createTeam(testUser.getId(), testTeam));
    }

    @Test
    void createTeam_duplicateTeamCode_throwsException() {
        Mockito.when(teamRepository.findByCode(Mockito.anyString())).thenReturn(testTeam);

        assertThrows(ResponseStatusException.class, () -> teamService.createTeam(testUser.getId(), testTeam));
    }

    @Test
    void joinTeam_validInput_teamJoined() {
        testUser.setTeamId(null);

        Mockito.when(teamRepository.findByCode(Mockito.anyString())).thenReturn(testTeam);
        Mockito.when(userService.getUserById(Mockito.anyLong())).thenReturn(testUser);

        teamService.joinTeam(testUser.getId(), "ABC123");

        Mockito.verify(teamRepository, Mockito.times(1)).findByCode(Mockito.anyString());
    }


    @Test
    void joinTeam_invalidCode_throwsException() {
        Mockito.when(teamRepository.findByCode(Mockito.anyString())).thenReturn(null);

        assertThrows(ResponseStatusException.class, () -> teamService.joinTeam(testUser.getId(), "INVALID_CODE"));
    }

    @Test
    void updateTeamName_validInput_teamNameUpdated() {
        Mockito.when(teamRepository.findTeamById(Mockito.anyLong())).thenReturn(testTeam);

        teamService.updateTeamName(testTeam.getId(), testUser.getId(), "New Team Name");

        assertEquals("New Team Name", testTeam.getName());
        Mockito.verify(teamRepository, Mockito.times(1)).save(Mockito.any());
    }

    @Test
    void updateTeamName_invalidTeamId_throwsException() {
        Mockito.when(teamRepository.findTeamById(Mockito.anyLong())).thenReturn(null);

        assertThrows(ResponseStatusException.class, () -> teamService.updateTeamName(999L, testUser.getId(), "New Team Name"));
    }

    @Test
    void updateTeamName_userNotAuthorized_throwsException() {
        Mockito.when(teamRepository.findTeamById(Mockito.anyLong())).thenReturn(testTeam);
        Mockito.when(userRepository.findById(Mockito.anyLong())).thenReturn(java.util.Optional.of(new User()));

        assertThrows(ResponseStatusException.class, () -> teamService.updateTeamName(testTeam.getId(), 999L, "New Team Name"));
    }

    @Test
    void quitTeam_validInput_userQuitTeam() {
        Mockito.when(teamRepository.findTeamById(Mockito.anyLong())).thenReturn(testTeam);

        teamService.quitTeam(testUser.getId(), testTeam.getId());

        Mockito.verify(teamRepository, Mockito.times(1)).save(Mockito.any());
    }

    @Test
    void quitTeam_userNotInTeam_throwsException() {
        testTeam.setMembers(new ArrayList<>());

        Mockito.when(teamRepository.findTeamById(Mockito.anyLong())).thenReturn(testTeam);

        assertThrows(ResponseStatusException.class, () -> teamService.quitTeam(testUser.getId(), 999L));
    }

    @Test
    void quitTeam_invalidTeamId_throwsException() {
        Mockito.when(teamRepository.findTeamById(Mockito.anyLong())).thenReturn(null);

        assertThrows(ResponseStatusException.class, () -> teamService.quitTeam(testUser.getId(), 999L));
    }
}
