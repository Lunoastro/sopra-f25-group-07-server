package ch.uzh.ifi.hase.soprafs24.service.Task;

import ch.uzh.ifi.hase.soprafs24.constant.ColorID;
import ch.uzh.ifi.hase.soprafs24.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs24.entity.Task;
import ch.uzh.ifi.hase.soprafs24.entity.User;
import ch.uzh.ifi.hase.soprafs24.repository.TaskRepository;
import ch.uzh.ifi.hase.soprafs24.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs24.service.TaskService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import static org.junit.jupiter.api.Assertions.*;

@WebAppConfiguration
@SpringBootTest
@TestPropertySource("classpath:application-dev.properties")
class TaskServiceIntegrationTest {

    @Qualifier("taskRepository")
    @Autowired
    private TaskRepository taskRepository;

    @Qualifier("userRepository")
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TaskService taskService;

    private User testUser;

    @BeforeEach
    void setup() {
        taskRepository.deleteAll();
        userRepository.deleteAll();

        // Create and save a test user
        testUser = new User();
        testUser.setUsername("testUser");
        testUser.setName("Test User");
        testUser.setPassword("password");
        testUser.setColor(ColorID.C1); // Assign a color to the user
        testUser.setXp(0);
        testUser.setLevel(1);
        testUser.setStatus(UserStatus.ONLINE);
        testUser.setToken("valid-token");
        testUser.setCreationDate(new Date());
        testUser.setTeamId(1L);
        userRepository.save(testUser);
    }

    @Test
    void claimTask_validInputs_success() {
        // Given a task that is not assigned
        Task testTask = new Task();
        testTask.setName("Test Task");
        testTask.setIsAssignedTo(null); // unassigned task
        testTask.setCreationDate(new Date());
        testTask.setDeadline(new Date(System.currentTimeMillis() + 3600 * 1000)); // Set deadline in 1 hour
        testTask.setValue(10);
        testTask.setActiveStatus(true);
        testTask.setPaused(false); // Initially not paused
        testTask.setcreatorId(testUser.getId()); // Set the creator ID to the test user
        taskRepository.save(testTask);

        // When the task is claimed
        Task claimedTask = taskService.claimTask(testTask, "Bearer valid-token");

        // Then the task is assigned to the user and color is set
        assertEquals(testUser.getId(), claimedTask.getIsAssignedTo());
        assertEquals(ColorID.C1, claimedTask.getColor());
    }

    @Test
    void claimTask_alreadyClaimed_throwsConflictException() {
        // Given a task that is already claimed
        Task testTask = new Task();
        testTask.setName("Test Task");
        testTask.setIsAssignedTo(99L); // already assigned to another user
        testTask.setCreationDate(new Date());
        testTask.setDeadline(new Date(System.currentTimeMillis() + 3600 * 1000)); // Set deadline in 1 hour
        testTask.setValue(10);
        testTask.setActiveStatus(true);
        testTask.setPaused(false); // Initially not paused
        testTask.setcreatorId(testUser.getId()); // Set the creator ID
        taskRepository.save(testTask);

        // When trying to claim the task
        assertThrows(ResponseStatusException.class, () -> taskService.claimTask(testTask, "Bearer valid-token"));
    }

    @Test
    void claimTask_userHasNoColor_throwsBadRequestException() {
        // Given a user with no color set
        testUser.setColor(null);
        userRepository.save(testUser);

        // Given an unassigned task
        Task testTask = new Task();
        testTask.setName("Test Task");
        testTask.setIsAssignedTo(null);
        testTask.setCreationDate(new Date());
        testTask.setDeadline(new Date(System.currentTimeMillis() + 3600 * 1000)); // Set deadline in 1 hour
        testTask.setValue(10);
        testTask.setActiveStatus(true);
        testTask.setPaused(false); // Initially not paused
        testTask.setcreatorId(testUser.getId()); // Set the creator ID
        taskRepository.save(testTask);

        // When trying to claim the task
        assertThrows(ResponseStatusException.class, () -> taskService.claimTask(testTask, "Bearer valid-token"));
    }

