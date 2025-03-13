package ch.uzh.ifi.hase.soprafs24.controller;

import ch.uzh.ifi.hase.soprafs24.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs24.entity.User;
import ch.uzh.ifi.hase.soprafs24.rest.dto.UserPostDTO;
import ch.uzh.ifi.hase.soprafs24.rest.dto.UserPutDTO;
import ch.uzh.ifi.hase.soprafs24.service.UserService;
import ch.uzh.ifi.hase.soprafs24.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UserControllerTest
 * This is a WebMvcTest which allows to test the UserController i.e. GET/POST
 * request without actually sending them over the network.
 * This tests if the UserController works.
 */
@WebMvcTest(UserController.class)
public class UserControllerTest { //command to run all tests: ./gradlew test --tests "ch.uzh.ifi.hase.soprafs24.controller.UserControllerTest"

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private UserService userService;

  @MockBean
  private UserRepository userRepository;


  @Test
  public void GET_givenUsers_whenGetUsers_thenReturnJsonArray() throws Exception { //./gradlew test --tests "ch.uzh.ifi.hase.soprafs24.controller.UserControllerTest.GET_givenUsers_whenGetUsers_thenReturnJsonArray"
    // given
    User user = new User();
    user.setName("user_firstname@lastname");
    user.setUsername("firstname@lastname");
    user.setStatus(UserStatus.ONLINE); 

    List<User> allUsers = Collections.singletonList(user);

    // this mocks the UserService -> we define above what the userService should
    // return when getUsers() is called
    given(userService.getUsers()).willReturn(allUsers);

    String validToken = "1";
    given(userService.validateToken(validToken)).willReturn(true);

    // when
    MockHttpServletRequestBuilder getRequest = get("/users")
        .contentType(MediaType.APPLICATION_JSON)
        .header("Authorization", "Bearer " + validToken);

    // then
    mockMvc.perform(getRequest).andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].name", is(user.getName())))
        .andExpect(jsonPath("$[0].username", is(user.getUsername())))
        .andExpect(jsonPath("$[0].status", is(user.getStatus().toString())));
        
  }

  @Test
  public void POST_createUser_validInput_userCreated() throws Exception {  //POST/users
    // given
    User user = new User();
    Date now = new Date(new Date().getTime() + 3600 * 1000);
    user.setId(1L);
    user.setName("user_testUsername");
    user.setUsername("testUsername");
    user.setToken("1");
    user.setStatus(UserStatus.ONLINE);
    user.setCreationDate(now);
    user.setBirthDate(null);
    
    
    UserPostDTO userPostDTO = new UserPostDTO();
    userPostDTO.setName("user_testUsername");
    userPostDTO.setUsername("testUsername");
    userPostDTO.setPassword("123");

    
    given(userService.createUser(Mockito.any())).willReturn(user);

    // when/then -> do the request + validate the result
    MockHttpServletRequestBuilder postRequest = post("/users")
        .contentType(MediaType.APPLICATION_JSON)
        .content(asJsonString(userPostDTO));
    
    String formattedDate = formatToIso8601(now); // Call the helper function

    // then
    mockMvc.perform(postRequest)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id", is(user.getId().intValue())))
        .andExpect(jsonPath("$.name", is(user.getName())))
        .andExpect(jsonPath("$.username", is(user.getUsername())))
        .andExpect(jsonPath("$.status", is(user.getStatus().toString())))
        .andExpect(jsonPath("$.token", is(user.getToken())))
        .andExpect(jsonPath("$.creationDate", is(formattedDate)))
        .andExpect(jsonPath("$.birthDate", is((Object) null)));
  }


  @Test 
  public void POST_failedCreateUser_invalidInput_duplicateUsername_userNotCreated() throws Exception {
      // given
      UserPostDTO userPostDTO = new UserPostDTO(); 
      userPostDTO.setName("user_testUsername");
      userPostDTO.setUsername("testUsername");
      userPostDTO.setPassword("123");

      given(userService.createUser(Mockito.any())).willThrow( 
              new ResponseStatusException(HttpStatus.CONFLICT, "add User failed becuase username already exists"));


      MockHttpServletRequestBuilder postRequest = post("/users")
              .contentType(MediaType.APPLICATION_JSON)
              .content(asJsonString(userPostDTO));


      mockMvc.perform(postRequest)
      .andExpect(status().isConflict());
  }

  @Test
  public void GET_retrieveUserWithId_success() throws Exception {
      //given
      // here we create a user object because the get request should succeed, so we will need it to compare the result later
      User user = new User();
      Date now = new Date(new Date().getTime() + 3600 * 1000);
      user.setId(1L);
      user.setName("user_testUsername");
      user.setUsername("testUsername");
      user.setToken("1"); //use "1" as mock valid token
      user.setStatus(UserStatus.ONLINE);
      user.setCreationDate(now);
      user.setBirthDate(null);

      // this mocks the UserService -> we define above what the userService should
      // return when getUsers() is called
      // specifying that the getUserById will succeed and return a user
      given(userService.getUserById(Mockito.any(Long.class))).willReturn(user);
      given(userService.validateToken("1")).willReturn(true);

      // when
      // executing a mock get request. This time we do not need to format the request body, since the get request does not have a request body.
      MockHttpServletRequestBuilder getRequest = get("/users/{userId}",user.getId())
              .header("Authorization", "Bearer 1")
              .accept(MediaType.APPLICATION_JSON);
      
      String formattedDate = formatToIso8601(now);

      // then
      // here we check the results, actual and expected
      mockMvc.perform(getRequest)
              .andExpect(status().isOk()) //checking status: 200
              .andExpect(jsonPath("$.id", is(user.getId().intValue())))
              .andExpect(jsonPath("$.name", is(user.getName())))
              .andExpect(jsonPath("$.username", is(user.getUsername())))
              .andExpect(jsonPath("$.status", is(user.getStatus().toString())))
              .andExpect(jsonPath("$.token", is(user.getToken())))
              .andExpect(jsonPath("$.creationDate", is(formattedDate)))
              .andExpect(jsonPath("$.birthDate", is((Object) null)));
  }

  @Test
  public void GET_failedRetrieveUserWithId_UserNotFound() throws Exception {
    // given
    User user = new User();
    user.setId(1L);

    given(userService.validateToken("1")).willReturn(true);

    // setting the getUserById function to throw an error (in the case of not finding a user)
    given(userService.getUserById(Mockito.any(Long.class))).willThrow( //specifying that this post request will throw an error. We are not actually executing createUser, so we don't have to setup the scenario of duplicate usernames...
              new ResponseStatusException(HttpStatus.NOT_FOUND, "user with this ID was not found"));

    // when/then -> do the request + validate the result
    //create mock POST request
    MockHttpServletRequestBuilder getRequest = get("/users/{userId}", user.getId())
      .header("Authorization", "Bearer 1") // Add required authorization header
      .accept(MediaType.APPLICATION_JSON);


    // then execute the mock GET request (which we've set to throw an exception, should result in 409 (not found)
    mockMvc.perform(getRequest)
      .andExpect(status().isNotFound());

  }

    @Test
    public void PUT_updateUser_success() throws Exception {
        //given
        // here we create a user object because the get request should succeed, so we will need it to compare the result later
        User user = new User();
        user.setId(1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        //create request body content
        UserPutDTO userPutDTO = new UserPutDTO();
        userPutDTO.setUsername("user1");
        userPutDTO.setBirthDate(null);

        String validToken = "valid-token";
        String authHeader = "Bearer " + validToken;

        // this mocks the UserService -> we define above what the userService should
        // return when getUsers() is called
        // specifying that the updateUser function will succeed and return nothing
        when(userService.validateToken(validToken)).thenReturn(true);  // Mock token validation to return true
        when(userService.findIDforToken(validToken)).thenReturn(1L); // Mock user ID for valid token
        doNothing().when(userService).updateUser(Mockito.any());  // Mock updateUser to do nothing (successful update)

        // when
        // executing a mock put request.
        MockHttpServletRequestBuilder putRequest = put("/users/{userId}", user.getId())
                .contentType(MediaType.APPLICATION_JSON) //this line indicates that the request body in this POST request is in JSON format.
                .content(asJsonString(userPutDTO))
                .header("Authorization", authHeader); //here we are putting the userPostDTO variable into JSON format (i.e. {"username":"testUsername", "password":"123"}

        // then
        // checking the status code
        mockMvc.perform(putRequest)
                .andExpect(status().isNoContent()); //checking status: 204
    }
  
    @Test
    public void PUT_failedUpdateUser_UserNotFound() throws Exception {
        User user = new User();
        user.setId(1L);

        //create request body content
        UserPutDTO userPutDTO = new UserPutDTO();
        userPutDTO.setUsername("user123");
        userPutDTO.setBirthDate(null);

        when(userRepository.findById(user.getId())).thenReturn(Optional.empty());

        String validToken = "valid-token";
        String authHeader = "Bearer " + validToken;
        when(userService.validateToken(validToken)).thenReturn(true);  // Mock token validation to return true
        when(userService.findIDforToken(validToken)).thenReturn(1L); 

        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "user with this ID was not found"))
            .when(userService).updateUser(Mockito.any());

        // setting the getUserById function to throw an error (in the case of not finding a user)
        MockHttpServletRequestBuilder putRequest = put("/users/{userId}", user.getId())
                .contentType(MediaType.APPLICATION_JSON) //this line indicates that the request body in this POST request is in JSON format.
                .content(asJsonString(userPutDTO)) //here we are putting the userPostDTO variable into JSON format (i.e. {"username":"testUsername", "password":"123"}
                .header("Authorization", authHeader);
        // then execute the mock GET request (which we've set to throw an exception, should result in 409 (not found)
        mockMvc.perform(putRequest).andExpect(status().isNotFound());
    }

  /**
   * Helper Method to convert userPostDTO into a JSON string such that the input
   * can be processed
   * Input will look like this: {"name": "Test User", "username": "testUsername"}
   * 
   * @param object
   * @return string
   */
  private String asJsonString(final Object object) {
    try {
      return new ObjectMapper().writeValueAsString(object);
    } catch (JsonProcessingException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          String.format("The request body could not be created.%s", e.toString()));
    }
  }

  private String formatToIso8601(Date now) {
    SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    isoFormat.setTimeZone(TimeZone.getTimeZone("UTC")); // Ensures UTC time
    String formattedDate = isoFormat.format(now);
    // Replace "Z" with "+00:00" to conform to your desired format
    return formattedDate.replace("Z", "+00:00");
  }

  



}