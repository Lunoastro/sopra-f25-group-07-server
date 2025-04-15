package ch.uzh.ifi.hase.soprafs24.controller;

import ch.uzh.ifi.hase.soprafs24.entity.User;
import ch.uzh.ifi.hase.soprafs24.rest.dto.team.TeamGetDTO;
import ch.uzh.ifi.hase.soprafs24.rest.dto.team.TeamPostDTO;
import ch.uzh.ifi.hase.soprafs24.rest.dto.team.TeamPutDTO;
import ch.uzh.ifi.hase.soprafs24.rest.dto.user.UserGetDTO;
import ch.uzh.ifi.hase.soprafs24.service.UserService;

import ch.uzh.ifi.hase.soprafs24.entity.Team;
import ch.uzh.ifi.hase.soprafs24.service.TeamService;

import ch.uzh.ifi.hase.soprafs24.rest.mapper.DTOMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;



import java.util.List;

/**
 * User Controller
 * This class is responsible for handling all REST request that are related to
 * the user.
 * The controller will receive the request and delegate the execution to the
 * UserService and finally return the result.
 */
@RestController
public class TeamController {

  private final TeamService teamService;

  private final UserService userService;

  TeamController(TeamService teamService, UserService userService) {
    this.teamService = teamService;
    this.userService = userService;
  }

  @PostMapping("/teams")
  @ResponseStatus(HttpStatus.CREATED)
  
  public TeamGetDTO createTeam(@RequestBody TeamPostDTO teamPostDTO, @RequestHeader("Authorization") String authorizationHeader) {
    // convert API user to internal representation
    String token = validateAuthorizationHeader(authorizationHeader);

    if (!userService.validateToken(token)) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized: Invalid token.");
    }

    Long userId = userService.findIDforToken(token);
    if (userId == null) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found.");
    }
    // Convert DTO to Team entity
    Team newTeam = DTOMapper.INSTANCE.convertTeamPostDTOtoEntity(teamPostDTO);
    
    // create user
    Team createdTeam = teamService.createTeam(userId, newTeam);    // convert internal representation of user back to API
    return DTOMapper.INSTANCE.convertEntityToTeamGetDTO(createdTeam);
  }

  @PostMapping("/teams/join")
  @ResponseStatus(HttpStatus.CREATED)
  
  public void joinTeam(@RequestParam String code, @RequestHeader("Authorization") String authorizationHeader) {
    // Validate the token
    String token = validateAuthorizationHeader(authorizationHeader);

    if (!userService.validateToken(token)) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized: Invalid token.");
    }

    // Get user ID from token
    Long userId = userService.findIDforToken(token);
    if (userId == null) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found.");
    }

    // Process team joining
    teamService.joinTeam(userId, code);
  }

  @GetMapping("/teams/{teamId}")
  @ResponseStatus(HttpStatus.OK)
  
  public TeamGetDTO getTeamById(@PathVariable Long teamId, @RequestHeader("Authorization") String authorizationHeader) {
    // Extract and validate the token
    String token = validateAuthorizationHeader(authorizationHeader);

    if (!userService.validateToken(token)) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized: Invalid token.");
    }

    // Fetch all users
    Team team = teamService.getTeamById(teamId);

    // Convert each user to the API representation
    return DTOMapper.INSTANCE.convertEntityToTeamGetDTO(team);
  }

  @GetMapping("/teams/{teamId}/users")
  @ResponseStatus(HttpStatus.OK)
  
  public List<UserGetDTO> getUsersByTeam(@PathVariable Long teamId, @RequestHeader("Authorization") String authorizationHeader) {
    // Extract and validate the token
    String token = validateAuthorizationHeader(authorizationHeader);

    if (!userService.validateToken(token)) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized: Invalid token.");
    }

    // Fetch user IDs from the team
    List<Long> userIds = teamService.getUsersByTeamId(teamId);

    // Fetch user entities from the user IDs
    List<User> users = userIds.stream()
                .map(userService::getUserById) // Convert each userId to a user entity using getUserById
                .toList();

    // Fetch userGetDTOs from the user entities
    return users.stream()
                .map(DTOMapper.INSTANCE::convertEntityToUserGetDTO) // Convert each user to a UserGetDTO using DTOMapper
                .toList();
  }

  @PutMapping("/teams/{teamId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void editTeamName(@PathVariable Long teamId, @RequestBody TeamPutDTO teamPutDTO, 
                         @RequestHeader("Authorization") String authorizationHeader) {    

    // Validate token
    String token = validateAuthorizationHeader(authorizationHeader);
    if (!userService.validateToken(token)) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized: Invalid token.");
    }

    // Get authenticated user ID
    Long userId = userService.findIDforToken(token);
    if (userId == null) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found.");
    }

    // Extract new team name from DTO
    String newTeamName = teamPutDTO.getName();

    // Call service to update team name
    teamService.updateTeamName(teamId, userId, newTeamName);
  }

  @PutMapping("/teams/{teamId}/paused")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void pauseTeam(@PathVariable Long teamId, @RequestHeader("Authorization") String authorizationHeader) {
    
    throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Pause team functionality not implemented yet.");

  }

  @DeleteMapping("/teams/{teamId}/users/{userId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void userQuit(@PathVariable Long teamId, @PathVariable Long userId, 
                     @RequestHeader("Authorization") String authorizationHeader) {

    String token = validateAuthorizationHeader(authorizationHeader);
    
    if (!userService.validateToken(token)) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized: Invalid token.");
    }
  
    Long authenticatedUserId = userService.findIDforToken(token);

    // Ensure the user can only quit their own team
    if (!authenticatedUserId.equals(userId)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden: You can only quit your own team.");
    }
    teamService.quitTeam(userId, teamId);
  }

  private String validateAuthorizationHeader(String authorizationHeader) {
    if (authorizationHeader == null || authorizationHeader.trim().isEmpty() || !authorizationHeader.startsWith("Bearer ")) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized: Missing or invalid Authorization header.");
    }
    return authorizationHeader.substring(7);  // Remove "Bearer " prefix
  }
}

// I wrote all the endpoints already, need to change content to match team functions and team services