package ch.uzh.ifi.hase.soprafs24.service;

import ch.uzh.ifi.hase.soprafs24.entity.User;
import ch.uzh.ifi.hase.soprafs24.constant.ColorID;
import ch.uzh.ifi.hase.soprafs24.entity.Team;
import ch.uzh.ifi.hase.soprafs24.repository.TeamRepository;
import ch.uzh.ifi.hase.soprafs24.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs24.rest.dto.user.UserGetDTO;
import ch.uzh.ifi.hase.soprafs24.rest.mapper.DTOMapper;
import ch.uzh.ifi.hase.soprafs24.websocket.SocketHandler;

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
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.Objects;
import ch.uzh.ifi.hase.soprafs24.entity.Task;



/**
 * User Service
 * This class is the "worker" and responsible for all functionality related to
 * the user
 * (e.g., it creates, modifies, deletes, finds). The result will be passed back
 * to the caller.
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class TeamService {

  private final Logger log = LoggerFactory.getLogger(TeamService.class);

  private final TeamRepository teamRepository;
  private final UserRepository userRepository;
  private final UserService userService;
  private final TaskService taskService;
  private final SocketHandler socketHandler;
  private final WebSocketNotificationService notificationService;

  @Autowired
  public TeamService(@Qualifier("teamRepository") TeamRepository teamRepository,
                     @Qualifier("userRepository") UserRepository userRepository,
                     @Qualifier("userService") UserService userService, 
                     @Qualifier("taskService") TaskService taskService,
                     @Qualifier("socketHandler") SocketHandler socketHandler,
                     @Qualifier("webSocketNotificationService") WebSocketNotificationService notificationService) {
    this.notificationService = notificationService;
    this.socketHandler = socketHandler;
    this.teamRepository = teamRepository;
    this.userRepository = userRepository;
    this.userService = userService;
    this.taskService = taskService;
  }

  public Team createTeam(Long userId, Team newTeam) {  //works as the registration func as well 
    User creator = userService.getUserById(userId);
    checkUserNotInTeam(creator); //check if user is already in a team
    validateTeamName(newTeam.getName()); //check if user's team name is valid
    checkIfTeamExists(newTeam); //check if team name is already in repository

    newTeam.setXp(0);
    newTeam.setLevel(1);
    newTeam.setCode(generateUniqueTeamCode()); //generate a unique team code
    newTeam.setIsPaused(false); // Set the team to not paused by default
    if (newTeam.getMembers() == null) {
        newTeam.setMembers(new ArrayList<>());  // Initialize the list if null
    }
    newTeam.getMembers().add(userId); // Add userId to the list
    // saves the given entity but data is only persisted in the database once
    // flush() is called
    newTeam = teamRepository.save(newTeam);
    teamRepository.flush();
    
    creator.setTeamId(newTeam.getId()); // Set the teamId for the user
    creator.setColor(ColorID.C1); // Set the color to C1 (default) for the creator

    userRepository.save(creator);
    userRepository.flush();

    // Associate the socket session with the team
    socketHandler.associateSessionWithTeam(userId, newTeam.getId());
    // Notify the user about the successful creation
    log.debug("User {} created team {}", userId, newTeam.getId());

    log.debug("Created Information for Team: {}", newTeam);
    return newTeam;
  }

  public void joinTeam(Long userId, String teamCode) {
    // Find the team by teamCode
    Team team = getTeamByCode(teamCode);

    // Check if team has spots open
    if (team.getMembers().size() >= ColorID.values().length) {
      throw new ResponseStatusException(
              HttpStatus.CONFLICT,
              "Team " + team.getId() + " is full (all colours in use).");
    }

    // Find the user
    User user = userService.getUserById(userId);

    // Check if user is already in a team
    checkUserNotInTeam(user);

    // Assign a color to the user that is not already taken by another member
    user.setColor(newTeamMemberColor(team));

    // Assign the user to the team
    user.setTeamId(team.getId());

    // Add user to teamMembers list
    team.getMembers().add(user.getId());

    // Save updates
    userRepository.save(user);
    teamRepository.save(team);
    socketHandler.associateSessionWithTeam(userId, team.getId());
    userRepository.flush();
    teamRepository.flush();
    log.debug("User {} joined team {}", userId, team.getId());

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

    // Unassign all tasks the user is currently assigned to
    List<Task> assignedTasks = taskService.getTasksAssignedToUser(userId);
    for (Task task : assignedTasks) {
        taskService.unassignTask(task);
    }

    // Delete all tasks the user has created
    List<Task> createdTasks = taskService.getTasksCreatedByUser(userId);
    for (Task task : createdTasks) {
        // If the task is assigned to someone else, unassign it first
        if (task.getIsAssignedTo() != null) {
            taskService.unassignTask(task);
        }
        taskService.deleteTask(task.getId(),userId);
    }


    // Remove user from the team
    team.getMembers().remove(userId);
    user.setTeamId(null);  // Remove teamId from user
    user.setColor(null);

    // Save changes
    userRepository.save(user);
    teamRepository.save(team);

    // If the team has no more members, delete it
    if (team.getMembers().isEmpty()) {
        teamRepository.delete(team);
    }

    teamRepository.flush();
    userRepository.flush();
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

  public void pauseTeam(Long teamId, Long userId) {
    // Find the team
    Team team = getTeamById(teamId);
    // Check if user is a member of the team
    checkUserIsTeamMember(team, userId);
    // Check if the team is already paused
    if (Boolean.TRUE.equals(team.getIsPaused())) {
      // Unpause the team
      team.setIsPaused(false);
      taskService.unpauseAllTasksInTeam();
    } else {
      // Pause the team
      team.setIsPaused(true);
      taskService.pauseAllTasksInTeam();
    }
    teamRepository.save(team);
    teamRepository.flush();
  }

  public void validateTeamPaused(String userToken) {
    taskService.validateUserToken(userToken);

    User user = userRepository.findByToken(userToken);
    if (user == null) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user token");
    }

    Long teamId = user.getTeamId();
    Team team = teamRepository.findTeamById(teamId);
    if (team == null) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found");
    }
    if (Boolean.TRUE.equals(team.getIsPaused())) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Team is paused");
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

  public Team getTeamByCode(String teamCode) {
    // Logic to fetch the team by TeamCode
    Team team = teamRepository.findByCode(teamCode);
    if (team == null) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found.");
    } else {
        return team;
    }
  }

  private ColorID newTeamMemberColor(Team team) {
    // Collect colours already in use
    Set<ColorID> usedColours = team.getMembers().stream()
          .map(userService::getUserById)          // fetch each member
          .map(User::getColor)                    // their colour
          .filter(Objects::nonNull)               // skip nulls (no colour yet)
          .collect(Collectors.toSet());

    // Return the first unused colour in the enum order C1…C10
    for (ColorID candidate : ColorID.values()) {
      if (!usedColours.contains(candidate)) {
          return candidate;
      }
    }

    // If all colours are taken, conflict
    throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "All team colours are already in use for team " + team.getId());
  }
  public List<UserGetDTO> getCurrentMembersForTeam(Long teamId) {
        if (teamId == null) {
            log.warn("getCurrentMembersForTeam called with null teamId.");
            return Collections.emptyList();
        }
        Team team;
        try {
            team = getTeamById(teamId);
        } catch (ResponseStatusException e) {
            log.warn("Team not found with id {} while trying to get current members DTO.", teamId);
            return Collections.emptyList();
        }

        List<Long> memberIds = team.getMembers();

        if (memberIds == null || memberIds.isEmpty()) {
            return Collections.emptyList();
        }

        return memberIds.stream()
                .map(memberId -> {
                    try {
                        return userService.getUserById(memberId);
                    } catch (ResponseStatusException e) {
                        log.warn("User with ID {} not found while fetching members for team {}. Skipping.", memberId,
                                teamId);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .map(user -> DTOMapper.INSTANCE.convertEntityToUserGetDTO(user))
                .collect(Collectors.toList());
    }
  }