    @Test
    void claimTask_invalidToken_throwsUnauthorizedException() {
        // Given an invalid token
        Task testTask = new Task();
        testTask.setName("Test Task");
        testTask.setIsAssignedTo(null); // unassigned task
        testTask.setCreationDate(new Date());
        testTask.setDeadline(new Date(System.currentTimeMillis() + 3600 * 1000)); // Set deadline in 1 hour
        testTask.setValue(10);
        testTask.setActiveStatus(true);
        testTask.setPaused(false); // Initially not paused
        testTask.setcreatorId(testUser.getId()); // Set the creator ID
        taskRepository.save(testTask);

        // When trying to claim the task with an invalid token
        assertThrows(ResponseStatusException.class, () -> taskService.claimTask(testTask, "Bearer invalid-token"));
    }

    @Test
    void createTask_validInputs_success() {
        // Given a new task
        Task newTask = new Task();
        newTask.setName("New Task");
        newTask.setIsAssignedTo(null); // unassigned initially
        newTask.setCreationDate(new Date());
        newTask.setDeadline(new Date(System.currentTimeMillis() + 3600 * 1000)); // Set deadline in 1 hour
        newTask.setValue(10);
        newTask.setActiveStatus(true);
        newTask.setPaused(false); // Initially not paused
        newTask.setcreatorId(testUser.getId()); // Set the creator ID

        // When creating the task
        Task createdTask = taskService.createTask(newTask, "Bearer valid-token");

        // Then verify the task is created correctly
        assertEquals(newTask.getName(), createdTask.getName());
        assertNull(createdTask.getIsAssignedTo());
        assertNotNull(createdTask.getCreationDate()); // Ensure creation date is set
    }

    @Test
    void createTask_taskAlreadyExists_throwsConflictException() {
        // Given an existing task
        Task testTask = new Task();
        testTask.setName("Existing Task");
        testTask.setCreationDate(new Date());
        testTask.setDeadline(new Date(System.currentTimeMillis() + 3600 * 1000)); // Set deadline in 1 hour
        testTask.setValue(10);
        testTask.setActiveStatus(true);
        testTask.setPaused(false); // Initially not paused
        testTask.setcreatorId(testUser.getId()); // Set the creator ID
        taskRepository.save(testTask);

        // When trying to create a task with the same name
        assertThrows(ResponseStatusException.class, () -> taskService.createTask(testTask, "Bearer valid-token"));
    }

    @Test
    void createTask_invalidToken_throwsUnauthorizedException() {
        // Given an invalid token
        Task testTask = new Task();
        testTask.setName("New Task");
        testTask.setCreationDate(new Date());
        testTask.setDeadline(new Date(System.currentTimeMillis() + 3600 * 1000)); // Set deadline in 1 hour
        testTask.setValue(10);
        testTask.setActiveStatus(true);
        testTask.setPaused(false); // Initially not paused
        testTask.setcreatorId(testUser.getId()); // Set the creator ID

        // When trying to create the task with an invalid token
        assertThrows(ResponseStatusException.class, () -> taskService.createTask(testTask, "Bearer invalid-token"));
    }

