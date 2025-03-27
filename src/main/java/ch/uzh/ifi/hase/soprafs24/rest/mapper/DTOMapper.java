package ch.uzh.ifi.hase.soprafs24.rest.mapper;

import ch.uzh.ifi.hase.soprafs24.entity.User;
import ch.uzh.ifi.hase.soprafs24.entity.Team;
import ch.uzh.ifi.hase.soprafs24.rest.dto.UserGetDTO;
import ch.uzh.ifi.hase.soprafs24.rest.dto.UserPostDTO;
import ch.uzh.ifi.hase.soprafs24.rest.dto.UserPutDTO;
import ch.uzh.ifi.hase.soprafs24.rest.dto.TeamGetDTO;
import ch.uzh.ifi.hase.soprafs24.rest.dto.TeamPostDTO;
import ch.uzh.ifi.hase.soprafs24.rest.dto.TeamPutDTO;
import ch.uzh.ifi.hase.soprafs24.rest.dto.TeamDeleteDTO;

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
  @Mapping(source = "token", target = "token")
  @Mapping(source = "status", target = "status")
  @Mapping(source = "creationDate", target = "creationDate")
  @Mapping(source ="birthDate", target = "birthDate")
  UserGetDTO convertEntityToUserGetDTO(User user);

  @Mapping(source = "username", target = "username")
  @Mapping(source = "id", target = "id")
  @Mapping(source = "birthDate", target = "birthDate")
  User convertUserPutDTOtoEntity(UserPutDTO userPutDTO);

  @Mapping(source = "teamId", target = "teamId")
  @Mapping(source = "teamName", target = "teamName")
  @Mapping(source = "teamXP", target = "teamXP")
  @Mapping(source = "teamLevel", target = "teamLevel")
  @Mapping(source = "teamCode", target = "teamCode")
  @Mapping(source = "teamMembers", target = "teamMembers")
  TeamGetDTO convertEntityToTeamGetDTO(Team team);

  @Mapping(source = "teamName", target = "teamName")
  @Mapping(source = "teamCode", target = "teamCode")
  Team convertTeamPostDTOtoEntity(TeamPostDTO teamPostDTO);

  @Mapping(source = "teamName", target = "teamName")
  Team convertTeamPutDTOtoEntity(TeamPutDTO teamPutDTO);

  @Mapping(source = "teamId", target = "teamId")
  @Mapping(source = "userId", target = "userId")
  Team convertTeamDeleteDTOtoEntity(TeamDeleteDTO teamDeleteDTO);


}
