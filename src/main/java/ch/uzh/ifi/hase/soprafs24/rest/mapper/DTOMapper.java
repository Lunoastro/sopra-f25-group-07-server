package ch.uzh.ifi.hase.soprafs24.rest.mapper;

import ch.uzh.ifi.hase.soprafs24.entity.User;
import ch.uzh.ifi.hase.soprafs24.entity.Team;
import ch.uzh.ifi.hase.soprafs24.entity.Task;
import ch.uzh.ifi.hase.soprafs24.rest.dto.task.TaskDeleteDTO;
import ch.uzh.ifi.hase.soprafs24.rest.dto.task.TaskGetDTO;
import ch.uzh.ifi.hase.soprafs24.rest.dto.task.TaskPostDTO;
import ch.uzh.ifi.hase.soprafs24.rest.dto.task.TaskPutDTO;
import ch.uzh.ifi.hase.soprafs24.rest.dto.team.TeamDeleteDTO;
import ch.uzh.ifi.hase.soprafs24.rest.dto.team.TeamGetDTO;
import ch.uzh.ifi.hase.soprafs24.rest.dto.team.TeamPostDTO;
import ch.uzh.ifi.hase.soprafs24.rest.dto.team.TeamPutDTO;
import ch.uzh.ifi.hase.soprafs24.rest.dto.user.UserDeleteDTO;
import ch.uzh.ifi.hase.soprafs24.rest.dto.user.UserGetDTO;
import ch.uzh.ifi.hase.soprafs24.rest.dto.user.UserPostDTO;
import ch.uzh.ifi.hase.soprafs24.rest.dto.user.UserPutDTO;

import org.mapstruct.*;
import org.mapstruct.factory.Mappers;

/**
 * DTOMapper
 * This class is responsible for generating classes that will automatically
 * transform/map the internal representation
 * of an entity (e.g., the User) to the external/API representation (e.g.,
 * UserGetDTO for getting, UserPostDTO for creating)
 * and vice versa.
 * Additional mappers can be defined for new entities.
 * Always created one mapper for getting information (GET) and one mapper for
 * creating information (POST).
 */
@Mapper
public interface DTOMapper {

  DTOMapper INSTANCE = Mappers.getMapper(DTOMapper.class);

  @Mapping(source = "name", target = "name")
  @Mapping(source = "username", target = "username")
  @Mapping(source = "password", target = "password")
  User convertUserPostDTOtoEntity(UserPostDTO userPostDTO);

  @Mapping(source = "id", target = "id")
  @Mapping(source = "name", target = "name")
  @Mapping(source = "username", target = "username")
  @Mapping(source = "color", target = "color")
  @Mapping(source = "token", target = "token")
  @Mapping(source = "status", target = "status")
  @Mapping(source = "creationDate", target = "creationDate")
  @Mapping(source = "birthDate", target = "birthDate")
  UserGetDTO convertEntityToUserGetDTO(User user);

  @Mapping(source = "username", target = "username")
  @Mapping(source = "color", target = "color")
  @Mapping(source = "id", target = "id")
  @Mapping(source = "birthDate", target = "birthDate")
  User convertUserPutDTOtoEntity(UserPutDTO userPutDTO);

  @Mapping(source = "id", target = "id")
  User convertUserDeleteDTOtoEntity(UserDeleteDTO userDeleteDTO);


  @Mapping(source = "name", target = "name")
  @Mapping(source = "code", target = "code")
  Team convertTeamPostDTOtoEntity(TeamPostDTO teamPostDTO);

  @Mapping(source = "id", target = "id")
  @Mapping(source = "name", target = "name")
  @Mapping(source = "xp", target = "xp")
  @Mapping(source = "level", target = "level")
  @Mapping(source = "code", target = "code")
  @Mapping(source = "members", target = "members")
  @Mapping(source = "tasks", target = "tasks")
  TeamGetDTO convertEntityToTeamGetDTO(Team team);

  @Mapping(source = "name", target = "name")
  Team convertTeamPutDTOtoEntity(TeamPutDTO teamPutDTO);

  @Mapping(source = "id", target = "id")
  Team convertTeamDeleteDTOtoEntity(TeamDeleteDTO teamDeleteDTO);

  @Mapping(source = "name", target = "name")
  @Mapping(source = "value", target = "value")
  @Mapping(source = "description", target = "description")
  @Mapping(source = "daysVisible", target = "daysVisible")
  @Mapping(source = "deadline", target = "deadline", dateFormat = "yyyy-MM-dd")
  @Mapping(source = "startDate", target = "startDate", dateFormat = "yyyy-MM-dd")
  Task convertTaskPostDTOtoEntity(TaskPostDTO taskPostDTO);
  
  @Mapping(source = "id", target = "id")
  @Mapping(source = "creatorId", target = "creatorId")
  @Mapping(source = "isAssignedTo", target = "isAssignedTo")
  @Mapping(source = "name", target = "name")
  @Mapping(source = "description", target = "description")
  @Mapping(source = "deadline", target = "deadline", dateFormat = "yyyy-MM-dd")
  @Mapping(source = "color", target = "color")
  @Mapping(source = "activeStatus", target = "activeStatus")
  @Mapping(source = "value", target = "value")
  TaskGetDTO convertEntityToTaskGetDTO(Task task);

  @Mapping(source = "id", target = "id")
  @Mapping(source = "creatorId", target = "creatorId")
  @Mapping(source = "isAssignedTo", target = "isAssignedTo")
  @Mapping(source = "name", target = "name")
  @Mapping(source = "description", target = "description")
  @Mapping(source = "deadline", target = "deadline", dateFormat = "yyyy-MM-dd")
  @Mapping(source = "color", target = "color")
  @Mapping(source = "activeStatus", target = "activeStatus")
  @Mapping(source = "value", target = "value")
  Task convertTaskGetDTOtoEntity(TaskGetDTO taskGetDTO);

  @Mapping(source = "isAssignedTo", target = "isAssignedTo")
  @Mapping(source = "name", target = "name")
  @Mapping(source = "description", target = "description")
  @Mapping(source = "daysVisible", target = "daysVisible")
  @Mapping(source = "deadline", target = "deadline",dateFormat = "yyyy-MM-dd")
  @Mapping(source = "startDate", target = "startDate",dateFormat = "yyyy-MM-dd")
  @Mapping(source = "color", target = "color")
  @Mapping(source = "activeStatus", target = "activeStatus")
  Task convertTaskPutDTOtoEntity(TaskPutDTO taskPutDTO);

  @Mapping(source = "id", target = "id")
  Task convertTaskDeleteDTOtoEntity(TaskDeleteDTO taskDeleteDTO);
}