    @Test
    void updateTask_creator_success() {
        // Create a task by testUser
        Task task = new Task();
        task.setName("Old Task Name");
        task.setCreationDate(new Date());
        task.setDeadline(new Date(System.currentTimeMillis() + 3600 * 1000));
        task.setValue(10);
        task.setActiveStatus(true);
        task.setPaused(false);
        task.setcreatorId(testUser.getId());
        task = taskRepository.save(task);
    
        // Update fields
        Task updatedTask = new Task();
        updatedTask.setName("Updated Task Name");
        updatedTask.setValue(25);
        updatedTask.setDeadline(new Date(System.currentTimeMillis() + 7200 * 1000)); // new deadline
    
        // Simulate update by the creator
        taskService.validateCreator("Bearer valid-token", task.getId());
    
        Task taskToUpdate = taskRepository.findById(task.getId()).orElse(null);
        assertNotNull(taskToUpdate);
        taskToUpdate.setName(updatedTask.getName());
        taskToUpdate.setValue(updatedTask.getValue());
        taskToUpdate.setDeadline(updatedTask.getDeadline());
    
        taskRepository.save(taskToUpdate);
    
        // Verify the update was successful
        Task fetchedTask = taskRepository.findById(task.getId()).orElse(null);
        assertNotNull(fetchedTask);
        assertEquals("Updated Task Name", fetchedTask.getName());
        assertEquals(25, fetchedTask.getValue());
    }
    
    
    @Test
    void updateTask_notCreator_throwsForbiddenException() {
        // Create a second user (not the creator)
        User anotherUser = new User();
        anotherUser.setUsername("anotherUser");
        anotherUser.setName("Another User");
        anotherUser.setPassword("password123");
        anotherUser.setColor(ColorID.C2);
        anotherUser.setXp(0);
        anotherUser.setLevel(1);
        anotherUser.setStatus(UserStatus.ONLINE);
        anotherUser.setToken("another-token");
        anotherUser.setCreationDate(new Date());
        userRepository.save(anotherUser);
    
        // Create a task with testUser as the creator
        Task task = new Task();
        task.setName("Original Task");
        task.setCreationDate(new Date());
        task.setDeadline(new Date(System.currentTimeMillis() + 3600 * 1000));
        task.setValue(10);
        task.setActiveStatus(true);
        task.setPaused(false);
        task.setcreatorId(testUser.getId());
        taskRepository.save(task);
    
        // Attempt to update the task as anotherUser (not the creator)
        assertThrows(ResponseStatusException.class, 
            () -> taskService.validateCreator("Bearer another-token", task.getId()));
    }

    @Test
    void deleteTask_creator_success() {
        // Create a task by testUser
        Task task = new Task();
        task.setName("Task to be Deleted");
        task.setCreationDate(new Date());
        task.setDeadline(new Date(System.currentTimeMillis() + 3600 * 1000));
        task.setValue(30);
        task.setActiveStatus(true);
        task.setPaused(false);
        task.setcreatorId(testUser.getId());
        task = taskRepository.save(task);
    
        // Simulate delete by the creator
        taskService.validateCreator("Bearer valid-token", task.getId());
        taskRepository.deleteById(task.getId());
    
        // Ensure task is deleted
        assertFalse(taskRepository.findById(task.getId()).isPresent());
    }

    @Test
    void deleteTask_notCreator_throwsForbiddenException() {
        // Create a second user
        User anotherUser = new User();
        anotherUser.setUsername("secondUser");
        anotherUser.setName("Second User");
        anotherUser.setPassword("pass456");
        anotherUser.setColor(ColorID.C3);
        anotherUser.setXp(0);
        anotherUser.setLevel(1);
        anotherUser.setStatus(UserStatus.ONLINE);
        anotherUser.setToken("second-token");
        anotherUser.setCreationDate(new Date());
        userRepository.save(anotherUser);
    
        // Create a task owned by testUser
        Task task = new Task();
        task.setName("Task to Delete");
        task.setCreationDate(new Date());
        task.setDeadline(new Date(System.currentTimeMillis() + 3600 * 1000));
        task.setValue(20);
        task.setActiveStatus(true);
        task.setPaused(false);
        task.setcreatorId(testUser.getId());
        taskRepository.save(task);
    
        // Try to validate delete access with the wrong user
        assertThrows(ResponseStatusException.class, () -> {
            taskService.validateCreator("Bearer second-token", task.getId());
        });
    }
    
    
}
