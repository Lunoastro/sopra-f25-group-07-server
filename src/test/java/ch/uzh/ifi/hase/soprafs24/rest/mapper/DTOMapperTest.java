
package ch.uzh.ifi.hase.soprafs24.rest.mapper;

import ch.uzh.ifi.hase.soprafs24.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs24.entity.User;
import ch.uzh.ifi.hase.soprafs24.entity.Team;
import ch.uzh.ifi.hase.soprafs24.entity.Task;
import ch.uzh.ifi.hase.soprafs24.rest.dto.user.*;
import ch.uzh.ifi.hase.soprafs24.rest.dto.team.*;
import ch.uzh.ifi.hase.soprafs24.rest.dto.task.*;
import ch.uzh.ifi.hase.soprafs24.constant.ColorID;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * DTOMapperTest
 * Tests if the mapping between the internal and the external/API representation
 * works.
 */
 class DTOMapperTest {

  private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd"); 

  @Test
   void testCreateUser_fromUserPostDTO_toUser_success() {
    // create UserPostDTO
    UserPostDTO userPostDTO = new UserPostDTO();
    userPostDTO.setName("user_name");
    userPostDTO.setUsername("name");
    userPostDTO.setPassword("123");

    // MAP -> Create user
    User user = DTOMapper.INSTANCE.convertUserPostDTOtoEntity(userPostDTO);

    // check content
    assertEquals(userPostDTO.getName(), user.getName());
    assertEquals(userPostDTO.getUsername(), user.getUsername());
    assertEquals(userPostDTO.getPassword(), user.getPassword());
  }

  @Test
   void testGetUser_fromUser_toUserGetDTO_success() {
    // create User
    User user = new User();
    Date now = new Date(new Date().getTime() + 3600 * 1000);
    user.setName("user_firstname@lastname");
    user.setUsername("firstname@lastname");
    user.setStatus(UserStatus.OFFLINE);
    user.setToken("1");
    user.setCreationDate(now);
    user.setBirthDate(null);
    

    // MAP -> Create UserGetDTO
    UserGetDTO userGetDTO = DTOMapper.INSTANCE.convertEntityToUserGetDTO(user);

  
    // check content
    assertEquals(user.getId(), userGetDTO.getId());
    assertEquals(user.getName(), userGetDTO.getName());
    assertEquals(user.getUsername(), userGetDTO.getUsername());
    assertEquals(user.getStatus(), userGetDTO.getStatus());
    assertEquals(user.getCreationDate(), userGetDTO.getCreationDate());
    assertEquals(user.getBirthDate(), userGetDTO.getBirthDate());
  }

  @Test
  void testUpdateUser_fromUserPutDTO_toUser_success() {
    // create UserPutDTO
    UserPutDTO userPutDTO = new UserPutDTO();
    userPutDTO.setId(1L);
    userPutDTO.setUsername("updated_username");
    userPutDTO.setColor(ColorID.C1);
    userPutDTO.setBirthDate(new Date());
    
    // MAP -> Update user
    User user = DTOMapper.INSTANCE.convertUserPutDTOtoEntity(userPutDTO);
    
    // check content
    assertEquals(userPutDTO.getId(), user.getId());
    assertEquals(userPutDTO.getUsername(), user.getUsername());
    assertEquals(userPutDTO.getColor(), user.getColor());
    assertEquals(userPutDTO.getBirthDate(), user.getBirthDate());
  }
  
  @Test
  void testDeleteUser_fromUserDeleteDTO_toUser_success() {
    // create UserDeleteDTO
    UserDeleteDTO userDeleteDTO = new UserDeleteDTO();
    userDeleteDTO.setId(1L);
    
    // MAP -> Delete user
    User user = DTOMapper.INSTANCE.convertUserDeleteDTOtoEntity(userDeleteDTO);
    
    // check content
    assertEquals(userDeleteDTO.getId(), user.getId());
  }

  @Test
  void testCreateTeam_fromTeamPostDTO_toTeam_success() {
    // create TeamPostDTO
    TeamPostDTO teamPostDTO = new TeamPostDTO();
    teamPostDTO.setName("Dream Team");
    teamPostDTO.setCode("DREAM123");

    // MAP -> Create team
    Team team = DTOMapper.INSTANCE.convertTeamPostDTOtoEntity(teamPostDTO);

    // check content
    assertEquals(teamPostDTO.getName(), team.getName());
    assertEquals(teamPostDTO.getCode(), team.getCode());
  }

  @Test
  void testGetTeam_fromTeam_toTeamGetDTO_success() {
      // create Team
      Team team = new Team();
      team.setId(1L);
      team.setName("Dream Team");
      team.setXp(500);
      team.setLevel(5);
      team.setCode("DREAM123");
      
      // Create sample members (using user IDs instead of User objects)
      List<Long> memberIds = new ArrayList<>();
      memberIds.add(1L);  // Instead of adding User objects, we add user IDs
      memberIds.add(2L);  // Same here
      team.setMembers(memberIds);  // Set the members as IDs
      
      // Create sample tasks
      List<Long> taskIds = new ArrayList<>();
      taskIds.add(1L);  // Use task IDs
      taskIds.add(2L);  // Same here
      team.setTasks(taskIds);  // Set the tasks as IDs
          
      // MAP -> Create TeamGetDTO
      TeamGetDTO teamGetDTO = DTOMapper.INSTANCE.convertEntityToTeamGetDTO(team);
        
      // check content
      assertEquals(team.getId(), teamGetDTO.getId());
      assertEquals(team.getName(), teamGetDTO.getName());
      assertEquals(team.getXp(), teamGetDTO.getXp());
      assertEquals(team.getLevel(), teamGetDTO.getLevel());
      assertEquals(team.getCode(), teamGetDTO.getCode());
      assertEquals(team.getMembers(), teamGetDTO.getMembers());  // Ensure members is a list of IDs
      assertEquals(team.getTasks(), teamGetDTO.getTasks());  // Ensure tasks is a list of IDs
      assertEquals(2, teamGetDTO.getMembers().size());  // Members count should be 2
      assertEquals(2, teamGetDTO.getTasks().size());  // Tasks count should be 2
  }

  
  @Test
  void testUpdateTeam_fromTeamPutDTO_toTeam_success() {
    // create TeamPutDTO
    TeamPutDTO teamPutDTO = new TeamPutDTO();
    teamPutDTO.setName("Updated Team Name");
    
    // MAP -> Update team
    Team team = DTOMapper.INSTANCE.convertTeamPutDTOtoEntity(teamPutDTO);
    
    // check content
    assertEquals(teamPutDTO.getName(), team.getName());
  }
  
  @Test
  void testDeleteTeam_fromTeamDeleteDTO_toTeam_success() {
    // create TeamDeleteDTO
    TeamDeleteDTO teamDeleteDTO = new TeamDeleteDTO();
    teamDeleteDTO.setId(1L);
    
    // MAP -> Delete team
    Team team = DTOMapper.INSTANCE.convertTeamDeleteDTOtoEntity(teamDeleteDTO);
    
    // check content
    assertEquals(teamDeleteDTO.getId(), team.getId());
  }
  
  @Test
  void testCreateTask_fromTaskPostDTO_toTask_success() throws ParseException {
    // create TaskPostDTO
    TaskPostDTO taskPostDTO = new TaskPostDTO();
    taskPostDTO.setName("Complete Project");
    taskPostDTO.setValue(100);
    taskPostDTO.setFrequency(7);
    taskPostDTO.setDescription("Finish the project before deadline");
    taskPostDTO.setDaysVisible(5);
    taskPostDTO.setDeadline(dateFormat.parse("2025-05-30"));
    taskPostDTO.setStartDate(dateFormat.parse("2025-05-01"));
    
    // MAP -> Create task
    Task task = DTOMapper.INSTANCE.convertTaskPostDTOtoEntity(taskPostDTO);

    // check content
    assertEquals(taskPostDTO.getName(), task.getName());
    assertEquals(taskPostDTO.getValue(), task.getValue());
    assertEquals(taskPostDTO.getFrequency(), task.getFrequency());
    assertEquals(taskPostDTO.getDescription(), task.getDescription());
    assertEquals(taskPostDTO.getDaysVisible(), task.getDaysVisible());
    assertEquals(taskPostDTO.getDeadline(), task.getDeadline());
    assertEquals(taskPostDTO.getStartDate(), task.getStartDate());
    // Not testing creatorId or activeStatus as they're not in TaskPostDTO
    // These would be set in the service layer
  }

  @Test
  void testGetTask_fromTask_toTaskGetDTO_success() throws ParseException {
    // create Task
    Task task = new Task();
    task.setId(1L);
    task.setcreatorId(10L);
    task.setIsAssignedTo(20L);
    task.setName("Complete Project");
    task.setDescription("Finish the project before deadline");
    task.setDeadline(dateFormat.parse("2025-05-30"));
    task.setColor(ColorID.C1);
    task.setActiveStatus(true);
    task.setValue(100);
    task.setGoogleEventId("event123");
    task.setDaysVisible(5);
    task.setStartDate(dateFormat.parse("2025-05-01"));
    task.setFrequency(7);
    task.setCreationDate(new Date()); // Add creation date as it's required in the entity
        
    // MAP -> Create TaskGetDTO
    TaskGetDTO taskGetDTO = DTOMapper.INSTANCE.convertEntityToTaskGetDTO(task);
      
    // check content
    assertEquals(task.getId(), taskGetDTO.getId());
    assertEquals(task.getcreatorId(), taskGetDTO.getCreatorId());
    assertEquals(task.getIsAssignedTo(), taskGetDTO.getIsAssignedTo());
    assertEquals(task.getName(), taskGetDTO.getName());
    assertEquals(task.getDescription(), taskGetDTO.getDescription());
    assertEquals("2025-05-30", taskGetDTO.getDeadline()); // TaskGetDTO uses String
    assertEquals(task.getColor().toString(), taskGetDTO.getColor()); // TaskGetDTO uses String
    assertEquals(task.getActiveStatus(), taskGetDTO.getActiveStatus());
    assertEquals(task.getValue(), taskGetDTO.getValue());
    assertEquals(task.getGoogleEventId(), taskGetDTO.getGoogleEventId());
    assertEquals(task.getDaysVisible(), taskGetDTO.getDaysVisible());
    assertEquals("2025-05-01", taskGetDTO.getStartDate()); // TaskGetDTO uses String
    assertEquals(task.getFrequency(), taskGetDTO.getFrequency());
  }
  
  @Test
  void testConvertTaskGetDTOtoEntity_success() throws ParseException {
    // create TaskGetDTO
    TaskGetDTO taskGetDTO = new TaskGetDTO();
    taskGetDTO.setId(1L);
    taskGetDTO.setCreatorId(10L);
    taskGetDTO.setIsAssignedTo(20L);
    taskGetDTO.setName("Complete Project");
    taskGetDTO.setDescription("Finish the project before deadline");
    taskGetDTO.setDeadline("2025-05-30");
    taskGetDTO.setColor(ColorID.C1.toString()); // Using String as per TaskGetDTO
    taskGetDTO.setActiveStatus(true);
    taskGetDTO.setValue(100);
    taskGetDTO.setGoogleEventId("event123");
    taskGetDTO.setDaysVisible(5);
    taskGetDTO.setStartDate("2025-05-01");
    taskGetDTO.setFrequency(7);
    
    // MAP -> Convert to Task entity
    Task task = DTOMapper.INSTANCE.convertTaskGetDTOtoEntity(taskGetDTO);
    
    // check content
    assertEquals(taskGetDTO.getId(), task.getId());
    assertEquals(taskGetDTO.getCreatorId(), task.getcreatorId());
    assertEquals(taskGetDTO.getIsAssignedTo(), task.getIsAssignedTo());
    assertEquals(taskGetDTO.getName(), task.getName());
    assertEquals(taskGetDTO.getDescription(), task.getDescription());
    assertEquals(dateFormat.parse(taskGetDTO.getDeadline()), task.getDeadline());
    assertEquals(ColorID.valueOf(taskGetDTO.getColor()), task.getColor()); // Convert from String to ColorID
    assertEquals(taskGetDTO.getActiveStatus(), task.getActiveStatus());
    assertEquals(taskGetDTO.getValue(), task.getValue());
    assertEquals(taskGetDTO.getGoogleEventId(), task.getGoogleEventId());
    assertEquals(taskGetDTO.getDaysVisible(), task.getDaysVisible());
    assertEquals(dateFormat.parse(taskGetDTO.getStartDate()), task.getStartDate());
    assertEquals(taskGetDTO.getFrequency(), task.getFrequency());
  }
  
  @Test
  void testUpdateTask_fromTaskPutDTO_toTask_success() throws ParseException {
    // create TaskPutDTO
    TaskPutDTO taskPutDTO = new TaskPutDTO();
    taskPutDTO.setIsAssignedTo(30L);
    taskPutDTO.setName("Updated Task");
    taskPutDTO.setDescription("Updated description");
    taskPutDTO.setValue(200);
    taskPutDTO.setDaysVisible(7);
    taskPutDTO.setDeadline(dateFormat.parse("2025-06-15"));
    taskPutDTO.setStartDate(dateFormat.parse("2025-06-01"));
    taskPutDTO.setColor(ColorID.C1);
    taskPutDTO.setActiveStatus(false);
    
    // MAP -> Update task
    Task task = DTOMapper.INSTANCE.convertTaskPutDTOtoEntity(taskPutDTO);
    
    // check content
    assertEquals(taskPutDTO.getIsAssignedTo(), task.getIsAssignedTo());
    assertEquals(taskPutDTO.getName(), task.getName());
    assertEquals(taskPutDTO.getDescription(), task.getDescription());
    assertEquals(taskPutDTO.getValue(), task.getValue());
    assertEquals(taskPutDTO.getDaysVisible(), task.getDaysVisible());
    assertEquals(taskPutDTO.getDeadline(), task.getDeadline());
    assertEquals(taskPutDTO.getStartDate(), task.getStartDate());
    assertEquals(taskPutDTO.getColor(), task.getColor());
    assertEquals(taskPutDTO.getActiveStatus(), task.getActiveStatus());
  }
  
  @Test
  void testDeleteTask_fromTaskDeleteDTO_toTask_success() {
    // create TaskDeleteDTO
    TaskDeleteDTO taskDeleteDTO = new TaskDeleteDTO();
    taskDeleteDTO.setId(1L);
    
    // MAP -> Delete task
    Task task = DTOMapper.INSTANCE.convertTaskDeleteDTOtoEntity(taskDeleteDTO);
    
    // check content
    assertEquals(taskDeleteDTO.getId(), task.getId());
  }


}