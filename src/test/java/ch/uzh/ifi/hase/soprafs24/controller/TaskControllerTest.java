package ch.uzh.ifi.hase.soprafs24.controller;

import ch.uzh.ifi.hase.soprafs24.constant.ColorID;
import ch.uzh.ifi.hase.soprafs24.entity.Task;
import ch.uzh.ifi.hase.soprafs24.service.TaskService;
import ch.uzh.ifi.hase.soprafs24.rest.dto.task.TaskPostDTO;
import ch.uzh.ifi.hase.soprafs24.rest.dto.task.TaskPutDTO;

import ch.uzh.ifi.hase.soprafs24.repository.TaskRepository;

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

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
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
    private TaskRepository taskRepository; 

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
        task.setValue(100);  // Ensure value is set
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



    private String asJsonString(final Object object) {
        try {
            return new ObjectMapper().writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert to JSON", e);
        }
    }
}
