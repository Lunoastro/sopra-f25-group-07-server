package ch.uzh.ifi.hase.soprafs24.service.Task;

import ch.uzh.ifi.hase.soprafs24.constant.ColorID;
import ch.uzh.ifi.hase.soprafs24.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs24.entity.Task;
import ch.uzh.ifi.hase.soprafs24.entity.User;
import ch.uzh.ifi.hase.soprafs24.repository.TaskRepository;
import ch.uzh.ifi.hase.soprafs24.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs24.rest.dto.task.TaskPostDTO;
import ch.uzh.ifi.hase.soprafs24.service.TaskService;
import ch.uzh.ifi.hase.soprafs24.service.UserService;

import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

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
        testUser.setTeamId(1L);
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
        testUser.setTeamId(1L);
        // given a valid task and user token
        TaskPostDTO validTaskPostDTO = new TaskPostDTO();
        validTaskPostDTO.setName("New Task");
        validTaskPostDTO.setDeadline(new Date(System.currentTimeMillis() + 3600 * 1000)); // set a future deadline

        Task validTask = new Task();
        validTask.setName(validTaskPostDTO.getName());
        validTask.setDeadline(validTaskPostDTO.getDeadline());

        // mock the user repository to return the test user when the valid token is
        // provided
        Mockito.when(userRepository.findByToken("some-valid-token")).thenReturn(testUser);
        Mockito.when(userService.validateToken("some-valid-token")).thenReturn(true);
        Mockito.when(taskRepository.findTaskById(Mockito.anyLong())).thenReturn(null); // ensure the task doesn't
                                                                                       // already exist

        // mock save method to return the task itself after saving
        Mockito.when(taskRepository.save(Mockito.any(Task.class))).thenAnswer(i -> i.getArgument(0));

        // when the user is valid and not already assigned
        Task createdTask = taskService.createTask(validTask, "Bearer some-valid-token");

        // then verify the task is created correctly and saved
        assertNotNull(createdTask);
        assertEquals("New Task", createdTask.getName());
        assertEquals(validTaskPostDTO.getDeadline(), createdTask.getDeadline());
        Mockito.verify(taskRepository, Mockito.times(1)).save(Mockito.any(Task.class)); // verifying save method is
                                                                                        // called
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
        assertThrows(ResponseStatusException.class,
                () -> taskService.createTask(existingTask, "Bearer some-valid-token"));
    }

    @Test
    void createTask_invalidToken_throwsUnauthorizedException() {
        // given an invalid token
        Mockito.when(userRepository.findByToken("invalid-token")).thenReturn(null); // mock user not found for invalid
                                                                                    // token

        // when
        assertThrows(ResponseStatusException.class, () -> taskService.createTask(testTask, "Bearer invalid-token"));
    }

    @Test
    void validatePostDto_validInput_success() {
        // given
        TaskPostDTO validTaskPostDTO = new TaskPostDTO();
        validTaskPostDTO.setName("Valid Task");
        validTaskPostDTO.setValue(10);
        validTaskPostDTO.setDeadline(new Date(System.currentTimeMillis() + 3600 * 1000)); // Future deadline

        // when & then (no exception should be thrown)
        assertDoesNotThrow(() -> taskService.validatePostDto(validTaskPostDTO));
    }

    @Test
    void validatePostDto_nullName_throwsBadRequestException() {
        // given
        TaskPostDTO invalidTaskPostDTO = new TaskPostDTO();
        invalidTaskPostDTO.setName(null); // Invalid name
        invalidTaskPostDTO.setDeadline(new Date(System.currentTimeMillis() + 3600 * 1000)); // Future deadline

        // when & then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> taskService.validatePostDto(invalidTaskPostDTO));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("Task name cannot be null or empty", exception.getReason());
    }

    @Test
    void validatePostDto_emptyName_throwsBadRequestException() {
        // given
        TaskPostDTO invalidTaskPostDTO = new TaskPostDTO();
        invalidTaskPostDTO.setName(""); // Invalid name
        invalidTaskPostDTO.setDeadline(new Date(System.currentTimeMillis() + 3600 * 1000)); // Future deadline

        // when & then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> taskService.validatePostDto(invalidTaskPostDTO));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("Task name cannot be null or empty", exception.getReason());
    }

    @Test
    void validatePostDto_pastDeadline_throwsIllegalArgumentException() {
        // given
        TaskPostDTO invalidTaskPostDTO = new TaskPostDTO();
        invalidTaskPostDTO.setName("Valid Task");
        invalidTaskPostDTO.setValue(10);
        invalidTaskPostDTO.setDeadline(new Date(System.currentTimeMillis() - 3600 * 1000)); // Past deadline

        // when & then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> taskService.validatePostDto(invalidTaskPostDTO));
        assertEquals("Invalid or past deadline provided.", exception.getMessage());
    }

    @Test
    void validateToBeEditedFields_validFields_success() {
        // given
        Task existingTask = new Task();
        existingTask.setName("Old Task");
        existingTask.setDescription("Old Description");
        existingTask.setDeadline(new Date(System.currentTimeMillis() + 3600 * 1000)); // Future deadline
        existingTask.setColor(ColorID.C1);
        existingTask.setActiveStatus(true);

        Task updatedTask = new Task();
        updatedTask.setName("New Task");
        updatedTask.setDescription("New Description");
        updatedTask.setDeadline(new Date(System.currentTimeMillis() + 7200 * 1000)); // Future deadline

        // when
        taskService.validateToBeEditedFields(existingTask, updatedTask);

        // then
        assertEquals("New Task", existingTask.getName());
        assertEquals("New Description", existingTask.getDescription());
        assertEquals(updatedTask.getDeadline(), existingTask.getDeadline());
        assertEquals(ColorID.C1, existingTask.getColor());
        assertTrue(existingTask.getActiveStatus());
    }

    @Test
    void validateToBeEditedFields_nullFields_noChanges_additonal_Task() {
        // given
        Task existingTask = new Task();
        existingTask.setName("Old Task");
        existingTask.setDescription("Old Description");
        existingTask.setDeadline(new Date(System.currentTimeMillis() + 3600 * 1000)); // Future deadline
        existingTask.setColor(ColorID.C1);
        existingTask.setActiveStatus(true);

        Task updatedTask = new Task();
        updatedTask.setName("Old Task");

        // when
        taskService.validateToBeEditedFields(existingTask, updatedTask);

        // then
        assertEquals("Old Task", existingTask.getName());
        assertEquals("Old Description", existingTask.getDescription());
        assertNotNull(existingTask.getDeadline());
        assertEquals(ColorID.C1, existingTask.getColor());
        assertTrue(existingTask.getActiveStatus());
    }

    @Test
    void validateToBeEditedFields_nullTask_throwsBadRequestException() {
        // given
        Task updatedTask = new Task();
        updatedTask.setName("New Task");

        // when & then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> taskService.validateToBeEditedFields(null, updatedTask));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("Task cannot be null", exception.getReason());
    }

        @Test
    void validateCreator_validInputs_success() {
        
        Task testTask = new Task();
        testTask.setId(1L);
        testTask.setName("Test Task");
        testTask.setCreationDate(new Date());
        testTask.setcreatorId(testUser.getId());
        testTask.setDeadline(new Date(System.currentTimeMillis() + 3600 * 1000));
        taskRepository.save(testTask);
        taskRepository.flush(); // Ensure the task is saved before validation
        // No mocking needed; rely on the actual repository behavior
        
        // Save the task to the repository
        // When & Then (no exception should be thrown)
        when(userRepository.findByToken("valid-token")).thenReturn(testUser);
        when(taskRepository.findTaskById(testTask.getId())).thenReturn(testTask);
        assertDoesNotThrow(() -> taskService.validateCreator("Bearer valid-token", testTask.getId()));
    }

    @Test
    void validateCreator_userNotFound_throwsUnauthorizedException() {
        // Given a task but no user
        Task testTask = new Task();
        testTask.setId(1L);
        testTask.setName("Test Task");
        testTask.setCreationDate(new Date());
        testTask.setDeadline(new Date(System.currentTimeMillis() + 3600 * 1000));
        testTask.setcreatorId(1L);
        taskRepository.save(testTask);

        // When & Then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> taskService.validateCreator("Bearer invalid-token", testTask.getId()));
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        assertEquals("User not found", exception.getReason());
    }

    @Test
    void validateCreator_taskNotFound_throwsNotFoundException() {
    
        // When & Then
        when(userRepository.findByToken("valid-token")).thenReturn(testUser);
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> taskService.validateCreator("Bearer valid-token", 999L)); // Non-existent task ID
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("Task not found", exception.getReason());
    }

    @Test
    void validateCreator_notAuthorized_throwsForbiddenException() {
        testUser.setStatus(UserStatus.OFFLINE);
        userRepository.save(testUser);
        
        Task testTask = new Task();
        testTask.setId(1L);
        testTask.setName("Test Task");
        testTask.setCreationDate(new Date());
        testTask.setDeadline(new Date(System.currentTimeMillis() + 3600 * 1000));
        testTask.setcreatorId(2L); // Different creator ID
        taskRepository.save(testTask);

        // When & Then
        
        when(userRepository.findByToken("valid-token")).thenReturn(testUser);
        when(taskRepository.findTaskById(testTask.getId())).thenReturn(testTask);
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> taskService.validateCreator("Bearer valid-token", testTask.getId()));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("Not authorized to edit this task", exception.getReason());
    }

    @Test
    void validateToBeEditedFields_taskNameCanBeUpdated_success() {
        // given
        Task existingTask = new Task();
        existingTask.setName("Old Task");
    
        Task updatedTask = new Task();
        updatedTask.setName("Updated Task");
    
        // when
        taskService.validateToBeEditedFields(existingTask, updatedTask);
    
        // then
        assertEquals("Updated Task", existingTask.getName());
    }

    @Test
    void validateToBeEditedFields_taskNameCannotBeEmpty_failure() {
        Task existingTask = new Task();
        existingTask.setName("Old Task");

        Task updatedTask = new Task();
        updatedTask.setName("");  // Invalid name (empty string)

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
        () -> taskService.validateToBeEditedFields(existingTask, updatedTask));  // Service should throw an exception

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());  // We expect a 400 BAD_REQUEST

        assertEquals("Task name cannot be empty", exception.getReason());  
    }
    
    @Test
    void validateToBeEditedFields_taskAssignmentCannotBeReassignedWithoutUnassigning_first() {
        Task existingTask = new Task();
        existingTask.setName("Important Task");
        existingTask.setIsAssignedTo(5L);

        Task updatedTask = new Task();
        updatedTask.setName("Important Task");
        updatedTask.setIsAssignedTo(10L);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
            () -> taskService.validateToBeEditedFields(existingTask, updatedTask));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("Task already claimed (Needs to be released first)", exception.getReason());
    }

    @Test
    void validateToBeEditedFields_taskAssignmentCanBeClaimed_success() {
        Task existingTask = new Task();
        existingTask.setName("Unclaimed Task");
        existingTask.setIsAssignedTo(null);

        Task updatedTask = new Task();
        updatedTask.setName("Unclaimed Task");
        updatedTask.setIsAssignedTo(10L);

        taskService.validateToBeEditedFields(existingTask, updatedTask);

        assertEquals(10L, existingTask.getIsAssignedTo());
    }
}
