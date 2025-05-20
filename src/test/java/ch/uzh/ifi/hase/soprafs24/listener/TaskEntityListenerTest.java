package ch.uzh.ifi.hase.soprafs24.listener;

import ch.uzh.ifi.hase.soprafs24.entity.Task;
import ch.uzh.ifi.hase.soprafs24.repository.TaskRepository;
import ch.uzh.ifi.hase.soprafs24.rest.dto.task.TaskGetDTO;
import ch.uzh.ifi.hase.soprafs24.rest.mapper.DTOMapper;
import ch.uzh.ifi.hase.soprafs24.service.WebSocketNotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class TaskEntityListenerTest {

    private static final Logger log = LoggerFactory.getLogger(TaskEntityListenerTest.class);
    private static final String ENTITY_TYPE = "TASKS";

    @Mock
    private WebSocketNotificationService mockNotificationService;

    @Mock
    private TaskRepository mockTaskRepository;

    @Spy
    private DTOMapper dtoMapper = DTOMapper.INSTANCE;

    @InjectMocks
    private TaskEntityListener taskEntityListener;

    private Task testTask;
    private TaskGetDTO testTaskGetDTO;

    @BeforeEach
    void setUp() {
        testTask = new Task();
        testTask.setId(1L);
        testTask.setTeamId(1L);
        testTask.setName("Test Task");

        testTaskGetDTO = new TaskGetDTO();
        testTaskGetDTO.setId(testTask.getId());
        testTaskGetDTO.setName(testTask.getName());

        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }


    @Test
    void afterTaskPersist_noActiveTransaction_sendsNotificationDirectly() {
        List<Task> tasksInRepo = new ArrayList<>(Collections.singletonList(testTask));
        when(mockTaskRepository.findAll()).thenReturn(tasksInRepo);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }

        taskEntityListener.afterTaskPersist(testTask);

        assertFalse(TransactionSynchronizationManager.isSynchronizationActive(),
                "Transaction synchronization should not be active when no transaction.");

        ArgumentCaptor<List<TaskGetDTO>> payloadCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(mockNotificationService, times(1)).notifyTeamMembers(
                eq(testTask.getTeamId()),
                eq(ENTITY_TYPE),
                payloadCaptor.capture());
        assertFalse(payloadCaptor.getValue().isEmpty());
        assertEquals(testTask.getId(), payloadCaptor.getValue().get(0).getId());
        verify(mockTaskRepository, times(1)).findAll();
    }

    @Test
    void afterTaskUpdate_noActiveTransaction_sendsNotificationDirectly() {
        List<Task> tasksInRepo = new ArrayList<>(Collections.singletonList(testTask));
        when(mockTaskRepository.findAll()).thenReturn(tasksInRepo);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }

        taskEntityListener.afterTaskUpdate(testTask);

        assertFalse(TransactionSynchronizationManager.isSynchronizationActive(),
                "Transaction synchronization should not be active when no transaction.");
        ArgumentCaptor<List> payloadCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockNotificationService, times(1)).notifyTeamMembers(
                eq(testTask.getTeamId()),
                eq(ENTITY_TYPE),
                payloadCaptor.capture());
        assertFalse(payloadCaptor.getValue().isEmpty());
        verify(mockTaskRepository, times(1)).findAll();
    }

    @Test
    void afterTaskRemove_noActiveTransaction_sendsNotificationDirectly() {
        List<Task> tasksInRepo = new ArrayList<>();

        when(mockTaskRepository.findAll()).thenReturn(tasksInRepo);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }

        taskEntityListener.afterTaskRemove(testTask);

        assertFalse(TransactionSynchronizationManager.isSynchronizationActive(),
                "Transaction synchronization should not be active when no transaction.");
        ArgumentCaptor<List> payloadCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockNotificationService, times(1)).notifyTeamMembers(
                eq(testTask.getTeamId()),
                eq(ENTITY_TYPE),
                payloadCaptor.capture());
        assertTrue(payloadCaptor.getValue().isEmpty(),
                "Payload should be empty if task is removed and repo is empty/filtered");
        verify(mockTaskRepository, times(1)).findAll();
    }

    @Test
    void getCurrentTasksForTeamDTO_nullTeamId_returnsEmptyList() {
        List<TaskGetDTO> result = taskEntityListener.getCurrentTasksForTeamDTO(null);

        assertTrue(result.isEmpty());
        verify(mockTaskRepository, never()).findAll();
    }

    @Test
    void getCurrentTasksForTeamDTO_validTeamId_filtersAndMapsTasks() {
        Long targetTeamId = 1L;
        Task task1Team1 = new Task();
        task1Team1.setId(1L);
        task1Team1.setTeamId(targetTeamId);
        task1Team1.setName("T1");
        Task task2Team2 = new Task();
        task2Team2.setId(2L);
        task2Team2.setTeamId(2L);
        task2Team2.setName("T2");
        Task task3Team1 = new Task();
        task3Team1.setId(3L);
        task3Team1.setTeamId(targetTeamId);
        task3Team1.setName("T3");

        List<Task> allTasks = new ArrayList<>();
        allTasks.add(task1Team1);
        allTasks.add(task2Team2);
        allTasks.add(task3Team1);

        when(mockTaskRepository.findAll()).thenReturn(allTasks);

        List<TaskGetDTO> result = taskEntityListener.getCurrentTasksForTeamDTO(targetTeamId);

        assertEquals(2, result.size());

        assertTrue(result.stream().anyMatch(dto -> dto.getId().equals(task1Team1.getId())));
        assertTrue(result.stream().anyMatch(dto -> dto.getId().equals(task3Team1.getId())));
        verify(mockTaskRepository, times(1)).findAll();
    }
}