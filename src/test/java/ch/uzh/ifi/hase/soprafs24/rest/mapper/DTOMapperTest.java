package ch.uzh.ifi.hase.soprafs24.rest.mapper;

import ch.uzh.ifi.hase.soprafs24.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs24.entity.User;
import ch.uzh.ifi.hase.soprafs24.rest.dto.User.UserGetDTO;
import ch.uzh.ifi.hase.soprafs24.rest.dto.User.UserPostDTO;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


import java.util.Date;


/**
 * DTOMapperTest
 * Tests if the mapping between the internal and the external/API representation
 * works.
 */
 class DTOMapperTest {
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


}
