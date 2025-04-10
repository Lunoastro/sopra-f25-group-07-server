package ch.uzh.ifi.hase.soprafs24.service;

import ch.uzh.ifi.hase.soprafs24.entity.User;
import ch.uzh.ifi.hase.soprafs24.entity.Team;
import ch.uzh.ifi.hase.soprafs24.repository.TeamRepository;
import ch.uzh.ifi.hase.soprafs24.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;


import java.util.List;
import java.util.UUID;
import java.util.ArrayList;



/**
 * User Service
 * This class is the "worker" and responsible for all functionality related to
 * the user
 * (e.g., it creates, modifies, deletes, finds). The result will be passed back
 * to the caller.
 */
@Service
@Transactional
public class TeamService {

  private final Logger log = LoggerFactory.getLogger(TeamService.class);

  private final TeamRepository teamRepository;
  private final UserRepository userRepository;
  private final UserService userService;

  @Autowired
  public TeamService(@Qualifier("teamRepository") TeamRepository teamRepository,
                     @Qualifier("userRepository") UserRepository userRepository,
                     @Qualifier("userService") UserService userService) {
    this.teamRepository = teamRepository;
    this.userRepository = userRepository;
    this.userService = userService;
  }

  public Team createTeam(Long userId, Team newTeam) {  //works as the registration func as well 
    validateTeamName(newTeam.getName()); //check if user's team name is valid
    checkIfTeamExists(newTeam); //check if team name is already in repository

    newTeam.setXp(0);
    newTeam.setLevel(1);
    newTeam.setCode(generateUniqueTeamCode()); //generate a unique team code
    if (newTeam.getMembers() == null) {
        newTeam.setMembers(new ArrayList<>());  // Initialize the list if null
    }
    newTeam.getMembers().add(userId); // Add userId to the list
    // saves the given entity but data is only persisted in the database once
    // flush() is called
    newTeam = teamRepository.save(newTeam);
    teamRepository.flush();

    log.debug("Created Information for Team: {}", newTeam);
    return newTeam;
  }

  public void joinTeam(Long userId, String teamCode) {
    // Find the team by teamCode
    Team team = getTeamByCode(teamCode);

    // Find the user
    User user = userService.getUserById(userId);

    // Check if user is already in a team
    checkUserNotInTeam(user);

    // Assign the user to the team
    user.setTeamId(team.getId());

    // Add user to teamMembers list
    team.getMembers().add(user.getId());

    // Save updates
    userRepository.save(user);
    teamRepository.save(team);
    userRepository.flush();
    teamRepository.flush();
  }

  public void updateTeamName(Long teamId, Long userId, String newTeamName) {
    // Check if team exists
    Team team = getTeamById(teamId);

    // Check if user is a member of the team
    checkUserIsTeamMember(team, userId);

    // Check if team name is not empty or null
    validateTeamName(newTeamName);

    // Ensure new team name is unique
    checkIfTeamNameExists(newTeamName);

    // Update the team name
    team.setName(newTeamName);
    teamRepository.save(team);
    teamRepository.flush();
  }

  public void quitTeam(Long userId, Long teamId) {
    // Find the team
    Team team = getTeamById(teamId);
    // Find the user
    User user = userService.getUserById(userId);
    // Ensure the user is actually in this team
    checkUserIsTeamMember(team, userId);

    // Remove user from the team
    team.getMembers().remove(userId);
    user.setId(null);  // Remove teamId from user

    // Save changes
    userRepository.save(user);
    teamRepository.save(team);

    // If the team has no more members, delete it
    if (team.getMembers().isEmpty()) {
        teamRepository.delete(team);
    }

    teamRepository.flush();
  }

  public List<Long> getUsersByTeamId(Long teamId) {
    Team team = this.teamRepository.findById(teamId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found."));
    return team.getMembers();
  }

  public Team getTeamById(Long teamId) {
    // Logic to fetch the team by TeamId
    Team team = teamRepository.findTeamById(teamId);
    if (team == null) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found.");
    } else {
        return team;
    }
  }
  
  /**
   * This is a helper method that will check the uniqueness criteria of the
   * username and the name
   * defined in the User entity. The method will do nothing if the input is unique
   * and throw an error otherwise.
   *
   * @param teamToBeCreated
   * @throws org.springframework.web.server.ResponseStatusException
   * @see User
   */

  private void checkIfTeamExists(Team teamToBeCreated) {
    if (teamRepository.findByName(teamToBeCreated.getName().trim()) != null) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Team name already exists.");
    }
    if (teamRepository.findByCode(teamToBeCreated.getCode()) != null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Team code already exists.");
    }
  }

  private void validateTeamName(String teamName) {
    if (teamName == null || teamName.trim().isEmpty()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Team name cannot be empty.");
    }
  }

  private void checkIfTeamNameExists(String teamName) {
    if (teamRepository.findByName(teamName) != null) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "A team with this name already exists.");
    }
  }

  private void checkUserNotInTeam(User user) {
    if (user.getTeamId() != null) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already in a team.");
    }
  }

  private void checkUserIsTeamMember(Team team, Long userId) {
    if (!team.getMembers().contains(userId)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this team.");
    }
  }

  private String generateUniqueTeamCode() {
    String teamCode;
    do {
        teamCode = UUID.randomUUID().toString().substring(0, 6);
    } while (teamRepository.findByCode(teamCode) != null); //if team cannot be found via generated code, we return the unique code
    return teamCode;
  }

  private Team getTeamByCode(String teamCode) {
    // Logic to fetch the team by TeamCode
    Team team = teamRepository.findByCode(teamCode);
    if (team == null) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found.");
    } else {
        return team;
    }
  }
}