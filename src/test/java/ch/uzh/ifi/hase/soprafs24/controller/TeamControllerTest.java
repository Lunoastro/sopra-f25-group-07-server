package ch.uzh.ifi.hase.soprafs24.controller;

import ch.uzh.ifi.hase.soprafs24.entity.Team;
import ch.uzh.ifi.hase.soprafs24.rest.dto.team.TeamGetDTO;
import ch.uzh.ifi.hase.soprafs24.rest.dto.team.TeamPostDTO;
import ch.uzh.ifi.hase.soprafs24.service.TeamService;
import ch.uzh.ifi.hase.soprafs24.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import ch.uzh.ifi.hase.soprafs24.entity.User;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
 class TeamControllerTest {

    @Autowired
    private TeamController teamController;

    @MockBean
    private TeamService teamService;

    @MockBean
    private UserService userService;

    private MockMvc mockMvc;

    private Team team;
    private TeamPostDTO teamPostDTO;
    private TeamGetDTO teamGetDTO;

    @BeforeEach
     void setup() {
        // Setup MockMvc
        mockMvc = MockMvcBuilders.standaloneSetup(teamController).build();

        // Create mock team data
        team = new Team();
        team.setId(1L);
        team.setName("Test Team");
        team.setCode("ABC123");
        team.setXP(0);
        team.setLevel(1);

        teamPostDTO = new TeamPostDTO();
        teamPostDTO.setName("Test Team");

        teamGetDTO = new TeamGetDTO();
        teamGetDTO.setId(1L);
        teamGetDTO.setName("Test Team");
        teamGetDTO.setCode("ABC123");
        teamGetDTO.setXp(0);
        teamGetDTO.setLevel(1);
    }

    @Test
     void POST_createTeam_validInput_teamCreated() throws Exception {
        // Given
        String authorizationHeader = "Bearer valid_token";
        when(userService.validateToken(anyString())).thenReturn(true);
        when(userService.findIDforToken(anyString())).thenReturn(1L);
        when(teamService.createTeam(anyLong(), any(Team.class))).thenReturn(team);

        // When & Then
        mockMvc.perform(MockMvcRequestBuilders.post("/teams")
                .header("Authorization", authorizationHeader)
                .contentType("application/json")
                .content("{ \"name\": \"Test Team\" }"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Test Team"))
                .andExpect(jsonPath("$.code").value("ABC123"))
                .andExpect(jsonPath("$.level").value(1));
    }

    @Test
     void POST_failedCreateTeam_invalidInput_duplicateTeamName_teamNotCreated() throws Exception {
    // Given
        String authorizationHeader = "Bearer valid_token";
        when(userService.validateToken(anyString())).thenReturn(true);
        when(userService.findIDforToken(anyString())).thenReturn(1L);
    
    // Simulate a conflict when trying to create a team with an already existing name
        when(teamService.createTeam(anyLong(), any(Team.class)))
            .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "add Team failed because team name already exists"));

    // Create a DTO for the team creation request
        String teamJson = "{ \"name\": \"Test Team\" }";

    // When & Then
        mockMvc.perform(MockMvcRequestBuilders.post("/teams")
                .header("Authorization", authorizationHeader)
                .contentType("application/json")
                .content(teamJson))
                .andExpect(status().isConflict()); // Expect a 409 Conflict status
}


    @Test
     void POST_joinTeam_validInput_teamJoined() throws Exception {
        // Given
        String authorizationHeader = "Bearer valid_token";
        when(userService.validateToken(anyString())).thenReturn(true);
        when(userService.findIDforToken(anyString())).thenReturn(1L);
        doNothing().when(teamService).joinTeam(anyLong(), anyString());

        // When & Then
        mockMvc.perform(MockMvcRequestBuilders.post("/teams/join")
                .param("code", "ABC123")
                .header("Authorization", authorizationHeader))
                .andExpect(status().isCreated());

        // Verify that the teamService.joinTeam method was called
        verify(teamService, times(1)).joinTeam(anyLong(), eq("ABC123"));
    }

    @Test
     void POST_failedJoinTeam_invalidInput_teamNotFound() throws Exception {
        // Given
        String authorizationHeader = "Bearer valid_token";
        when(userService.validateToken(anyString())).thenReturn(true);
        when(userService.findIDforToken(anyString())).thenReturn(1L);
        
        // Simulate that the team does not exist (throws a not found exception)
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Team with this team code does not exist"))
                .when(teamService).joinTeam(anyLong(), anyString());
    
        // When & Then
        mockMvc.perform(MockMvcRequestBuilders.post("/teams/join")  // Assuming team with
                    .param("code", "NONEXISTENT")
                    .header("Authorization", authorizationHeader))
                .andExpect(status().isNotFound());  // Expect a 404 Not Found status
    
        // Verify that the teamService.joinTeam method was called
        verify(teamService, times(1)).joinTeam(anyLong(), eq("NONEXISTENT"));
    }
    

    @Test
     void GET_getTeamById_validTeamId_teamReturned() throws Exception {
        // Given
        String authorizationHeader = "Bearer valid_token";
        when(userService.validateToken(anyString())).thenReturn(true);
        when(userService.findIDforToken(anyString())).thenReturn(1L);
        when(teamService.getTeamById(anyLong())).thenReturn(team);

        // When & Then
        mockMvc.perform(MockMvcRequestBuilders.get("/teams/1")
                .header("Authorization", authorizationHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Test Team"));
    }

    @Test
     void GET_failedGetTeamById_invalidTeamId_teamNotFound() throws Exception {
        // Given
        String authorizationHeader = "Bearer valid_token";
        when(userService.validateToken(anyString())).thenReturn(true);
        when(userService.findIDforToken(anyString())).thenReturn(1L);
        
        // Simulate that the team does not exist (throws a not found exception)
        when(teamService.getTeamById(anyLong())).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));
    
        // When & Then
        mockMvc.perform(MockMvcRequestBuilders.get("/teams/999")  // Assuming team with ID 999 doesn't exist
                    .header("Authorization", authorizationHeader))
                .andExpect(status().isNotFound());  // Expect a 404 Not Found status
    }

    @Test
     void GET_getUsersByTeam_validTeamId_usersReturned() throws Exception {
    // Given
        String authorizationHeader = "Bearer valid_token";
        when(userService.validateToken(anyString())).thenReturn(true);
        when(userService.findIDforToken(anyString())).thenReturn(1L);
        when(teamService.getUsersByTeamId(anyLong())).thenReturn(List.of(1L, 2L));
    
    // Mock userService.getUserById to return dummy User objects
        User mockUser1 = new User();
        mockUser1.setId(1L);
        User mockUser2 = new User();
        mockUser2.setId(2L);
        when(userService.getUserById(1L)).thenReturn(mockUser1);
        when(userService.getUserById(2L)).thenReturn(mockUser2);
    
    // When & Then
        mockMvc.perform(MockMvcRequestBuilders.get("/teams/1/users")
                .header("Authorization", authorizationHeader))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(1))  // Expect the 'id' field in the first DTO to be 1
            .andExpect(jsonPath("$[1].id").value(2));  // Expect the 'id' field in the second DTO to be 2
    }

    @Test
     void GET_getUsersByTeam_invalidTeamId_teamNotFound() throws Exception {
        // Given
        String authorizationHeader = "Bearer valid_token";
        when(userService.validateToken(anyString())).thenReturn(true);
        when(userService.findIDforToken(anyString())).thenReturn(1L);
        when(teamService.getUsersByTeamId(anyLong())).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));
    
        // When & Then
        mockMvc.perform(MockMvcRequestBuilders.get("/teams/999/users")
                .header("Authorization", authorizationHeader))
            .andExpect(status().isNotFound());
    
    }
    

    @Test
     void PUT_updateTeamName_validInput_teamNameUpdated() throws Exception {
        // Given
        String authorizationHeader = "Bearer valid_token";
        when(userService.validateToken(anyString())).thenReturn(true);
        when(userService.findIDforToken(anyString())).thenReturn(1L);
        doNothing().when(teamService).updateTeamName(anyLong(), anyLong(), anyString());

        // When & Then
        mockMvc.perform(MockMvcRequestBuilders.put("/teams/1")
                .header("Authorization", authorizationHeader)
                .contentType("application/json")
                .content("{ \"name\": \"New Team Name\" }"))
                .andExpect(status().isNoContent());

        // Verify that the teamService.updateTeamName method was called
        verify(teamService, times(1)).updateTeamName(anyLong(), anyLong(), eq("New Team Name"));
    }

    @Test
     void PUT_updateTeamName_invalidTeamId_teamNotFound() throws Exception {
        // Given
        String authorizationHeader = "Bearer valid_token";
        when(userService.validateToken(anyString())).thenReturn(true);
        when(userService.findIDforToken(anyString())).thenReturn(1L);
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"))
                .when(teamService).updateTeamName(anyLong(), anyLong(), anyString());
    
        // When & Then
        mockMvc.perform(MockMvcRequestBuilders.put("/teams/999")
                .header("Authorization", authorizationHeader)
                .contentType("application/json")
                .content("{ \"name\": \"New Team Name\" }"))
            .andExpect(status().isNotFound());
    
        // Verify that the teamService.updateTeamName method was called
        verify(teamService, times(1)).updateTeamName(anyLong(), anyLong(), eq("New Team Name"));
    }
    

    @Test
     void DELETE_quitTeam_validInput_userQuitTeam() throws Exception {
        // Given
        String authorizationHeader = "Bearer valid_token";
        when(userService.validateToken(anyString())).thenReturn(true);
        when(userService.findIDforToken(anyString())).thenReturn(1L);
        doNothing().when(teamService).quitTeam(anyLong(), anyLong());

        // When & Then
        mockMvc.perform(MockMvcRequestBuilders.delete("/teams/1/users/1")
                .header("Authorization", authorizationHeader))
                .andExpect(status().isNoContent());

        // Verify that the teamService.quitTeam method was called
        verify(teamService, times(1)).quitTeam(anyLong(), eq(1L));
    }


    @Test
     void DELETE_quitTeam_invalidTeamId_teamNotFound() throws Exception {
        // Given
        String authorizationHeader = "Bearer valid_token";
        when(userService.validateToken(anyString())).thenReturn(true);
        when(userService.findIDforToken(anyString())).thenReturn(1L);
        
        // Ensure that the error is thrown when trying to quit a non-existent team
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"))
                .when(teamService).quitTeam(eq(1L), anyLong()); // Allow any teamId
    
        // When & Then
        mockMvc.perform(MockMvcRequestBuilders.delete("/teams/999/users/1")
                .header("Authorization", authorizationHeader))
            .andExpect(status().isNotFound());
    
        // Verify the method was called with the correct userId, but any teamId
        verify(teamService, times(1)).quitTeam(eq(1L), anyLong());
    }
    
    
}
