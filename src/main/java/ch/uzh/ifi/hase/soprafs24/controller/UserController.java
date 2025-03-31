package ch.uzh.ifi.hase.soprafs24.controller;

import ch.uzh.ifi.hase.soprafs24.entity.User;
import ch.uzh.ifi.hase.soprafs24.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs24.rest.dto.User.UserGetDTO;
import ch.uzh.ifi.hase.soprafs24.rest.dto.User.UserPostDTO;
import ch.uzh.ifi.hase.soprafs24.rest.dto.User.UserPutDTO;
import ch.uzh.ifi.hase.soprafs24.rest.mapper.DTOMapper;
import ch.uzh.ifi.hase.soprafs24.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;



import java.util.ArrayList;
import java.util.List;

/**
 * User Controller
 * This class is responsible for handling all REST request that are related to
 * the user.
 * The controller will receive the request and delegate the execution to the
 * UserService and finally return the result.
 */
@RestController
public class UserController {

  private final UserService userService;
  private final UserRepository userRepository;

  

  UserController(UserService userService, UserRepository userRepository) {
    this.userService = userService;
    this.userRepository = userRepository;
  }

  @GetMapping("/users")
  @ResponseStatus(HttpStatus.OK)
  @ResponseBody
  public List<UserGetDTO> getAllUsers(@RequestHeader("Authorization") String authorizationHeader) {
    // Extract and validate the token
    String token = validateAuthorizationHeader(authorizationHeader);

    if (!userService.validateToken(token)) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized: Invalid token.");
    }

    // Fetch all users
    List<User> users = userService.getUsers();
    List<UserGetDTO> userGetDTOs = new ArrayList<>();

    // Convert each user to the API representation
    for (User user : users) {
        userGetDTOs.add(DTOMapper.INSTANCE.convertEntityToUserGetDTO(user));
    }
    return userGetDTOs;
}

  @PostMapping("/users")
  @ResponseStatus(HttpStatus.CREATED)
  @ResponseBody
  public UserGetDTO createUser(@RequestBody UserPostDTO userPostDTO) {
    // convert API user to internal representation
    User userInput = DTOMapper.INSTANCE.convertUserPostDTOtoEntity(userPostDTO);

    // create user
    User createdUser = userService.createUser(userInput);
    // convert internal representation of user back to API
    return DTOMapper.INSTANCE.convertEntityToUserGetDTO(createdUser);
  }

  @PostMapping("/registeredUsers")
  @ResponseStatus(HttpStatus.OK)
  @ResponseBody
  public UserGetDTO loginUser(@RequestBody UserPostDTO userPostDTO) {
      // convert API user to internal representation
    User userInput = DTOMapper.INSTANCE.convertUserPostDTOtoEntity(userPostDTO);

      // log in user
    User loggedInUser = userService.loginUser(userInput);
      // convert internal representation of user back to API
    return DTOMapper.INSTANCE.convertEntityToUserGetDTO(loggedInUser);
  }  

  @PutMapping("/logoff")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logoff(@RequestBody UserPutDTO userPutDTO, @RequestHeader("Authorization") String authorizationHeader) {
    String token = validateAuthorizationHeader(authorizationHeader);
    if (!userService.validateToken(token)) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized: Invalid token.");
    }
    Long authenticatedUserId = userService.findIDforToken(token);
    // Ensure the user ID in the request matches the authenticated user ID
    if (!authenticatedUserId.equals(userPutDTO.getId())) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden: You can only log off your own account.");
    }
    User userInput = DTOMapper.INSTANCE.convertUserPutDTOtoEntity(userPutDTO);
    userService.logoffUser(userInput);
  }

  @GetMapping("/users/{userId}")
  @ResponseStatus(HttpStatus.OK)
  @ResponseBody
  public UserGetDTO getUserProfile(@PathVariable Long userId, @RequestHeader("Authorization") String authorizationHeader) {
    // Extract the token from the Authorization header
    String token = validateAuthorizationHeader(authorizationHeader);

    if (!userService.validateToken(token)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized: Invalid token.");
    }

    User user = userService.getUserById(userId);
    return DTOMapper.INSTANCE.convertEntityToUserGetDTO(user);
}

  @PutMapping("/users/{userId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void update(@PathVariable Long userId, @RequestBody UserPutDTO userPutDTO, @RequestHeader("Authorization") String authorizationHeader) {    
    if(!userRepository.findById(userId).isPresent()){
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User with userId " + userId + " was not found.");
    }

    String token = validateAuthorizationHeader(authorizationHeader);
    if (!userService.validateToken(token)) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized: Invalid token.");
    }

    Long authenticatedUserId = userService.findIDforToken(token);
    
    // Ensure the user ID in the path matches the authenticated user ID
    if (!authenticatedUserId.equals(userId)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden: You can only update your own profile.");
    }
      
      //convert API user to internal representation
    User userInput = DTOMapper.INSTANCE.convertUserPutDTOtoEntity(userPutDTO);
    userInput.setId(userId);
      //update user information
    userService.updateUser(userInput);
  }

  private String validateAuthorizationHeader(String authorizationHeader) {
    if (authorizationHeader == null || authorizationHeader.trim().isEmpty() || !authorizationHeader.startsWith("Bearer ")) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized: Missing or invalid Authorization header.");
    }
    return authorizationHeader.substring(7);  // Remove "Bearer " prefix
}


}
