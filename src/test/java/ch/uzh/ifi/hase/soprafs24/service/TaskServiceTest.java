package ch.uzh.ifi.hase.soprafs24.service;

import ch.uzh.ifi.hase.soprafs24.constant.ColorID;
import ch.uzh.ifi.hase.soprafs24.entity.Task;
import ch.uzh.ifi.hase.soprafs24.entity.User;
import ch.uzh.ifi.hase.soprafs24.repository.TaskRepository;
import ch.uzh.ifi.hase.soprafs24.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs24.rest.dto.task.TaskPostDTO;

import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private TaskService taskService;

    private Task testTask;
    private User testUser;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);

        // Setup test user
        testUser = new User();
        testUser.setId(42L);
        testUser.setUsername("testUser");
        testUser.setColor(ColorID.C1);
        testUser.setToken("Bearer some-valid-token");

        // Setup test task (unassigned)
        testTask = new Task();
        testTask.setId(1L);
        testTask.setName("Test Task");
        testTask.setIsAssignedTo(null); // Task is unassigned
    }

    @Test
    void claimTask_validInputs_success() {
        // when
        Mockito.when(userRepository.findByToken("some-valid-token")).thenReturn(testUser);
        Mockito.when(userService.validateToken("some-valid-token")).thenReturn(true); // Mock validateToken
        Mockito.when(taskRepository.save(Mockito.any(Task.class))).thenAnswer(i -> i.getArgument(0));

        Task claimedTask = taskService.claimTask(testTask, "Bearer some-valid-token");

        // then
        Mockito.verify(taskRepository, Mockito.times(1)).save(Mockito.any(Task.class));
        assertEquals(42L, claimedTask.getIsAssignedTo());
        assertEquals(ColorID.C1, claimedTask.getColor());
    }

    @Test
    void claimTask_alreadyClaimed_throwsConflictException() {
        // given a task that is already claimed
        testTask.setIsAssignedTo(99L); // task already assigned to another user

        // when
        Mockito.when(userRepository.findByToken("some-valid-token")).thenReturn(testUser);
        Mockito.when(userService.validateToken("some-valid-token")).thenReturn(true);

        // then
        assertThrows(ResponseStatusException.class, () -> taskService.claimTask(testTask, "Bearer some-valid-token"));
    }

    @Test
    void claimTask_userHasNoColor_throwsBadRequestException() {
        // given a user with no color set
        testUser.setColor(null);

        // when
        Mockito.when(userRepository.findByToken("some-valid-token")).thenReturn(testUser);
        Mockito.when(userService.validateToken("some-valid-token")).thenReturn(true); // Mock validateToken

        // then
        assertThrows(ResponseStatusException.class, () -> taskService.claimTask(testTask, "Bearer some-valid-token"));
    }

    @Test
    void claimTask_invalidToken_throwsUnauthorizedException() {
        // given an invalid token
        Mockito.when(userRepository.findByToken("invalid-token")).thenReturn(null);


        assertThrows(ResponseStatusException.class, () -> taskService.claimTask(testTask, "Bearer invalid-token"));
    }

    @Test
    void createTask_validInput_success() {
        // given a valid task and user token
        TaskPostDTO validTaskPostDTO = new TaskPostDTO();
        validTaskPostDTO.setName("New Task");
        validTaskPostDTO.setDeadline(new Date(System.currentTimeMillis() + 3600 * 1000)); // set a future deadline
    
        Task validTask = new Task();
        validTask.setName(validTaskPostDTO.getName());
        validTask.setDeadline(validTaskPostDTO.getDeadline());
    
        // mock the user repository to return the test user when the valid token is provided
        Mockito.when(userRepository.findByToken("some-valid-token")).thenReturn(testUser);
        Mockito.when(userService.validateToken("some-valid-token")).thenReturn(true);
        Mockito.when(taskRepository.findTaskById(Mockito.anyLong())).thenReturn(null);  // ensure the task doesn't already exist
    
        // mock save method to return the task itself after saving
        Mockito.when(taskRepository.save(Mockito.any(Task.class))).thenAnswer(i -> i.getArgument(0));
    
        // when the user is valid and not already assigned
        Task createdTask = taskService.createTask(validTask, "Bearer some-valid-token");
    
        // then verify the task is created correctly and saved
        assertNotNull(createdTask);
        assertEquals("New Task", createdTask.getName());
        assertEquals(validTaskPostDTO.getDeadline(), createdTask.getDeadline());
        Mockito.verify(taskRepository, Mockito.times(1)).save(Mockito.any(Task.class)); // verifying save method is called
    }

    @Test
    void createTask_taskAlreadyExists_throwsConflictException() {
        // given a task that already exists
        Task existingTask = new Task();
        existingTask.setId(1L);
        Mockito.when(taskRepository.findTaskById(1L)).thenReturn(existingTask); // mock that task already exists

        // when
        Mockito.when(userRepository.findByToken("some-valid-token")).thenReturn(testUser);
        Mockito.when(userService.validateToken("some-valid-token")).thenReturn(true);

        // then
        assertThrows(ResponseStatusException.class, () -> taskService.createTask(existingTask, "Bearer some-valid-token"));
    }

    @Test
    void createTask_invalidToken_throwsUnauthorizedException() {
        // given an invalid token
        Mockito.when(userRepository.findByToken("invalid-token")).thenReturn(null); // mock user not found for invalid token

        // when
        assertThrows(ResponseStatusException.class, () -> taskService.createTask(testTask, "Bearer invalid-token"));
    }
    


}

