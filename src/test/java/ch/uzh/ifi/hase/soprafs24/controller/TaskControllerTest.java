package ch.uzh.ifi.hase.soprafs24.controller;

import ch.uzh.ifi.hase.soprafs24.constant.ColorID;
import ch.uzh.ifi.hase.soprafs24.entity.Task;
import ch.uzh.ifi.hase.soprafs24.entity.User;
import ch.uzh.ifi.hase.soprafs24.service.TaskService;
import ch.uzh.ifi.hase.soprafs24.rest.dto.task.TaskGetDTO;
import ch.uzh.ifi.hase.soprafs24.rest.dto.task.TaskPostDTO;
import ch.uzh.ifi.hase.soprafs24.rest.dto.task.TaskPutDTO;
import ch.uzh.ifi.hase.soprafs24.repository.TaskRepository;
import ch.uzh.ifi.hase.soprafs24.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs24.service.UserService;

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.Collections;
import java.util.List;
import java.util.Arrays;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TaskController.class)
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaskService taskService;

    @MockBean
    private UserService userService;

    @MockBean
    private TaskRepository taskRepository; 

    @MockBean
    private UserRepository userRepository;

    @Test
    void POST_createTask_validInput_taskCreated() throws Exception {
        Date now = new Date();
        Task task = new Task();
        task.setId(1L);
        task.setName("Clean Room");
        task.setDescription("Tidy everything up");
        task.setIsAssignedTo(2L);
        task.setDeadline(now);
        task.setCreationDate(now);
        task.setPaused(false);
        task.setPausedDate(null);
        task.setUnpausedDate(null);
        task.setValue(100); 
        task.setColor(ColorID.C1);
        task.setActiveStatus(true);
        task.setFrequency(7);
    
        TaskPostDTO taskPostDTO = new TaskPostDTO();
        taskPostDTO.setName("Clean Room");
        taskPostDTO.setDescription("Tidy everything up");
        taskPostDTO.setDeadline(now);
    
        String token = "token123";
        doNothing().when(taskService).validateUserToken(token);
        given(taskService.createTask(Mockito.any(), Mockito.eq(token))).willReturn(task);
    
        MockHttpServletRequestBuilder postRequest = post("/tasks")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + token)
            .content(asJsonString(taskPostDTO));
    
        mockMvc.perform(postRequest)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id", is(task.getId().intValue())))
            .andExpect(jsonPath("$.name", is(task.getName())))
            .andExpect(jsonPath("$.description", is(task.getDescription())))
            .andExpect(jsonPath("$.color", is(task.getColor().toString())))
            .andExpect(jsonPath("$.activeStatus", is(task.getActiveStatus())))
            .andExpect(jsonPath("$.isAssignedTo", is(task.getIsAssignedTo().intValue())));
           
    }

    @Test
    void POST_failedCreateTask_invalidInput_taskNotCreated() throws Exception {
        TaskPostDTO taskPostDTO = new TaskPostDTO();
        taskPostDTO.setName(null);  // Invalid input (null name)
    
        String token = "token123";
    
        doNothing().when(taskService).validateUserToken("Bearer " + token);
    
        // Simulate validation failure
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task name cannot be null"))
            .when(taskService).validatePostDto(any(TaskPostDTO.class));
    
        // Perform the POST request
        MockHttpServletRequestBuilder postRequest = post("/tasks")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + token)
            .content(asJsonString(taskPostDTO));
    
        mockMvc.perform(postRequest)
            .andExpect(status().isBadRequest()); // Expecting 400 status
    }

    
       
    @Test
    void GET_getTaskById_validTaskId_taskReturned() throws Exception {
        Date now = new Date();
        Task task = new Task();
        task.setId(1L);
        task.setName("Clean Kitchen");
        task.setDescription("Wipe down counters");
        task.setIsAssignedTo(5L);
        task.setDeadline(now);
        task.setCreationDate(now);
        task.setPaused(true);
        task.setPausedDate(now);
        task.setUnpausedDate(null);
        task.setValue(50);
        task.setColor(ColorID.C5);
        task.setActiveStatus(false);
        task.setFrequency(3);

        // Mock service behavior
        doNothing().when(taskService).validateUserToken(anyString());
        given(taskService.getTaskById(1L)).willReturn(task);

        MockHttpServletRequestBuilder getRequest = get("/tasks/{taskId}", 1L)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer dummy-token");

        mockMvc.perform(getRequest)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(task.getId().intValue())))
            .andExpect(jsonPath("$.name", is(task.getName())))
            .andExpect(jsonPath("$.description", is(task.getDescription())))
            .andExpect(jsonPath("$.color", is(task.getColor().toString())))
            .andExpect(jsonPath("$.activeStatus", is(task.getActiveStatus())))
            .andExpect(jsonPath("$.isAssignedTo", is(task.getIsAssignedTo().intValue())))
            .andExpect(jsonPath("$.deadline").exists()); //should we check for the exact date?
    
    }


    @Test
    void GET_getTasks_noParameters_tasksReturned() throws Exception {
        // Setup test data
        Date now = new Date();
        Task task1 = new Task();
        task1.setId(1L);
        task1.setName("Task 1");
        task1.setDescription("Description 1");
        task1.setIsAssignedTo(5L);
        task1.setTeamId(10L);
        task1.setDeadline(now);
        task1.setCreationDate(now);
        task1.setActiveStatus(true);

        Task task2 = new Task();
        task2.setId(2L);
        task2.setName("Task 2");
        task2.setDescription("Description 2");
        task2.setIsAssignedTo(6L);
        task2.setTeamId(10L);
        task2.setDeadline(now);
        task2.setCreationDate(now);
        task2.setActiveStatus(false);

        // Create a list of tasks for the mock response
        List<Task> tasks = Arrays.asList(task1, task2);

        // Mock user for token validation
        User mockUser = new User();
        mockUser.setId(5L);
        mockUser.setTeamId(10L);

        String token = "token123";

        // Mock service behavior
        doNothing().when(taskService).validateUserToken(token);
        when(taskService.getFilteredTasks(null, null)).thenReturn(tasks);
        when(userRepository.findByToken(token)).thenReturn(mockUser);

        // Perform the GET request
        MockHttpServletRequestBuilder getRequest = get("/tasks")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + token);

        mockMvc.perform(getRequest)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].id", is(task1.getId().intValue())))
            .andExpect(jsonPath("$[0].name", is(task1.getName())))
            .andExpect(jsonPath("$[1].id", is(task2.getId().intValue())))
            .andExpect(jsonPath("$[1].name", is(task2.getName())));

        // Verify that the service methods were called
        verify(taskService, times(1)).validateUserToken(token);
        verify(taskService, times(1)).getFilteredTasks(null, null);
        verify(userRepository, times(1)).findByToken(token);
    }

    @Test
    void GET_getTasks_withParameters_filteredTasksReturned() throws Exception {
        // Setup test data
        Date now = new Date();
        Task task1 = new Task();
        task1.setId(1L);
        task1.setName("Task 1");
        task1.setDescription("Description 1");
        task1.setIsAssignedTo(5L);
        task1.setTeamId(10L);
        task1.setDeadline(now);
        task1.setCreationDate(now);
        task1.setActiveStatus(true);

        // Create a list of tasks for the mock response - only active tasks
        List<Task> activeTasks = Collections.singletonList(task1);

        // Mock user for token validation
        User mockUser = new User();
        mockUser.setId(5L);
        mockUser.setTeamId(10L);

        String token = "token123";
        Boolean isActive = true;
        String type = "personal";

        // Mock service behavior
        doNothing().when(taskService).validateUserToken(token);
        when(taskService.getFilteredTasks(isActive, type)).thenReturn(activeTasks);
        when(userRepository.findByToken(token)).thenReturn(mockUser);

        // Perform the GET request with parameters
        MockHttpServletRequestBuilder getRequest = get("/tasks")
            .param("isActive", isActive.toString())
            .param("type", type)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + token);

        mockMvc.perform(getRequest)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id", is(task1.getId().intValue())))
            .andExpect(jsonPath("$[0].name", is(task1.getName())))
            .andExpect(jsonPath("$[0].activeStatus", is(true)));

        // Verify that the service methods were called with correct parameters
        verify(taskService, times(1)).validateUserToken(token);
        verify(taskService, times(1)).getFilteredTasks(isActive, type);
        verify(userRepository, times(1)).findByToken(token);
    }

    @Test
    void GET_getTasks_unauthorized_returnsUnauthorized() throws Exception {
        String token = "invalid-token";
        
        // Mock service to throw exception for invalid token
        doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token"))
            .when(taskService).validateUserToken(token);

        // Perform the GET request with invalid token
        MockHttpServletRequestBuilder getRequest = get("/tasks")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + token);

        mockMvc.perform(getRequest)
            .andExpect(status().isUnauthorized());

        verify(taskService, times(1)).validateUserToken(token);
        verify(taskService, times(0)).getFilteredTasks(any(), any());
    }

    @Test
    void GET_isEditable_userIsCreator_returnsTrue() throws Exception {
        Long taskId = 1L;
        String token = "token123";

        Task mockTask = new Task();
        mockTask.setId(taskId);

        // Mock service behavior for a valid creator
        doNothing().when(taskService).validateUserToken(token);
        doNothing().when(taskService).validateCreator(token, taskId);
        when(taskService.getTaskById(taskId)).thenReturn(mockTask);
        when(taskService.checkTaskType(mockTask)).thenReturn("additional");

        // Perform the GET request
        MockHttpServletRequestBuilder getRequest = get("/tasks/{taskId}/isEditable", taskId)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + token);

        mockMvc.perform(getRequest)
            .andExpect(status().isOk())
            .andExpect(content().string("true"));

        verify(taskService).validateUserToken(token);
        verify(taskService).validateCreator(token, taskId);
        verify(taskService).getTaskById(taskId);
        verify(taskService).checkTaskType(mockTask);
    }

    @Test
    void GET_isEditable_userNotCreator_returnsFalse() throws Exception {
        Long taskId = 1L;
        String token = "token123";

        // Mock service behavior for an invalid creator
        doNothing().when(taskService).validateUserToken(token);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not the creator"))
            .when(taskService).validateCreator(token, taskId);

        // Perform the GET request
        MockHttpServletRequestBuilder getRequest = get("/tasks/{taskId}/isEditable", taskId)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + token);

        mockMvc.perform(getRequest)
            .andExpect(status().isOk())
            .andExpect(content().string("false"));

        verify(taskService, times(1)).validateUserToken(token);
        verify(taskService, times(1)).validateCreator(token, taskId);
    }

    @Test
    void GET_isEditable_invalidToken_returnsFalse() throws Exception {
        Long taskId = 1L;
        String token = "invalid-token";

        // Mock service behavior for an invalid token
        doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token"))
            .when(taskService).validateUserToken(token);

        // Perform the GET request
        MockHttpServletRequestBuilder getRequest = get("/tasks/{taskId}/isEditable", taskId)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + token);

        mockMvc.perform(getRequest)
            .andExpect(status().isOk())
            .andExpect(content().string("false"));

        verify(taskService, times(1)).validateUserToken(token);
        verify(taskService, times(0)).validateCreator(anyString(), anyLong());
    }

    @Test
    void GET_isEditable_taskNotFound_returnsFalse() throws Exception {
        Long nonExistentTaskId = 999L;
        String token = "token123";

        // Mock service behavior for a non-existent task
        doNothing().when(taskService).validateUserToken(token);
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"))
            .when(taskService).validateCreator(token, nonExistentTaskId);

        // Perform the GET request
        MockHttpServletRequestBuilder getRequest = get("/tasks/{taskId}/isEditable", nonExistentTaskId)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + token);

        mockMvc.perform(getRequest)
            .andExpect(status().isOk())
            .andExpect(content().string("false"));

        verify(taskService, times(1)).validateUserToken(token);
        verify(taskService, times(1)).validateCreator(token, nonExistentTaskId);
    }

    @Test
    void GET_getTaskById_invalidTaskId_taskNotFound() throws Exception {
        // Mock service behavior for invalid task ID (task not found)
        doNothing().when(taskService).validateUserToken(anyString());
        given(taskService.getTaskById(999L)).willReturn(null); // or throw an exception, depending on how your service handles this

        MockHttpServletRequestBuilder getRequest = get("/tasks/{taskId}", 999L)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer dummy-token");

        mockMvc.perform(getRequest)
            .andExpect(status().isNotFound()); // Expecting 404 Not Found

}

        
    @Test
    void PUT_updateTask_validInput_taskUpdated() throws Exception {
        Date now = new Date();
        Task existingTask = new Task();
        existingTask.setId(1L);
        existingTask.setName("Clean Kitchen");
        existingTask.setDescription("Wipe down counters");
        existingTask.setIsAssignedTo(5L);
        existingTask.setDeadline(now);
        existingTask.setCreationDate(now);
        existingTask.setPaused(true);
        existingTask.setPausedDate(now);
        existingTask.setUnpausedDate(null);
        existingTask.setValue(50);
        existingTask.setColor(ColorID.C5);
        existingTask.setActiveStatus(false);
        existingTask.setFrequency(3);

        TaskPutDTO taskPutDTO = new TaskPutDTO();
        taskPutDTO.setName("Clean Bathroom");
        taskPutDTO.setDescription("Wipe the mirrors and sink");
        taskPutDTO.setDeadline(now);
        taskPutDTO.setColor(ColorID.C2);
        taskPutDTO.setActiveStatus(true);

        String token = "token123";

        // Mock the service layer to simulate the task update behavior
        given(taskService.getTaskById(1L)).willReturn(existingTask);
        given(taskService.updateTask(Mockito.any(), Mockito.any())).willReturn(existingTask);

        MockHttpServletRequestBuilder putRequest = put("/tasks/{taskId}", 1L)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + token)
            .content(asJsonString(taskPutDTO));

        // When: Perform the PUT request and then validate the response
        mockMvc.perform(putRequest)
        .andExpect(status().isNoContent());
}


    @Test
    void PUT_failedUpdateTask_invalidTaskId_taskNotFound() throws Exception {

        String authorizationHeader = "Bearer token123";
        TaskPutDTO taskPutDTO = new TaskPutDTO();
        taskPutDTO.setName("Clean Bathroom");
        taskPutDTO.setDescription("Wipe the mirrors and sink");
        taskPutDTO.setDeadline(new Date());
        taskPutDTO.setColor(ColorID.C2);
        taskPutDTO.setActiveStatus(true);

        when(taskService.getTaskById(1L)).thenReturn(null);
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"))
            .when(taskService).updateTask(any(), any());

        mockMvc.perform(MockMvcRequestBuilders.put("/tasks/1")
                .header("Authorization", authorizationHeader)
                .contentType("application/json")
                .content(asJsonString(taskPutDTO)))
                .andExpect(status().isNotFound());


        verify(taskService, times(1)).updateTask(any(), any());
    }



    @Test
    void DELETE_deleteTask_validInput_taskDeleted() throws Exception {
        doNothing().when(taskService).deleteTask(1L); 
        doNothing().when(taskService).validateUserToken(anyString());
        doNothing().when(taskService).validateCreator(anyString(), eq(1L));

        MockHttpServletRequestBuilder deleteRequest = delete("/tasks/{taskId}", 1L)
            .header("Authorization", "Bearer dummy-token");

        mockMvc.perform(deleteRequest)
            .andExpect(status().isNoContent());

        verify(taskService, times(1)).deleteTask(1L);
    }

    @Test
    void DELETE_failedDeleteTask_invalidTaskId_taskNotFound() throws Exception {
        doNothing().when(taskService).validateUserToken(anyString());
        doNothing().when(taskService).validateCreator(anyString(), eq(1L));
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Task with ID 1 does not exist")).when(taskService).deleteTask(1L);

        MockHttpServletRequestBuilder deleteRequest = delete("/tasks/{taskId}", 1L)
            .header("Authorization", "Bearer dummy-token");

        mockMvc.perform(deleteRequest)
            .andExpect(status().isNotFound());

        verify(taskService, times(1)).deleteTask(1L);
    }   
    
    @Test
    void PATCH_successfulClaimTask_taskClaimed() throws Exception {
        // Setup: Create a valid task and a token
        Long taskId = 1L;
        String token = "valid_token";
        
        Task existingTask = new Task();
        existingTask.setId(taskId);
        existingTask.setName("Test Task");
        existingTask.setActiveStatus(true);
        existingTask.setIsAssignedTo(null); // Not assigned yet
        
        Task claimedTask = new Task();
        claimedTask.setId(taskId);
        claimedTask.setName("Test Task");
        claimedTask.setActiveStatus(true);
        claimedTask.setIsAssignedTo(123L); // Now assigned to user with ID 123
        
        TaskGetDTO claimedTaskDTO = new TaskGetDTO();
        claimedTaskDTO.setId(taskId);
        claimedTaskDTO.setName("Test Task");
        claimedTaskDTO.setIsAssignedTo(123L);
        
        doNothing().when(taskService).validateUserToken(token);
        when(taskService.getTaskById(taskId)).thenReturn(existingTask);
        when(taskService.claimTask(existingTask, token)).thenReturn(claimedTask);
        
        // Perform the PATCH request
        MockHttpServletRequestBuilder patchRequest = patch("/tasks/{taskId}/claim", taskId)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + token);
        
        // For HTTP 204 No Content, we should check status code only
        mockMvc.perform(patchRequest)
            .andExpect(status().isNoContent());
            
        // Verify that the service methods were called correctly
        verify(taskService).validateUserToken(token);
        verify(taskService).getTaskById(taskId);
        verify(taskService).claimTask(existingTask, token);
    }

    @Test
    void PATCH_failedClaimTask_invalidToken_unauthorized() throws Exception {
        // Setup
        Long taskId = 1L;
        String invalidToken = "invalid_token";
        
        // Mock service behavior - simulate invalid token with token without prefix
        doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token"))
            .when(taskService).validateUserToken(invalidToken);
        
        // Perform the PATCH request
        MockHttpServletRequestBuilder patchRequest = patch("/tasks/{taskId}/claim", taskId)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + invalidToken);
        
        // Verify response
        mockMvc.perform(patchRequest)
            .andExpect(status().isUnauthorized());
    }

    @Test
    void PATCH_failedClaimTask_taskNotFound() throws Exception {
        // Setup
        Long nonExistentTaskId = 999L;
        String token = "valid_token";
        
        // Mock service behavior - token without prefix
        doNothing().when(taskService).validateUserToken(token);
        when(taskService.getTaskById(nonExistentTaskId))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        
        // Perform the PATCH request
        MockHttpServletRequestBuilder patchRequest = patch("/tasks/{taskId}/claim", nonExistentTaskId)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + token);
        
        // Verify response
        mockMvc.perform(patchRequest)
            .andExpect(status().isNotFound());
    }

    @Test
    void PATCH_failedClaimTask_alreadyClaimed() throws Exception {
        // Setup
        Long taskId = 1L;
        String token = "valid_token";
        
        Task existingTask = new Task();
        existingTask.setId(taskId);
        existingTask.setName("Already Claimed Task");
        existingTask.setActiveStatus(true);
        existingTask.setIsAssignedTo(456L); // Already assigned to another user
        
        // Mock service behavior - token without prefix
        doNothing().when(taskService).validateUserToken(token);
        when(taskService.getTaskById(taskId)).thenReturn(existingTask);
        when(taskService.claimTask(existingTask, token))
            .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Task already claimed by another user"));
        
        // Perform the PATCH request
        MockHttpServletRequestBuilder patchRequest = patch("/tasks/{taskId}/claim", taskId)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + token);
        
        // Verify response
        mockMvc.perform(patchRequest)
            .andExpect(status().isConflict());
    }

    @Test
    void PATCH_failedClaimTask_missingAuthHeader() throws Exception {
        // Setup
        Long taskId = 1L;
        
        // Perform the PATCH request without Authorization header
        MockHttpServletRequestBuilder patchRequest = patch("/tasks/{taskId}/claim", taskId)
            .contentType(MediaType.APPLICATION_JSON);
        
        mockMvc.perform(patchRequest)
            .andExpect(status().isBadRequest());
    }

    @Test
    void PATCH_failedClaimTask_invalidAuthHeaderFormat() throws Exception {
        // Setup
        Long taskId = 1L;
        String malformedToken = "InvalidFormat"; // Not a Bearer token
        
        // Perform the PATCH request
        MockHttpServletRequestBuilder patchRequest = patch("/tasks/{taskId}/claim", taskId)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", malformedToken);
        
        mockMvc.perform(patchRequest)
            .andExpect(status().isUnauthorized());
    } 
        

    private String asJsonString(final Object object) {
        try {
            return new ObjectMapper().writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert to JSON", e);
        }
    }
}
