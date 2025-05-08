package ch.uzh.ifi.hase.soprafs24.service;

import ch.uzh.ifi.hase.soprafs24.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs24.entity.User;
import ch.uzh.ifi.hase.soprafs24.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;


import java.util.List;
import java.util.UUID;
import java.util.Date;



/**
 * User Service
 * This class is the "worker" and responsible for all functionality related to
 * the user
 * (e.g., it creates, modifies, deletes, finds). The result will be passed back
 * to the caller.
 */
@Service
@Transactional
public class UserService {

  private final Logger log = LoggerFactory.getLogger(UserService.class);

  private final UserRepository userRepository;

  public UserService(@Qualifier("userRepository") UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  public List<User> getUsers() {
    return this.userRepository.findAll();
  }

  public User getUserById(Long userId) {
    User user = userRepository.findUserById(userId);
    if (user == null) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User with userId " + userId + " was not found.");
    }
    return user;
}

  public User createUser(User newUser) {  //works as the registration func as well 
    if (newUser.getUsername() == null || newUser.getUsername().trim().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username cannot be empty.");
  }

  if (newUser.getPassword() == null || newUser.getPassword().trim().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password cannot be empty.");
  }
    if (newUser.getName() == null || newUser.getName().isEmpty()) {
      String defaultName = "user_" + newUser.getUsername();  // Create name as "user_username"
      newUser.setName(defaultName);
  }
    newUser.setCreationDate(new Date(new Date().getTime() + 3600 * 1000));
    newUser.setToken(UUID.randomUUID().toString());
    newUser.setStatus(UserStatus.ONLINE);
    newUser.setLevel(1);
    checkIfUserExists(newUser);
    // saves the given entity but data is only persisted in the database once
    // flush() is called
    userRepository.save(newUser);
    userRepository.flush();

    log.debug("Created Information for User: {}", newUser);
    return newUser;
  }


  public User loginUser(User registeredUser) { //login for already created/registered users
    User user = userRepository.findByUsername(registeredUser.getUsername());
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid password or inexistent user");
  }
  
  // Check if the provided password matches the stored password
    if (!(registeredUser.getPassword().equals(user.getPassword()))) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid password or inexistent user");
  }

    user.setStatus(UserStatus.ONLINE);
    user = userRepository.save(user);
    userRepository.flush();
    return user;
}


  public void logoffUser(User userInput) {
    User user = userRepository.findByUsername(userInput.getUsername());
    if(user == null) {throw new ResponseStatusException(HttpStatus.NOT_FOUND, "user null");}
    user.setStatus(UserStatus.OFFLINE);
    userRepository.save(user);
    userRepository.flush();
      
  }

  public User updateUser(User userInput) {
    User user = getUserById(userInput.getId()); // get existing user details

    if (userInput.getUsername() != null && !userInput.getUsername().equals(user.getUsername())) {
        User existingUser = userRepository.findByUsername(userInput.getUsername());
        if (existingUser != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This username is already taken.");
        }
        user.setUsername(userInput.getUsername());
        user.setName("user_" + userInput.getUsername());
    }

    if (userInput.getBirthDate() == null) {
        user.setBirthDate(null);
    }
    else {
      user.setBirthDate(adjustBirthDateByOneHour(userInput.getBirthDate()));  // Explicitly set birthDate to null if not provided
    }
    if (userInput.getColor() != null && !userInput.getColor().equals(user.getColor())) {
        user.setColor(userInput.getColor());
    }

    userRepository.save(user);
    userRepository.flush();
    return user;
  }

  public void deleteUser(Long userId) {
    User user = getUserById(userId); // otherwise we throw a 404
    userRepository.delete(user);
    userRepository.flush();
}

  public void addExperiencePoints(Long userId, int points) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

    user.setXp(user.getXp() + points);
    checkLevelUp(user); 
    userRepository.save(user);
    userRepository.flush();

  }

  private void checkLevelUp(User user) {
    int currentXP = user.getXp();
    int currentLevel = user.getLevel();
    int nextLevelXP = getXpForLevel(currentLevel + 1);

    while (currentXP >= nextLevelXP) {
        user.setLevel(currentLevel + 1);
        currentLevel++;
        nextLevelXP = getXpForLevel(currentLevel + 1);
    }
}

  private int getXpForLevel(int level) {
      int baseXP = 100;
      double exponent = 1.5;
      return (int)(baseXP * Math.pow(level, exponent));
  }

  public void deductExperiencePoints(Long userId, int points) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

    // Calculate new XP, ensuring it doesn't go below 0
    int newXp = Math.max(0, user.getXp() - points);
    user.setXp(newXp);
    
    // Check if level needs to be adjusted
    adjustLevelAfterXpDeduction(user);
    
    userRepository.save(user);
    userRepository.flush();
}

  private void adjustLevelAfterXpDeduction(User user) {
      int currentXP = user.getXp();
      int currentLevel = user.getLevel();
      
      // If user's level is already 1, no need to check further as 1 is the minimum level
      if (currentLevel <= 1) {
          user.setLevel(1);
          return;
      }
      
      // Check if current XP is less than what's required for the current level
      while (currentLevel > 1 && currentXP < getXpForLevel(currentLevel)) {
          currentLevel--;
          user.setLevel(currentLevel);
      }
  }

   



  /**
   * This is a helper method that will check the uniqueness criteria of the
   * username and the name
   * defined in the User entity. The method will do nothing if the input is unique
   * and throw an error otherwise.
   *
   * @param userToBeCreated
   * @throws org.springframework.web.server.ResponseStatusException
   * @see User
   */
  private void checkIfUserExists(User userToBeCreated) {
    String usernameToCheck = userToBeCreated.getUsername().trim();
    User userByUsername = userRepository.findByUsername(usernameToCheck);

    if (userByUsername != null) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "add User failed because username already exists");
    }
}
  public static String verifyToken(String userToken) {
    if (!userToken.startsWith("Bearer ")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Authorization header format");
  }
    return userToken.substring(7);
}
  public boolean validateToken(String token) {
    User user = userRepository.findByToken(token);  // Assuming you store the token on the user object
    return user != null && user.getStatus() == UserStatus.ONLINE;  // Token is valid and the user is online
}

  private Date adjustBirthDateByOneHour(Date birthDate) {
    if (birthDate == null) {
      return null;
  }
    return new Date(birthDate.getTime() + 3600 * 1000); // Add 1 hour (3600 seconds * 1000 milliseconds)
}

  public Long findIDforToken(String token) {
    User user = userRepository.findByToken(token);
    if (user == null) {
      return null;  // Or you can throw an exception or return a specific value
    }
    // Return the user ID if the user is found
    return user.getId();
  }


}
