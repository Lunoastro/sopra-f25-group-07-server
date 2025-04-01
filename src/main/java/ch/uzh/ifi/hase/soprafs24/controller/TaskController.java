package ch.uzh.ifi.hase.soprafs24.controller;

import ch.uzh.ifi.hase.soprafs24.entity.Task;
import ch.uzh.ifi.hase.soprafs24.rest.dto.Task.TaskGetDTO;
import ch.uzh.ifi.hase.soprafs24.rest.dto.Task.TaskPostDTO;
import ch.uzh.ifi.hase.soprafs24.rest.dto.Task.TaskPutDTO;
import ch.uzh.ifi.hase.soprafs24.rest.mapper.DTOMapper;
import ch.uzh.ifi.hase.soprafs24.service.TaskService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;



import java.util.ArrayList;
import java.util.List;
@RestController
public class TaskController {

  private final TaskService taskService;

  

  TaskController(TaskService taskService) {
    this.taskService = taskService;
  }
    @GetMapping("/tasks")
    @ResponseStatus(HttpStatus.OK)
    public List<TaskGetDTO> getAllTasks(@RequestHeader("Authorization") String userToken) {
        taskService.validate_userToken(userToken);
        // Retrieve all tasks using the service
        List<Task> tasks = taskService.getAllTasks();
        // Convert the list of entities to a list of DTOs for the response
        List<TaskGetDTO> taskGetDTOs = new ArrayList<>();
        for (Task task : tasks) {
            taskGetDTOs.add(DTOMapper.INSTANCE.convertEntityToTaskGetDTO(task));
        }
        return taskGetDTOs;
    }

    @PostMapping("/tasks/create")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskGetDTO createTask(@RequestBody TaskPostDTO taskPostDTO, @RequestHeader("Authorization") String userToken) {
        // Extract the token from the Bearer header
        taskService.validate_userToken(userToken);
        // Convert the incoming DTO to an entity
        Task task = DTOMapper.INSTANCE.convertTaskPostDTOtoEntity(taskPostDTO);
        // send the task to the service for creation
        Task createdTask = taskService.createTask(task, userToken);
        // Convert the created entity back to a DTO for the response
        return DTOMapper.INSTANCE.convertEntityToTaskGetDTO(createdTask);
    }

    @GetMapping("/tasks/{taskId}")
    @ResponseStatus(HttpStatus.OK)
    public TaskGetDTO getTask(@PathVariable Long taskId, @RequestHeader("Authorization") String userToken) {
        // Validate the user token
        taskService.validate_userToken(userToken);
        // Retrieve the task using the service
        Task task = taskService.getTaskById(taskId);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        // Convert the entity to a DTO for the response
        return DTOMapper.INSTANCE.convertEntityToTaskGetDTO(task);
    }


    @PutMapping("/tasks/{taskId}/edit")
    @ResponseStatus(HttpStatus.OK)
    public TaskGetDTO updateTask(@PathVariable Long taskId, @RequestBody TaskPutDTO taskPutDTO, @RequestHeader("Authorization") String userToken) {
        // Validate the user token
        taskService.validate_userToken(userToken);
        // Retrieve the existing task
        Task existingTask = taskService.getTaskById(taskId);
        
        // convert putDTO to entity
        Task task = DTOMapper.INSTANCE.convertTaskPutDTOtoEntity(taskPutDTO);
        Task updatedTask = taskService.updateTask(existingTask, task);
        
        // Convert the updated entity back to a DTO for the response
        return DTOMapper.INSTANCE.convertEntityToTaskGetDTO(updatedTask);
    }
    
    @DeleteMapping("/tasks/{taskId}/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTask(@PathVariable Long taskId, @RequestHeader("Authorization") String userToken) {
        // Validate the user token
        taskService.validate_userToken(userToken);
        // Check if the task exists
        Task existingTask = taskService.getTaskById(taskId);
        if (existingTask == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        // Delete the task using the service
        taskService.deleteTask(taskId);
    }
}