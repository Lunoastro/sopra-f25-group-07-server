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
import java.util.List;
import java.util.Calendar;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import ch.uzh.ifi.hase.soprafs24.service.CalendarService;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserService userService;

    @Mock
    private CalendarService calendarService;

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
        testUser.setToken("some-valid-token");

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

        Task claimedTask = taskService.claimTask(testTask, "some-valid-token");

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
        assertThrows(ResponseStatusException.class, () -> taskService.claimTask(testTask, "some-valid-token"));
    }

    @Test
    void claimTask_userHasNoColor_throwsBadRequestException() {
        // given a user with no color set
        testUser.setColor(null);

        // when
        Mockito.when(userRepository.findByToken("some-valid-token")).thenReturn(testUser);
        Mockito.when(userService.validateToken("some-valid-token")).thenReturn(true); // Mock validateToken

        // then
        assertThrows(ResponseStatusException.class, () -> taskService.claimTask(testTask, "some-valid-token"));
    }

    @Test
    void claimTask_invalidToken_throwsUnauthorizedException() {
        // given an invalid token
        Mockito.when(userRepository.findByToken("invalid-token")).thenReturn(null);

        assertThrows(ResponseStatusException.class, () -> taskService.claimTask(testTask, "invalid-token"));
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
        Task createdTask = taskService.createTask(validTask, "some-valid-token");

        // then verify the task is created correctly and saved
        assertNotNull(createdTask);
        assertEquals("New Task", createdTask.getName());
        assertEquals(validTaskPostDTO.getDeadline(), createdTask.getDeadline());
        Mockito.verify(taskRepository, Mockito.times(1)).save(Mockito.any(Task.class)); // verifying save method is
                                                                                        // called
    }

    @Test
    void createTaskAlreadyExists() {
        // given a task that already exists
        Task existingTask = new Task();
        existingTask.setId(1L);
        Mockito.when(taskRepository.findTaskById(1L)).thenReturn(existingTask); // mock that task already exists

        // when
        Mockito.when(userRepository.findByToken("some-valid-token")).thenReturn(testUser);
        Mockito.when(userService.validateToken("some-valid-token")).thenReturn(true);

        // then
        assertThrows(ResponseStatusException.class,
                () -> taskService.createTask(existingTask, "some-valid-token"));
    }

    @Test
    void createTask_invalidToken_throwsUnauthorizedException() {
        // given an invalid token
        Mockito.when(userRepository.findByToken("invalid-token")).thenReturn(null); // mock user not found for invalid
                                                                                    // token

        // when
        assertThrows(ResponseStatusException.class, () -> taskService.createTask(testTask, "invalid-token"));
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
        existingTask.setCreationDate(new Date());

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
        
        Task testingTask = new Task();
        testingTask.setId(1L);
        testingTask.setName("Test Task");
        testingTask.setCreationDate(new Date());
        testingTask.setcreatorId(testUser.getId());
        testingTask.setDeadline(new Date(System.currentTimeMillis() + 3600 * 1000));
        taskRepository.save(testingTask);
        taskRepository.flush(); // Ensure the task is saved before validation
        // No mocking needed; rely on the actual repository behavior
        
        // Save the task to the repository
        // When & Then (no exception should be thrown)
        when(userRepository.findByToken("valid-token")).thenReturn(testUser);
        when(taskRepository.findTaskById(testingTask.getId())).thenReturn(testingTask);
        assertDoesNotThrow(() -> taskService.validateCreator("valid-token", testingTask.getId()));
    }

    @Test
    void validateCreator_userNotFound_throwsUnauthorizedException() {
        // Given a task but no user
        Task testingTask = new Task();
        testingTask.setId(1L);
        testingTask.setName("Test Task");
        testingTask.setCreationDate(new Date());
        testingTask.setDeadline(new Date(System.currentTimeMillis() + 3600 * 1000));
        testingTask.setcreatorId(1L);
        taskRepository.save(testingTask);

        // When & Then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> taskService.validateCreator("invalid-token", testingTask.getId()));
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        assertEquals("User not found", exception.getReason());
    }

    @Test
    void validateCreator_taskNotFound_throwsNotFoundException() {
    
        // When & Then
        when(userRepository.findByToken("valid-token")).thenReturn(testUser);
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> taskService.validateCreator("valid-token", 999L)); // Non-existent task ID
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("Task not found", exception.getReason());
    }

    @Test
    void validateCreator_notAuthorized_throwsForbiddenException() {
        testUser.setStatus(UserStatus.OFFLINE);
        userRepository.save(testUser);
        
        Task testingTask = new Task();
        testingTask.setId(1L);
        testingTask.setName("Test Task");
        testingTask.setCreationDate(new Date());
        testingTask.setDeadline(new Date(System.currentTimeMillis() + 3600 * 1000));
        testingTask.setcreatorId(2L); // Different creator ID
        taskRepository.save(testingTask);

        // When & Then
        
        when(userRepository.findByToken("valid-token")).thenReturn(testUser);
        when(taskRepository.findTaskById(testingTask.getId())).thenReturn(testingTask);
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> taskService.validateCreator("valid-token", testingTask.getId()));
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

    @Test
    void updateAllTaskColors_success() {
        // given
        User testingUser = new User();
        testingUser.setId(42L);
        testingUser.setUsername("testingUser");
        testingUser.setColor(ColorID.C1);

        // Create a list of tasks assigned to this user
        Task task1 = new Task();
        task1.setId(1L);
        task1.setName("Task 1");
        task1.setIsAssignedTo(testingUser.getId());
        task1.setColor(ColorID.C2); // Different color initially

        Task task2 = new Task();
        task2.setId(2L);
        task2.setName("Task 2");
        task2.setIsAssignedTo(testingUser.getId());
        task2.setColor(ColorID.C3); // Different color initially

        List<Task> userTasks = List.of(task1, task2);

        // when
        when(taskRepository.findTaskByIsAssignedTo(testingUser.getId())).thenReturn(userTasks);
        when(taskRepository.saveAll(Mockito.anyList())).thenReturn(userTasks);

        taskService.updateAllTaskColors(testingUser);

        // then
        Mockito.verify(taskRepository).findTaskByIsAssignedTo(testingUser.getId());
        Mockito.verify(taskRepository).saveAll(Mockito.anyList());

        // Verify that both tasks now have the user's color
        ArgumentCaptor<List<Task>> taskCaptor = ArgumentCaptor.forClass(List.class);
        verify(taskRepository).saveAll(taskCaptor.capture());
        List<Task> capturedTasks = taskCaptor.getValue();
        
        assertEquals(2, capturedTasks.size());
        assertEquals(ColorID.C1, capturedTasks.get(0).getColor());
        assertEquals(ColorID.C1, capturedTasks.get(1).getColor());
    }

    @Test
    void deleteTask_success() {
        // given
        Long taskId = 1L;
        Task mockTask = new Task();
        mockTask.setId(taskId);
        mockTask.setName("Mock Task");
        mockTask.setcreatorId(42L);

        Mockito.when(taskRepository.findById(taskId)).thenReturn(Optional.of(mockTask));

        Mockito.doNothing().when(calendarService).syncSingleTask(mockTask, mockTask.getcreatorId());
        
        // when
        taskService.deleteTask(taskId);

        // then
        Mockito.verify(taskRepository).deleteById(taskId);
    }

    @Test
    void getTaskById_success() {
        // given
        Task task = new Task();
        task.setId(1L);
        task.setName("Test Task");

        // when
        when(taskRepository.findById(1L)).thenReturn(java.util.Optional.of(task));
        Task result = taskService.getTaskById(1L);

        // then
        assertEquals(task.getId(), result.getId());
        assertEquals(task.getName(), result.getName());
        Mockito.verify(taskRepository).findById(1L);
    }

    @Test
    void getTaskById_notFound_throwsNotFoundException() {
        // given
        Long taskId = 999L;

        // when
        when(taskRepository.findById(taskId)).thenReturn(java.util.Optional.empty());

        // then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> taskService.getTaskById(taskId));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("Task not found", exception.getReason());
    }

    @Test
    void pauseTask_success() {
        // given
        Task task = new Task();
        task.setId(1L);
        task.setName("Test Task");
        task.setPaused(false);
        task.setPausedDate(null);

        // when
        taskService.pauseTask(task);

        // then
        assertTrue(task.isPaused());
        assertNotNull(task.getPausedDate());
        Mockito.verify(taskRepository).save(task);
    }

    @Test
    void unpauseTask_success() {
        // given
        Task task = new Task();
        task.setId(1L);
        task.setName("Test Task");
        task.setPaused(true);
        task.setPausedDate(new Date());
        task.setUnpausedDate(null);

        // when
        taskService.unpauseTask(task);

        // then
        assertFalse(task.isPaused());
        assertNotNull(task.getUnpausedDate());
        Mockito.verify(taskRepository).save(task);
    }

    @Test
    void assignTask_success() {
        // given
        Task task = new Task();
        task.setId(1L);
        task.setName("Test Task");
        task.setIsAssignedTo(null); // Task is initially unassigned

        Long userId = 42L;

        // when
        taskService.assignTask(task, userId);

        // then
        assertEquals(userId, task.getIsAssignedTo());
        Mockito.verify(taskRepository).save(task);
    }

    @Test
    void unassignTask_success() {
        // given
        Task task = new Task();
        task.setId(1L);
        task.setName("Test Task");
        task.setIsAssignedTo(42L); // Task is initially assigned

        // when
        taskService.unassignTask(task);

        // then
        assertNull(task.getIsAssignedTo());
        Mockito.verify(taskRepository).save(task);
    }

    @Test
    void getFilteredTasks_noFilters_returnsAllTasks() {
        // given
        Task task1 = new Task();
        task1.setId(1L);
        task1.setName("Regular Task");
        task1.setActiveStatus(true);
        task1.setFrequency(null); // Not recurring

        Task task2 = new Task();
        task2.setId(2L);
        task2.setName("Recurring Task");
        task2.setActiveStatus(true);
        task2.setFrequency(7); // Recurring

        Task task3 = new Task();
        task3.setId(3L);
        task3.setName("Inactive Task");
        task3.setActiveStatus(false);
        task3.setFrequency(null); // Not recurring

        List<Task> allTasks = List.of(task1, task2, task3);

        // when
        when(taskRepository.findAll()).thenReturn(allTasks);
        List<Task> result = taskService.getFilteredTasks(null, null);

        // then
        assertEquals(3, result.size());
        assertTrue(result.contains(task1));
        assertTrue(result.contains(task2));
        assertTrue(result.contains(task3));
    }

    @Test
    void getFilteredTasks_activeFilter_returnsActiveTasks() {
        // given
        Task task1 = new Task();
        task1.setId(1L);
        task1.setName("Active Task 1");
        task1.setActiveStatus(true);

        Task task2 = new Task();
        task2.setId(2L);
        task2.setName("Active Task 2");
        task2.setActiveStatus(true);

        Task task3 = new Task();
        task3.setId(3L);
        task3.setName("Inactive Task");
        task3.setActiveStatus(false);

        List<Task> allTasks = List.of(task1, task2, task3);

        // when
        when(taskRepository.findAll()).thenReturn(allTasks);
        List<Task> result = taskService.getFilteredTasks(true, null);

        // then
        assertEquals(2, result.size());
        assertTrue(result.contains(task1));
        assertTrue(result.contains(task2));
        assertFalse(result.contains(task3));
    }

    @Test
    void getFilteredTasks_recurringFilter_returnsRecurringTasks() {
        // given
        Task task1 = new Task();
        task1.setId(1L);
        task1.setName("Regular Task");
        task1.setFrequency(null); // Not recurring

        Task task2 = new Task();
        task2.setId(2L);
        task2.setName("Recurring Task 1");
        task2.setFrequency(7); // Recurring weekly

        Task task3 = new Task();
        task3.setId(3L);
        task3.setName("Recurring Task 2");
        task3.setFrequency(30); // Recurring monthly

        List<Task> allTasks = List.of(task1, task2, task3);

        // when
        when(taskRepository.findAll()).thenReturn(allTasks);
        List<Task> result = taskService.getFilteredTasks(null, "recurring");

        // then
        assertEquals(2, result.size());
        assertFalse(result.contains(task1));
        assertTrue(result.contains(task2));
        assertTrue(result.contains(task3));
    }

    @Test
    void getFilteredTasks_bothFilters_returnsActiveRecurringTasks() {
        // given
        Task task1 = new Task();
        task1.setId(1L);
        task1.setName("Active Regular Task");
        task1.setActiveStatus(true);
        task1.setFrequency(null); // Not recurring

        Task task2 = new Task();
        task2.setId(2L);
        task2.setName("Active Recurring Task");
        task2.setActiveStatus(true);
        task2.setFrequency(7); // Recurring

        Task task3 = new Task();
        task3.setId(3L);
        task3.setName("Inactive Recurring Task");
        task3.setActiveStatus(false);
        task3.setFrequency(14); // Recurring

        Task task4 = new Task();
        task4.setId(4L);
        task4.setName("Inactive Regular Task");
        task4.setActiveStatus(false);
        task4.setFrequency(null); // Not recurring

        List<Task> allTasks = List.of(task1, task2, task3, task4);

        // when
        when(taskRepository.findAll()).thenReturn(allTasks);
        List<Task> result = taskService.getFilteredTasks(true, "recurring");

        // then
        assertEquals(1, result.size());
        assertFalse(result.contains(task1));
        assertTrue(result.contains(task2));
        assertFalse(result.contains(task3));
        assertFalse(result.contains(task4));
    }
    
    @Test
    void validateRecurringPostDto_validInput_success() {
        // given
        TaskPostDTO validTaskPostDTO = new TaskPostDTO();
        validTaskPostDTO.setName("Valid Recurring Task");
        validTaskPostDTO.setValue(10);
        validTaskPostDTO.setFrequency(7); // Weekly
        validTaskPostDTO.setStartDate(new Date(System.currentTimeMillis() + 86400000)); // Tomorrow
        validTaskPostDTO.setDaysVisible(4); // More than half of frequency

        // when & then
        assertDoesNotThrow(() -> taskService.validatePostDto(validTaskPostDTO));
    }

    @Test
    void validateRecurringPostDto_nullFrequency_throwsBadRequestException() {
        // given
        TaskPostDTO invalidTaskPostDTO = new TaskPostDTO();
        invalidTaskPostDTO.setName("Invalid Recurring Task");
        invalidTaskPostDTO.setValue(10);
        invalidTaskPostDTO.setFrequency(null); // Null frequency
        invalidTaskPostDTO.setStartDate(new Date(System.currentTimeMillis() + 86400000));
        invalidTaskPostDTO.setDaysVisible(4);

        // when & then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> taskService.validatePostDto(invalidTaskPostDTO));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("Task frequency cannot be null or less than or equal to 0", exception.getReason());
    }

    @Test
    void validateRecurringPostDto_zeroFrequency_throwsBadRequestException() {
        // given
        TaskPostDTO invalidTaskPostDTO = new TaskPostDTO();
        invalidTaskPostDTO.setName("Invalid Recurring Task");
        invalidTaskPostDTO.setValue(10);
        invalidTaskPostDTO.setFrequency(0); // Zero frequency
        invalidTaskPostDTO.setStartDate(new Date(System.currentTimeMillis() + 86400000));
        invalidTaskPostDTO.setDaysVisible(4);

        // when & then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> taskService.validatePostDto(invalidTaskPostDTO));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("Task frequency cannot be null or less than or equal to 0", exception.getReason());
    }

    @Test
    void validateRecurringPostDto_pastStartDate_throwsIllegalArgumentException() {
        // given
        TaskPostDTO invalidTaskPostDTO = new TaskPostDTO();
        invalidTaskPostDTO.setName("Invalid Recurring Task");
        invalidTaskPostDTO.setValue(10);
        invalidTaskPostDTO.setFrequency(7);
        invalidTaskPostDTO.setStartDate(new Date(System.currentTimeMillis() - 86400000)); // Yesterday
        invalidTaskPostDTO.setDaysVisible(4);

        // when & then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> taskService.validatePostDto(invalidTaskPostDTO));
        assertEquals("Invalid or past start date provided.", exception.getMessage());
    }

    // Tests for validateRecurringPutDto(Task, Task)
    @Test
    void validateRecurringPutDto_validUpdate_success() {
        // given
        Task existingTask = new Task();
        existingTask.setId(1L);
        existingTask.setName("Original Recurring Task");
        existingTask.setFrequency(14); // Bi-weekly
        existingTask.setDaysVisible(7);
        existingTask.setStartDate(new Date(System.currentTimeMillis() + 86400000));
        existingTask.setActiveStatus(true);

        Task updatedTask = new Task();
        updatedTask.setFrequency(7); // Change to weekly
        updatedTask.setDaysVisible(4); // Valid days visible
        updatedTask.setActiveStatus(false); // Change to inactive

        // when
        taskService.validateToBeEditedFields(existingTask, updatedTask);

        // then
        assertEquals(7, existingTask.getFrequency());
        assertEquals(4, existingTask.getDaysVisible());
        assertFalse(existingTask.getActiveStatus());
    }

    @Test
    void validateRecurringPutDto_zeroFrequency_throwsBadRequestException() {
        // given
        Task existingTask = new Task();
        existingTask.setId(1L);
        existingTask.setName("Original Recurring Task");
        existingTask.setFrequency(14);
        existingTask.setDaysVisible(7);
        existingTask.setStartDate(new Date(System.currentTimeMillis() + 86400000));

        Task updatedTask = new Task();
        updatedTask.setFrequency(0); // Invalid frequency

        // when & then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> taskService.validateToBeEditedFields(existingTask, updatedTask));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("Frequency must be greater than 0", exception.getReason());
    }

    @Test
    void validateRecurringPutDto_invalidDaysVisible_tooLarge_throwsBadRequestException() {
        // given
        Task existingTask = new Task();
        existingTask.setId(1L);
        existingTask.setName("Original Recurring Task");
        existingTask.setFrequency(14);
        existingTask.setDaysVisible(7);
        existingTask.setStartDate(new Date(System.currentTimeMillis() + 86400000));

        Task updatedTask = new Task();
        updatedTask.setDaysVisible(15); // Greater than frequency

        // when & then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> taskService.validateToBeEditedFields(existingTask, updatedTask));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("daysVisible must be at least half of the frequency (or 1 if frequency is 1) but not greater than the frequency", exception.getReason());
    }

    @Test
    void validateRecurringPutDto_pastStartDate_throwsBadRequestException() {
        // given
        Task existingTask = new Task();
        existingTask.setId(1L);
        existingTask.setName("Original Recurring Task");
        existingTask.setFrequency(14);
        existingTask.setDaysVisible(7);
        existingTask.setStartDate(new Date(System.currentTimeMillis() + 86400000));

        Task updatedTask = new Task();
        updatedTask.setStartDate(new Date(System.currentTimeMillis() - 86400000)); // Past date

        // when & then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> taskService.validateToBeEditedFields(existingTask, updatedTask));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("Start date must be in the future", exception.getReason());
    }

    // Tests for validateTaskInTeam(String, Long)
    @Test
    void validateTaskInTeam_sameTeam_success() {
        // given
        Task task = new Task();
        task.setId(1L);
        task.setTeamId(1L); // Same team as user

        User user = new User();
        user.setId(42L);
        user.setTeamId(1L);
        user.setToken("team-token");

        // when
        when(taskRepository.findTaskById(1L)).thenReturn(task);
        when(userRepository.findByToken("team-token")).thenReturn(user);

        // then - no exception should be thrown
        assertDoesNotThrow(() -> taskService.validateTaskInTeam("team-token", 1L));
    }

    @Test
    void validateTaskInTeam_differentTeam_throwsForbiddenException() {
        // given
        Task task = new Task();
        task.setId(1L);
        task.setTeamId(2L); // Different team from user

        User user = new User();
        user.setId(42L);
        user.setTeamId(1L);
        user.setToken("team-token");

        // when
        when(taskRepository.findTaskById(1L)).thenReturn(task);
        when(userRepository.findByToken("team-token")).thenReturn(user);

        // then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> taskService.validateTaskInTeam("team-token", 1L));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("Task does not belong to the team of the user", exception.getReason());
    }


    // Tests for validateRecurringEdit(String, Long)
    @Test
    void validateRecurringEdit_recurringTask_success() {
        // given
        Task recurringTask = new Task();
        recurringTask.setId(1L);
        recurringTask.setFrequency(7); // This makes it a recurring task

        // when
        when(taskRepository.findTaskById(1L)).thenReturn(recurringTask);

        // then - no exception should be thrown
        assertDoesNotThrow(() -> taskService.validateRecurringEdit("valid-token", 1L));
    }

    @Test
    void validateRecurringEdit_additionalTask_callsValidateCreator() {
        // given
        Task additionalTask = new Task();
        additionalTask.setId(1L);
        additionalTask.setFrequency(null); // This makes it an additional task
        additionalTask.setcreatorId(42L);

        User user = new User();
        user.setId(42L);
        user.setToken("valid-token");

        // when
        when(taskRepository.findTaskById(1L)).thenReturn(additionalTask);
        when(userRepository.findByToken("valid-token")).thenReturn(user);

        // Create a spy of the taskService to verify that validateCreator is called
        TaskService spyTaskService = spy(taskService);
        doNothing().when(spyTaskService).validateCreator("valid-token", 1L);

        // then
        spyTaskService.validateRecurringEdit("valid-token", 1L);
        verify(spyTaskService).validateCreator("valid-token", 1L);
    }

    @Test
    void validateRecurringEdit_additionalTask_notCreator_throwsForbiddenException() {
        // given
        Task additionalTask = new Task();
        additionalTask.setId(1L);
        additionalTask.setFrequency(null); // This makes it an additional task
        additionalTask.setcreatorId(99L); // Different creator

        User user = new User();
        user.setId(42L);
        user.setToken("valid-token");

        // when
        when(taskRepository.findTaskById(1L)).thenReturn(additionalTask);
        when(userRepository.findByToken("valid-token")).thenReturn(user);

        // then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> taskService.validateRecurringEdit("valid-token", 1L));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("Not authorized to edit this task", exception.getReason());
    }

}
