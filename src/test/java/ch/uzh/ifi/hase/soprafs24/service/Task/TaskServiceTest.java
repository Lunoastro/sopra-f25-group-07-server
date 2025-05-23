package ch.uzh.ifi.hase.soprafs24.service.Task;

import ch.uzh.ifi.hase.soprafs24.constant.ColorID;
import ch.uzh.ifi.hase.soprafs24.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs24.entity.Task;
import ch.uzh.ifi.hase.soprafs24.entity.Team;
import ch.uzh.ifi.hase.soprafs24.entity.User;
import ch.uzh.ifi.hase.soprafs24.repository.TaskRepository;
import ch.uzh.ifi.hase.soprafs24.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs24.repository.TeamRepository;
import ch.uzh.ifi.hase.soprafs24.rest.dto.task.TaskPostDTO;
import ch.uzh.ifi.hase.soprafs24.service.TaskService;
import ch.uzh.ifi.hase.soprafs24.service.UserService;
import ch.uzh.ifi.hase.soprafs24.service.WebSocketNotificationService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Comparator;

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
import java.util.Calendar;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskServiceTest {
    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private UserService userService;

    @Mock
    private CalendarService calendarService;

    @Mock
    private WebSocketNotificationService webSocketNotificationService;

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
    void createTask_setsDefaultStartDateIfNull() {
        // given
        Task task = new Task();
        task.setCreationDate(new Date());
        task.setFrequency(7); // Recurring task
        task.setStartDate(null); // Start date not provided

        User user = new User();
        user.setId(42L);
        user.setTeamId(1L);
        user.setToken("valid-token");
        user.setStatus(UserStatus.ONLINE);

        // when
        when(userService.validateToken("valid-token")).thenReturn(true);
        when(userRepository.findByToken("valid-token")).thenReturn(user);
        when(taskRepository.save(Mockito.any(Task.class))).thenAnswer(i -> i.getArgument(0));

        taskService.createTask(task, "valid-token");

        // then
        assertEquals(task.getCreationDate(), task.getStartDate(), "Default startDate should be set to creationDate");
        verify(taskRepository, Mockito.times(1)).save(task);
    }

    @Test
    void createTask_setsDefaultDaysVisibleIfNull() {
        // given
        Task task = new Task();
        task.setCreationDate(new Date());
        task.setFrequency(7); // Recurring task
        task.setDaysVisible(null); // Days visible not provided

        User user = new User();
        user.setId(42L);
        user.setTeamId(1L);
        user.setToken("valid-token");
        user.setStatus(UserStatus.ONLINE);

        // when
        when(userService.validateToken("valid-token")).thenReturn(true);
        when(userRepository.findByToken("valid-token")).thenReturn(user);
        when(taskRepository.save(Mockito.any(Task.class))).thenAnswer(i -> i.getArgument(0));

        taskService.createTask(task, "valid-token");

        // then
        assertEquals(3, task.getDaysVisible(), "Default daysVisible should be half of the frequency");
        verify(taskRepository, Mockito.times(1)).save(task);
    }

    @Test
    void createTask_noDefaultValuesNeeded() {
        // given
        Task task = new Task();
        task.setCreationDate(new Date());
        task.setStartDate(new Date());
        task.setDaysVisible(5); // Already set
        task.setFrequency(10); // Recurring task

        User user = new User();
        user.setId(42L);
        user.setStatus(UserStatus.ONLINE);
        user.setTeamId(1L);
        user.setToken("valid-token");

        // when
        when(userService.validateToken("valid-token")).thenReturn(true);
        when(userRepository.findByToken("valid-token")).thenReturn(user);
        when(taskRepository.save(Mockito.any(Task.class))).thenAnswer(i -> i.getArgument(0));

        taskService.createTask(task, "valid-token");

        // then
        assertEquals(5, task.getDaysVisible(), "daysVisible should remain unchanged");
        assertNotNull(task.getStartDate(), "startDate should remain unchanged");
        verify(taskRepository, Mockito.times(1)).save(task);
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
    void validatePostDto_pastDeadline_throwsResponseStatusException() {
        // given
        TaskPostDTO invalidTaskPostDTO = new TaskPostDTO();
        invalidTaskPostDTO.setName("Valid Task");
        invalidTaskPostDTO.setValue(10);
        // Set deadline to yesterday
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -1); // yesterday
        invalidTaskPostDTO.setDeadline(cal.getTime());
        // when & then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> taskService.validatePostDto(invalidTaskPostDTO));
        assertEquals("400 BAD_REQUEST \"Invalid or past deadline provided.\"", exception.getMessage());
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
        existingTask.setStartDate(new Date(System.currentTimeMillis() + 1000 * 60 * 60)); // Must not be null

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
        updatedTask.setName(""); // Invalid name (empty string)

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> taskService.validateToBeEditedFields(existingTask, updatedTask)); // Service should throw an
                                                                                        // exception

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus()); // We expect a 400 BAD_REQUEST

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
        User testingUser = new User();
        testingUser.setId(42L);
        testingUser.setUsername("testingUser");
        testingUser.setColor(ColorID.C1);
        Long taskId = 1L;
        Task mockTask = new Task();
        mockTask.setId(taskId);
        mockTask.setName("Mock Task");
        mockTask.setcreatorId(42L);

        Mockito.when(taskRepository.findById(taskId)).thenReturn(Optional.of(mockTask));

        Mockito.doNothing().when(calendarService).syncSingleTask(mockTask, mockTask.getcreatorId());

        // when
        taskService.deleteTask(taskId, 42L);

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
        Date futureDeadline = new Date(System.currentTimeMillis() + 3 * 24 * 60 * 60 * 1000); // 3 days from now

        Task task1 = new Task();
        task1.setId(1L);
        task1.setName("Active Task 1");
        task1.setActiveStatus(true);
        task1.setDaysVisible(3);
        task1.setDeadline(futureDeadline);

        Task task2 = new Task();
        task2.setId(2L);
        task2.setName("Active Task 2");
        task2.setActiveStatus(true);
        task2.setDaysVisible(3);
        task2.setDeadline(futureDeadline);

        Task task3 = new Task();
        task3.setId(3L);
        task3.setName("Inactive Task");
        task3.setActiveStatus(false);
        task3.setDaysVisible(1);
        task3.setDeadline(futureDeadline);

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
        Date futureDeadline = new Date(System.currentTimeMillis() + 3 * 24 * 60 * 60 * 1000); // 3 days from now

        Task task1 = new Task();
        task1.setId(1L);
        task1.setName("Active Regular Task");
        task1.setActiveStatus(true);
        task1.setFrequency(null); // Not recurring
        task1.setDaysVisible(2);
        task1.setDeadline(futureDeadline);

        Task task2 = new Task();
        task2.setId(2L);
        task2.setName("Active Recurring Task");
        task2.setActiveStatus(true);
        task2.setFrequency(7); // Recurring
        task2.setDaysVisible(3);
        task2.setDeadline(futureDeadline);

        Task task3 = new Task();
        task3.setId(3L);
        task3.setName("Inactive Recurring Task");
        task3.setActiveStatus(false);
        task3.setFrequency(14); // Recurring
        task3.setDaysVisible(1);
        task3.setDeadline(futureDeadline);

        Task task4 = new Task();
        task4.setId(4L);
        task4.setName("Inactive Regular Task");
        task4.setActiveStatus(false);
        task4.setFrequency(null); // Not recurring
        task4.setDaysVisible(2);
        task4.setDeadline(futureDeadline);

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
        validTaskPostDTO.setDaysVisible(2); // Less than half of frequency

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
    void validateRecurringPostDto_pastStartDate_throwsResponseStatusException() {
        // given
        TaskPostDTO invalidTaskPostDTO = new TaskPostDTO();
        invalidTaskPostDTO.setName("Invalid Recurring Task");
        invalidTaskPostDTO.setValue(10);
        invalidTaskPostDTO.setFrequency(7);
        invalidTaskPostDTO.setStartDate(new Date(System.currentTimeMillis() - 86400000)); // Yesterday
        invalidTaskPostDTO.setDaysVisible(4);

        // when & then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> taskService.validatePostDto(invalidTaskPostDTO));
        assertEquals("Start date must be today or in the future", exception.getReason());
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
        updatedTask.setDaysVisible(3); // Valid days visible
        updatedTask.setActiveStatus(false); // Change to inactive

        // when
        taskService.validateToBeEditedFields(existingTask, updatedTask);

        // then
        assertEquals(7, existingTask.getFrequency());
        assertEquals(3, existingTask.getDaysVisible());
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
        existingTask.setDaysVisible(10);
        existingTask.setStartDate(new Date(System.currentTimeMillis() + 86400000));

        Task updatedTask = new Task();
        updatedTask.setDaysVisible(15); // Greater than frequency

        // when & then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> taskService.validateToBeEditedFields(existingTask, updatedTask));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals(
                "daysVisible can be at most half of the frequency (or 1 if frequency is 1) but not lower than 1",
                exception.getReason());
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
        assertEquals("Start date must be today or in the future", exception.getReason());
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

    @Test
    void validateRecurringPutDto_validInputs_success() {
        // given
        Task existingTask = new Task();
        existingTask.setFrequency(10);
        existingTask.setDaysVisible(5);
        existingTask.setStartDate(new Date(System.currentTimeMillis() + 3600 * 1000)); // Future start date
        existingTask.setActiveStatus(true);

        Task updatedTask = new Task();
        updatedTask.setFrequency(15);
        updatedTask.setDaysVisible(7);
        updatedTask.setStartDate(new Date(System.currentTimeMillis() + 7200 * 1000)); // Future start date
        updatedTask.setActiveStatus(false);

        // when
        taskService.validateRecurringPutDto(existingTask, updatedTask);

        // then
        assertEquals(15, existingTask.getFrequency());
        assertEquals(7, existingTask.getDaysVisible());
        assertEquals(updatedTask.getStartDate(), existingTask.getStartDate());
        assertFalse(existingTask.getActiveStatus());
    }

    @Test
    void validateRecurringPutDto_invalidFrequency_throwsBadRequestException() {
        // given
        Task existingTask = new Task();
        existingTask.setFrequency(10);

        Task updatedTask = new Task();
        updatedTask.setFrequency(0); // Invalid frequency

        // when & then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> taskService.validateRecurringPutDto(existingTask, updatedTask));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("Frequency must be greater than 0", exception.getReason());
    }

    @Test
    void validateRecurringPutDto_invalidDaysVisible_throwsBadRequestException() {
        // given
        Task existingTask = new Task();
        existingTask.setFrequency(10);

        Task updatedTask = new Task();
        updatedTask.setDaysVisible(6); // Invalid daysVisible (more than half of frequency)

        // when & then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> taskService.validateRecurringPutDto(existingTask, updatedTask));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals(
                "daysVisible can be at most half of the frequency (or 1 if frequency is 1) but not lower than 1",
                exception.getReason());
    }

    @Test
    void validateRecurringPutDto_nullFields_noChanges() {
        // given
        Task existingTask = new Task();
        existingTask.setFrequency(10);
        existingTask.setDaysVisible(5);
        existingTask.setStartDate(new Date(System.currentTimeMillis() + 3600 * 1000)); // Future start date
        existingTask.setActiveStatus(true);

        Task updatedTask = new Task(); // All fields are null

        // when
        taskService.validateRecurringPutDto(existingTask, updatedTask);

        // then
        assertEquals(10, existingTask.getFrequency());
        assertEquals(5, existingTask.getDaysVisible());
        assertNotNull(existingTask.getStartDate());
        assertTrue(existingTask.getActiveStatus());
    }

    @Test
    void pauseAllTasksInTeam_allTasksPaused_success() {
        // given
        Task task1 = new Task();
        task1.setId(1L);
        task1.setPaused(false);

        Task task2 = new Task();
        task2.setId(2L);
        task2.setPaused(false);

        List<Task> tasks = List.of(task1, task2);

        // when
        when(taskRepository.findAll()).thenReturn(tasks);

        taskService.pauseAllTasksInTeam();

        // then
        assertTrue(task1.isPaused());
        assertNotNull(task1.getPausedDate());
        assertTrue(task2.isPaused());
        assertNotNull(task2.getPausedDate());
        verify(taskRepository, times(1)).saveAll(tasks);
    }

    @Test
    void autodistributeTasks_success() {
        // given
        Long teamId = 1L;

        // Setup team
        Team team = new Team();
        team.setId(teamId);
        List<Long> memberIds = List.of(1L, 2L, 3L);
        team.setMembers(memberIds);

        // Setup team members
        User user1 = new User();
        user1.setId(1L);
        user1.setXp(100);
        user1.setColor(ColorID.C1);

        User user2 = new User();
        user2.setId(2L);
        user2.setXp(50);
        user2.setColor(ColorID.C2);

        User user3 = new User();
        user3.setId(3L);
        user3.setXp(200);
        user3.setColor(ColorID.C3);

        List<User> users = new ArrayList<>(List.of(user1, user2, user3));

        // Setup tasks
        Task task1 = new Task();
        task1.setId(1L);
        task1.setTeamId(teamId);
        task1.setIsAssignedTo(null); // Unclaimed
        task1.setValue(20);
        task1.setActiveStatus(true);

        Task task2 = new Task();
        task2.setId(2L);
        task2.setTeamId(teamId);
        task2.setIsAssignedTo(null); // Unclaimed
        task2.setValue(30);
        task2.setActiveStatus(true);

        Task task3 = new Task();
        task3.setId(3L);
        task3.setTeamId(teamId);
        task3.setIsAssignedTo(null); // Unclaimed
        task3.setValue(10);
        task3.setActiveStatus(true);

        Task task4 = new Task();
        task4.setId(4L);
        task4.setTeamId(teamId);
        task4.setIsAssignedTo(5L); // Already claimed
        task4.setValue(40);
        task4.setActiveStatus(true);

        Task task5 = new Task();
        task5.setId(5L);
        task5.setTeamId(2L); // Different team
        task5.setIsAssignedTo(null);
        task5.setValue(50);
        task5.setActiveStatus(true);

        // Änderung: Verwendung von ArrayList statt List.of()
        List<Task> allTasks = new ArrayList<>(Arrays.asList(task1, task2, task3, task4, task5));

        // when
        when(teamRepository.findTeamById(teamId)).thenReturn(team);
        when(userRepository.findAllById(memberIds)).thenReturn(users);
        when(taskRepository.findAll()).thenReturn(allTasks);

        // For the getFilteredTasks internal method call
        // Create a spy to intercept the getFilteredTasks call within the method
        TaskService spyTaskService = spy(taskService);
        doReturn(allTasks).when(spyTaskService).getFilteredTasks(true, null);

        // Mock notification service
        doNothing().when(webSocketNotificationService).notifyTeamMembers(anyLong(), anyString(), any());

        List<Task> result = spyTaskService.autodistributeTasks(teamId);

        // then
        assertEquals(3, result.size());

        // Verify the tasks were assigned in expected order (by value) to users (by XP)
        // Users should be assigned in order: user2 (XP=50), user1 (XP=100), user3
        // (XP=200)
        assertEquals(2L, task2.getIsAssignedTo()); // Highest value task to lowest XP user
        assertEquals(ColorID.C2, task2.getColor());

        assertEquals(1L, task1.getIsAssignedTo()); // Second highest to second lowest
        assertEquals(ColorID.C1, task1.getColor());

        assertEquals(3L, task3.getIsAssignedTo()); // Lowest value to highest XP
        assertEquals(ColorID.C3, task3.getColor());

        // Verify the repositories were called correctly
        verify(taskRepository, times(3)).save(any(Task.class));
        verify(userRepository, times(3)).save(any(User.class));
    }

    @Test
    void autodistributeTasks_noTasksRemainUnclaimed() {
        // given
        Long teamId = 1L;

        // Setup team
        Team team = new Team();
        team.setId(teamId);
        List<Long> memberIds = List.of(1L, 2L);
        team.setMembers(memberIds);

        // Setup team members
        User user1 = new User();
        user1.setId(1L);
        user1.setXp(100);
        user1.setColor(ColorID.C1);

        User user2 = new User();
        user2.setId(2L);
        user2.setXp(50);
        user2.setColor(ColorID.C2);

        // Änderung: Verwendung von ArrayList statt List.of()
        List<User> users = new ArrayList<>(Arrays.asList(user1, user2));

        // Setup 5 unclaimed tasks - bereits als ArrayList implementiert, gut!
        List<Task> unclaimedTasks = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Task task = new Task();
            task.setId((long) i);
            task.setTeamId(teamId);
            task.setIsAssignedTo(null); // All unclaimed
            task.setValue(i * 10);
            task.setActiveStatus(true);
            unclaimedTasks.add(task);
        }

        // when
        when(teamRepository.findTeamById(teamId)).thenReturn(team);
        when(userRepository.findAllById(memberIds)).thenReturn(users);

        TaskService spyTaskService = spy(taskService);
        doReturn(unclaimedTasks).when(spyTaskService).getFilteredTasks(true, null);

        doNothing().when(webSocketNotificationService).notifyTeamMembers(anyLong(), anyString(), any());

        List<Task> result = spyTaskService.autodistributeTasks(teamId);

        // then
        assertEquals(5, result.size());

        // Check that no tasks remain unclaimed
        for (Task task : result) {
            assertNotNull(task.getIsAssignedTo(), "Task " + task.getId() + " should be assigned");
            assertNotNull(task.getColor(), "Task " + task.getId() + " should have a color");
        }

        // Check for round-robin assignment (with 2 users and 5 tasks)
        // User 2 (lowest XP) should get tasks with IDs 5, 3, 1
        // User 1 should get tasks with IDs 4, 2

        // Sort tasks by original value to check assignment pattern
        result.sort(Comparator.comparingInt(Task::getValue).reversed());

        assertEquals(2L, result.get(0).getIsAssignedTo()); // Highest value to user2
        assertEquals(1L, result.get(1).getIsAssignedTo()); // Second highest to user1
        assertEquals(2L, result.get(2).getIsAssignedTo()); // Third highest to user2
        assertEquals(1L, result.get(3).getIsAssignedTo()); // Fourth highest to user1
        assertEquals(2L, result.get(4).getIsAssignedTo()); // Fifth highest to user2
    }

    @Test
    void autodistributeTasks_teamNotFound_throwsNotFoundException() {
        // given
        Long teamId = 999L;

        // when
        when(teamRepository.findTeamById(teamId)).thenReturn(null);

        // then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> taskService.autodistributeTasks(teamId));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("Team not found with ID " + teamId, exception.getReason());
    }

    @Test
    void autodistributeTasks_noTeamMembers_throwsNotFoundException() {
        // given
        Long teamId = 1L;

        // Setup team
        Team team = new Team();
        team.setId(teamId);
        team.setMembers(List.of()); // Empty member list

        // when
        when(teamRepository.findTeamById(teamId)).thenReturn(team);
        when(userRepository.findAllById(List.of())).thenReturn(List.of());

        // then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> taskService.autodistributeTasks(teamId));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("No users found for team ID " + teamId, exception.getReason());
    }

    @Test
    void autodistributeTasks_noUnclaimedTasks_returnsEmptyList() {
        // given
        Long teamId = 1L;

        // Setup team
        Team team = new Team();
        team.setId(teamId);
        List<Long> memberIds = List.of(1L, 2L);
        team.setMembers(memberIds);

        // Setup team members
        User user1 = new User();
        user1.setId(1L);
        user1.setXp(100);
        User user2 = new User();
        user2.setId(2L);
        user2.setXp(50);
        List<User> users = List.of(user1, user2);

        // Setup tasks (all claimed)
        Task task1 = new Task();
        task1.setId(1L);
        task1.setTeamId(teamId);
        task1.setIsAssignedTo(1L); // Already claimed
        task1.setActiveStatus(true);

        Task task2 = new Task();
        task2.setId(2L);
        task2.setTeamId(teamId);
        task2.setIsAssignedTo(2L); // Already claimed
        task2.setActiveStatus(true);

        List<Task> allTasks = List.of(task1, task2);

        // when
        when(teamRepository.findTeamById(teamId)).thenReturn(team);
        when(userRepository.findAllById(memberIds)).thenReturn(users);

        TaskService spyTaskService = spy(taskService);
        doReturn(allTasks).when(spyTaskService).getFilteredTasks(true, null);

        List<Task> result = spyTaskService.autodistributeTasks(teamId);

        // then
        assertTrue(result.isEmpty());
        verify(taskRepository, never()).save(any(Task.class));
        verify(userRepository, never()).save(any(User.class));
        verify(webSocketNotificationService, never()).notifyTeamMembers(anyLong(), anyString(), any());
    }

    @Test
    void autodistributeTasks_multipleTeams_onlyDistributesForSpecifiedTeam() {
        // given
        Long teamId = 1L;
        Long otherTeamId = 2L;

        // Setup teams
        Team team = new Team();
        team.setId(teamId);
        List<Long> memberIds = List.of(1L, 2L);
        team.setMembers(memberIds);

        // Setup team members
        User user1 = new User();
        user1.setId(1L);
        user1.setXp(100);
        user1.setColor(ColorID.C1);

        User user2 = new User();
        user2.setId(2L);
        user2.setXp(50);
        user2.setColor(ColorID.C2);

        // Änderung: Verwendung von ArrayList statt List.of()
        List<User> users = new ArrayList<>(Arrays.asList(user1, user2));

        // Tasks for teamId
        Task task1 = new Task();
        task1.setId(1L);
        task1.setTeamId(teamId);
        task1.setIsAssignedTo(null); // Unclaimed
        task1.setValue(20);
        task1.setActiveStatus(true);

        Task task2 = new Task();
        task2.setId(2L);
        task2.setTeamId(teamId);
        task2.setIsAssignedTo(null); // Unclaimed
        task2.setValue(30);
        task2.setActiveStatus(true);

        // Tasks for otherTeamId
        Task task3 = new Task();
        task3.setId(3L);
        task3.setTeamId(otherTeamId);
        task3.setIsAssignedTo(null); // Unclaimed
        task3.setValue(40);
        task3.setActiveStatus(true);

        // Änderung: Verwendung von ArrayList statt List.of()
        List<Task> allTasks = new ArrayList<>(Arrays.asList(task1, task2, task3));

        // when
        when(teamRepository.findTeamById(teamId)).thenReturn(team);
        when(userRepository.findAllById(memberIds)).thenReturn(users);

        TaskService spyTaskService = spy(taskService);
        doReturn(allTasks).when(spyTaskService).getFilteredTasks(true, null);

        doNothing().when(webSocketNotificationService).notifyTeamMembers(anyLong(), anyString(), any());

        List<Task> result = spyTaskService.autodistributeTasks(teamId);

        // then
        assertEquals(2, result.size());

        // Verify only tasks for the specified team were distributed
        assertNotNull(task1.getIsAssignedTo());
        assertNotNull(task2.getIsAssignedTo());
        assertNull(task3.getIsAssignedTo()); // Other team's task should remain unclaimed

        verify(taskRepository, times(2)).save(any(Task.class));
        verify(userRepository, times(2)).save(any(User.class));
    }

    @Test
    void autodistributeTasks_moreTasksThanUsers_roundRobinDistribution() {
        // given
        Long teamId = 1L;

        // Setup team with 2 members
        Team team = new Team();
        team.setId(teamId);
        List<Long> memberIds = List.of(1L, 2L);
        team.setMembers(memberIds);

        // Setup team members
        User user1 = new User();
        user1.setId(1L);
        user1.setXp(100);
        user1.setColor(ColorID.C1);

        User user2 = new User();
        user2.setId(2L);
        user2.setXp(50);
        user2.setColor(ColorID.C2);

        // Änderung: Verwendung von ArrayList statt List.of()
        List<User> users = new ArrayList<>(Arrays.asList(user1, user2));

        // Setup 5 unclaimed tasks - bereits als ArrayList implementiert, gut!
        List<Task> unclaimedTasks = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Task task = new Task();
            task.setId((long) i);
            task.setTeamId(teamId);
            task.setIsAssignedTo(null);
            task.setValue(i * 10);
            task.setActiveStatus(true);
            unclaimedTasks.add(task);
        }

        // when
        when(teamRepository.findTeamById(teamId)).thenReturn(team);
        when(userRepository.findAllById(memberIds)).thenReturn(users);

        TaskService spyTaskService = spy(taskService);
        doReturn(unclaimedTasks).when(spyTaskService).getFilteredTasks(true, null);

        doNothing().when(webSocketNotificationService).notifyTeamMembers(anyLong(), anyString(), any());

        List<Task> result = spyTaskService.autodistributeTasks(teamId);

        // then
        assertEquals(5, result.size());

        // Sort tasks by value descending to check the assignment pattern
        result.sort(Comparator.comparingInt(Task::getValue).reversed());

        // Check round-robin assignment pattern
        // First task to user with lowest XP (user2)
        assertEquals(2L, result.get(0).getIsAssignedTo());
        // Second task to next user (user1)
        assertEquals(1L, result.get(1).getIsAssignedTo());
        // Back to first user (user2)
        assertEquals(2L, result.get(2).getIsAssignedTo());
        // Back to second user (user1)
        assertEquals(1L, result.get(3).getIsAssignedTo());
        // Back to first user (user2)
        assertEquals(2L, result.get(4).getIsAssignedTo());

        // Verify correct number of save operations
        verify(taskRepository, times(5)).save(any(Task.class));
        verify(userRepository, times(5)).save(any(User.class));
    }

    @Test
    void luckyDrawTasks_success() {
        // given
        Long teamId = 1L;

        // Setup team
        Team team = new Team();
        team.setId(teamId);
        List<Long> memberIds = List.of(1L, 2L, 3L);
        team.setMembers(memberIds);

        // Setup team members
        User user1 = new User();
        user1.setId(1L);

        User user2 = new User();
        user2.setId(2L);

        User user3 = new User();
        user3.setId(3L);

        List<User> users = new ArrayList<>(Arrays.asList(user1, user2, user3));

        // Setup tasks
        Task task1 = new Task();
        task1.setId(1L);
        task1.setTeamId(teamId);
        task1.setIsAssignedTo(null); // Unclaimed
        task1.setValue(20);
        task1.setActiveStatus(true);
        task1.setLuckyDraw(false);

        Task task2 = new Task();
        task2.setId(2L);
        task2.setTeamId(teamId);
        task2.setIsAssignedTo(null); // Unclaimed
        task2.setValue(30);
        task2.setActiveStatus(true);
        task2.setLuckyDraw(false);

        Task task3 = new Task();
        task3.setId(3L);
        task3.setTeamId(teamId);
        task3.setIsAssignedTo(1L); // Already claimed
        task3.setValue(10);
        task3.setActiveStatus(true);
        task3.setLuckyDraw(false);

        Task task4 = new Task();
        task4.setId(4L);
        task4.setTeamId(2L); // Different team
        task4.setIsAssignedTo(null); // Unclaimed
        task4.setValue(40);
        task4.setActiveStatus(true);
        task4.setLuckyDraw(false);

        List<Task> allTasks = new ArrayList<>(Arrays.asList(task1, task2, task3, task4));

        // when
        when(teamRepository.findTeamById(teamId)).thenReturn(team);
        when(userRepository.findAllById(memberIds)).thenReturn(users);

        // For the getFilteredTasks internal method call
        TaskService spyTaskService = spy(taskService);
        doReturn(allTasks).when(spyTaskService).getFilteredTasks(true, null);

        List<Task> result = spyTaskService.luckyDrawTasks(teamId);

        // then
        assertEquals(2, result.size());

        // Verify tasks were flagged correctly
        for (Task task : result) {
            assertTrue(task.getLuckyDraw(), "Task should be marked for lucky draw");
            assertEquals(teamId, task.getTeamId(), "Task should be from the specified team");
            assertNull(task.getIsAssignedTo(), "Task should be unclaimed");
        }

        // Verify only eligible tasks were included (from our team, unclaimed)
        assertTrue(result.contains(task1));
        assertTrue(result.contains(task2));
        assertFalse(result.contains(task3)); // Already claimed
        assertFalse(result.contains(task4)); // Different team

        // Verify original tasks were properly updated
        assertTrue(task1.getLuckyDraw());
        assertTrue(task2.getLuckyDraw());
        assertFalse(task3.getLuckyDraw()); // Should be unchanged
        assertFalse(task4.getLuckyDraw()); // Should be unchanged
    }

    @Test
    void luckyDrawTasks_nullTeamId() {
        // given
        Long teamId = null;

        // when & then
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> taskService.luckyDrawTasks(teamId));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("User team ID cannot be null", exception.getReason());
    }

    @Test
    void luckyDrawTasks_noActiveTasks() {
        // given
        Long teamId = 1L;
        List<Task> emptyTaskList = new ArrayList<>();

        // when
        TaskService spyTaskService = spy(taskService);
        doReturn(emptyTaskList).when(spyTaskService).getFilteredTasks(true, null);

        List<Task> result = spyTaskService.luckyDrawTasks(teamId);

        // then
        assertTrue(result.isEmpty());
    }

    @Test
    void luckyDrawTasks_nullTaskList() {
        // given
        Long teamId = 1L;

        // when
        TaskService spyTaskService = spy(taskService);
        doReturn(null).when(spyTaskService).getFilteredTasks(true, null);

        List<Task> result = spyTaskService.luckyDrawTasks(teamId);

        // then
        assertTrue(result.isEmpty());
    }

    @Test
    void luckyDrawTasks_noUnclaimedTasks() {
        // given
        Long teamId = 1L;

        // Setup tasks - all already claimed
        Task task1 = new Task();
        task1.setId(1L);
        task1.setTeamId(teamId);
        task1.setIsAssignedTo(1L); // Claimed
        task1.setValue(20);
        task1.setActiveStatus(true);

        Task task2 = new Task();
        task2.setId(2L);
        task2.setTeamId(teamId);
        task2.setIsAssignedTo(2L); // Claimed
        task2.setValue(30);
        task2.setActiveStatus(true);

        List<Task> allTasks = new ArrayList<>(Arrays.asList(task1, task2));

        // when
        TaskService spyTaskService = spy(taskService);
        doReturn(allTasks).when(spyTaskService).getFilteredTasks(true, null);

        List<Task> result = spyTaskService.luckyDrawTasks(teamId);

        // then
        assertTrue(result.isEmpty());
    }

    @Test
    void luckyDrawTasks_differentTeams() {
        // given
        Long teamId = 1L;
        Long otherTeamId = 2L;

        // Setup tasks
        Task task1 = new Task();
        task1.setId(1L);
        task1.setTeamId(teamId);
        task1.setIsAssignedTo(null); // Unclaimed
        task1.setValue(20);
        task1.setActiveStatus(true);
        task1.setLuckyDraw(false);

        Task task2 = new Task();
        task2.setId(2L);
        task2.setTeamId(otherTeamId); // Different team
        task2.setIsAssignedTo(null); // Unclaimed
        task2.setValue(30);
        task2.setActiveStatus(true);
        task2.setLuckyDraw(false);

        List<Task> allTasks = new ArrayList<>(Arrays.asList(task1, task2));

        // when
        TaskService spyTaskService = spy(taskService);
        doReturn(allTasks).when(spyTaskService).getFilteredTasks(true, null);

        List<Task> result = spyTaskService.luckyDrawTasks(teamId);

        // then
        assertEquals(1, result.size());
        assertEquals(task1.getId(), result.get(0).getId());
        assertTrue(result.get(0).getLuckyDraw());
        assertFalse(task2.getLuckyDraw()); // Should remain unchanged
    }

    @Test
    void unlockAllTasksForUser_unlocksAllLockedTasks_success() {
        // given
        Long userId = 42L;
        Task lockedTask1 = new Task();
        lockedTask1.setId(1L);
        lockedTask1.setName("Locked Task 1");
        lockedTask1.setLockedByUser(userId);

        Task lockedTask2 = new Task();
        lockedTask2.setId(2L);
        lockedTask2.setName("Locked Task 2");
        lockedTask2.setLockedByUser(userId);

        Task unlockedTask = new Task();
        unlockedTask.setId(3L);
        unlockedTask.setName("Unlocked Task");
        unlockedTask.setLockedByUser(null);

        List<Task> allTasks = List.of(lockedTask1, lockedTask2, unlockedTask);

        when(taskRepository.findAll()).thenReturn(allTasks);
        when(taskRepository.save(any(Task.class))).thenAnswer(i -> i.getArgument(0));

        // when
        taskService.unlockAllTasksForUser(userId);

        // then
        assertNull(lockedTask1.getLockedByUser());
        assertNull(lockedTask2.getLockedByUser());
        assertNull(unlockedTask.getLockedByUser()); // should remain null
        verify(taskRepository, times(1)).save(lockedTask1);
        verify(taskRepository, times(1)).save(lockedTask2);
        verify(taskRepository, never()).save(unlockedTask);
    }

    @Test
    void unlockAllTasksForUser_noTasksLocked_nothingHappens() {
        // given
        Long userId = 42L;
        Task task1 = new Task();
        task1.setId(1L);
        task1.setName("Task 1");
        task1.setLockedByUser(null);

        Task task2 = new Task();
        task2.setId(2L);
        task2.setName("Task 2");
        task2.setLockedByUser(99L); // locked by another user

        List<Task> allTasks = List.of(task1, task2);

        when(taskRepository.findAll()).thenReturn(allTasks);

        // when
        taskService.unlockAllTasksForUser(userId);

        // then
        verify(taskRepository, never()).save(any(Task.class));
        assertNull(task1.getLockedByUser());
        assertEquals(99L, task2.getLockedByUser());
    }

    @Test
    void unlockTask_success_whenLockedByUser() {
        // given
        Long taskId = 1L;
        Long userId = 42L;
        Task lockedTask = new Task();
        lockedTask.setId(taskId);
        lockedTask.setLockedByUser(userId);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(lockedTask));
        when(taskRepository.save(any(Task.class))).thenAnswer(i -> i.getArgument(0));

        // when
        Task result = taskService.unlockTask(taskId, userId);

        // then
        assertNull(result.getLockedByUser());
        verify(taskRepository).findById(taskId);
        verify(taskRepository).save(lockedTask);
    }

    @Test
    void unlockTask_throwsNotFound_whenTaskDoesNotExist() {
        // given
        Long taskId = 99L;
        Long userId = 42L;
        when(taskRepository.findById(taskId)).thenReturn(Optional.empty());

        // when & then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> taskService.unlockTask(taskId, userId));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertTrue(exception.getReason().contains("Task not found"));
    }

    @Test
    void unlockTask_throwsBadRequest_whenTaskNotLocked() {
        // given
        Long taskId = 1L;
        Long userId = 42L;
        Task unlockedTask = new Task();
        unlockedTask.setId(taskId);
        unlockedTask.setLockedByUser(null);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(unlockedTask));

        // when & then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> taskService.unlockTask(taskId, userId));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("Task is not currently locked.", exception.getReason());
    }

    @Test
    void unlockTask_throwsForbidden_whenLockedByAnotherUser() {
        // given
        Long taskId = 1L;
        Long userId = 42L;
        Long anotherUserId = 99L;
        Task lockedTask = new Task();
        lockedTask.setId(taskId);
        lockedTask.setLockedByUser(anotherUserId);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(lockedTask));

        // when & then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> taskService.unlockTask(taskId, userId));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("You are not the user who locked this task.", exception.getReason());
    }

    @Test
    void lockTask_success_whenNotLocked() {
        // given
        Long taskId = 1L;
        Long userId = 42L;
        Task task = new Task();
        task.setId(taskId);
        task.setLockedByUser(null);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class))).thenAnswer(i -> i.getArgument(0));

        // when
        Task result = taskService.lockTask(taskId, userId);

        // then
        assertEquals(userId, result.getLockedByUser());
        verify(taskRepository).findById(taskId);
        verify(taskRepository).save(task);
    }

    @Test
    void lockTask_success_whenAlreadyLockedBySameUser() {
        // given
        Long taskId = 1L;
        Long userId = 42L;
        Task task = new Task();
        task.setId(taskId);
        task.setLockedByUser(userId);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class))).thenAnswer(i -> i.getArgument(0));

        // when
        Task result = taskService.lockTask(taskId, userId);

        // then
        assertEquals(userId, result.getLockedByUser());
        verify(taskRepository).findById(taskId);
        verify(taskRepository).save(task);
    }

    @Test
    void lockTask_conflict_whenLockedByAnotherUser() {
        // given
        Long taskId = 1L;
        Long userId = 42L;
        Long anotherUserId = 99L;
        Task task = new Task();
        task.setId(taskId);
        task.setLockedByUser(anotherUserId);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        // when & then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> taskService.lockTask(taskId, userId));
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("Task is already locked by another user.", exception.getReason());
        verify(taskRepository).findById(taskId);
        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void lockTask_notFound_throwsNotFoundException() {
        // given
        Long taskId = 123L;
        Long userId = 42L;
        when(taskRepository.findById(taskId)).thenReturn(Optional.empty());

        // when & then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> taskService.lockTask(taskId, userId));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("Task not found with ID: " + taskId, exception.getReason());
        verify(taskRepository).findById(taskId);
        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void checkLockedByUser_taskNotLocked_doesNotThrow() {
        Task task = new Task();
        task.setId(1L);
        task.setLockedByUser(null);

        assertDoesNotThrow(() -> taskService.checkLockedByUser(task, 42L));
    }

    @Test
    void checkLockedByUser_taskLockedByCurrentUser_doesNotThrow() {
        Task task = new Task();
        task.setId(1L);
        task.setLockedByUser(42L);

        assertDoesNotThrow(() -> taskService.checkLockedByUser(task, 42L));
    }

    @Test
    void checkLockedByUser_taskLockedByAnotherUser_throwsBadRequest() {
        Task task = new Task();
        task.setId(1L);
        task.setLockedByUser(99L);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> taskService.checkLockedByUser(task, 42L));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertTrue(exception.getReason().contains("Task is locked by another user"));
    }

    @Test
    void unpauseAllTasksInTeam_updatesAllTasksAndSaves() {
        // given
        Task task1 = new Task();
        task1.setId(1L);
        task1.setName("Task 1");
        task1.setPaused(true);
        task1.setDeadline(new Date(System.currentTimeMillis() + 1000000));
        task1.setPausedDate(new Date(System.currentTimeMillis() - 1000000));
        task1.setCreationDate(new Date(System.currentTimeMillis() - 2000000)); 

        Task task2 = new Task();
        task2.setId(2L);
        task2.setName("Task 2");
        task2.setPaused(true);
        task2.setDeadline(new Date(System.currentTimeMillis() + 2000000));
        task2.setPausedDate(new Date(System.currentTimeMillis() - 2000000));
        task2.setCreationDate(new Date(System.currentTimeMillis() - 3000000)); 

        List<Task> tasks = Arrays.asList(task1, task2);

        when(taskRepository.findAll()).thenReturn(tasks);
        when(taskRepository.saveAll(Mockito.anyList())).thenReturn(tasks);

        // when
        taskService.unpauseAllTasksInTeam();

        // then
        assertFalse(task1.isPaused());
        assertFalse(task2.isPaused());
        assertNotNull(task1.getUnpausedDate());
        assertNotNull(task2.getUnpausedDate());
        verify(taskRepository, times(1)).saveAll(tasks);
    }

    @Test
    void unpauseAllTasksInTeam_handlesEmptyTaskList() {
        // given
        when(taskRepository.findAll()).thenReturn(List.of());
        when(taskRepository.saveAll(Mockito.anyList())).thenReturn(List.of());

        // when
        taskService.unpauseAllTasksInTeam();

        // then
        verify(taskRepository, times(1)).saveAll(List.of());
    }

    @Test
    void updateTask_successfulUpdate_returnsUpdatedTask() {
        // given
        Task existingTask = new Task();
        existingTask.setId(1L);
        existingTask.setName("Old Task");
        existingTask.setcreatorId(42L);
        existingTask.setActiveStatus(true);
        existingTask.setPaused(false);

        Task updateDTO = new Task();
        updateDTO.setName("Updated Task");

        Long userId = 42L;

        // when
        Mockito.doNothing().when(calendarService).syncSingleTask(existingTask, existingTask.getcreatorId());
        Mockito.when(taskRepository.save(existingTask)).thenReturn(existingTask);

        Task updatedTask = taskService.updateTask(existingTask, updateDTO, userId);

        // then
        assertEquals("Updated Task", updatedTask.getName());
        Mockito.verify(calendarService).syncSingleTask(existingTask, existingTask.getcreatorId());
        Mockito.verify(taskRepository).save(existingTask);
        Mockito.verify(taskRepository).flush();
    }

    @Test
    void updateTask_lockedByAnotherUser_throwsBadRequest() {
        // given
        Task existingTask = new Task();
        existingTask.setId(1L);
        existingTask.setName("Old Task");
        existingTask.setcreatorId(42L);
        existingTask.setLockedByUser(99L); // locked by someone else

        Task updateDTO = new Task();
        updateDTO.setName("Updated Task");

        Long userId = 42L;

        // then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> taskService.updateTask(existingTask, updateDTO, userId));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertTrue(exception.getReason().contains("Task is locked by another user"));
    }

    @Test
    void updateTask_pausedTask_throwsForbidden() {
        // given
        Task existingTask = new Task();
        existingTask.setId(1L);
        existingTask.setName("Old Task");
        existingTask.setcreatorId(42L);
        existingTask.setPaused(true);

        Task updateDTO = new Task();
        updateDTO.setName("Updated Task");

        Long userId = 42L;

        // then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> taskService.updateTask(existingTask, updateDTO, userId));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("Task is paused", exception.getReason());
    }

    @Test
    void updateTask_invalidFields_throwsException() {
        // given
        Task existingTask = new Task();
        existingTask.setId(1L);
        existingTask.setName("Old Task");
        existingTask.setcreatorId(42L);

        Task updateDTO = new Task();
        updateDTO.setName(""); // invalid name

        Long userId = 42L;

        // then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> taskService.updateTask(existingTask, updateDTO, userId));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("Task name cannot be empty", exception.getReason());
    }

}