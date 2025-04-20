package ch.uzh.ifi.hase.soprafs24.controller;

import ch.uzh.ifi.hase.soprafs24.entity.Task;
import ch.uzh.ifi.hase.soprafs24.rest.dto.task.TaskGetDTO;
import ch.uzh.ifi.hase.soprafs24.rest.dto.task.TaskPostDTO;
import ch.uzh.ifi.hase.soprafs24.rest.dto.task.TaskPutDTO;
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
    @PostMapping("/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskGetDTO createTask(@RequestBody TaskPostDTO taskPostDTO, @RequestHeader("Authorization") String userToken) {
        // Extract the token from the Bearer header
        taskService.validateUserToken(userToken);
        // validate the DTO before converting to catch errors
        taskService.validatePostDto(taskPostDTO);
        // Convert the incoming DTO to an entity
        Task task = DTOMapper.INSTANCE.convertTaskPostDTOtoEntity(taskPostDTO);
        // send the task to the service for creation
        Task createdTask = taskService.createTask(task, userToken);
        // Convert the created entity back to a DTO for the response
        return DTOMapper.INSTANCE.convertEntityToTaskGetDTO(createdTask);
    }

    @GetMapping("/tasks")
    @ResponseStatus(HttpStatus.OK)
    public List<TaskGetDTO> getTasks(@RequestParam(required = false) Boolean isActive,
                                        @RequestParam(required = false) String type,
                                        @RequestHeader("Authorization") String userToken) {
        taskService.validateUserToken(userToken);
        // Retrieve all tasks using the service
        List<Task> tasks = taskService.getFilteredTasks(isActive, type);
        // Convert the list of entities to a list of DTOs for the response
        List<TaskGetDTO> taskGetDTOs = new ArrayList<>();
        for (Task task : tasks) {
            taskGetDTOs.add(DTOMapper.INSTANCE.convertEntityToTaskGetDTO(task));
        }
        return taskGetDTOs;
    }

    @GetMapping("/tasks/{taskId}")
    @ResponseStatus(HttpStatus.OK)
    public TaskGetDTO getTask(@PathVariable Long taskId, @RequestHeader("Authorization") String userToken) {
        // Validate the user token
        taskService.validateUserToken(userToken);
        // Retrieve the task using the service
        Task task = taskService.getTaskById(taskId);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        // Convert the entity to a DTO for the response
        return DTOMapper.INSTANCE.convertEntityToTaskGetDTO(task);
    }

    @PutMapping("/tasks/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public TaskGetDTO updateTask(@PathVariable Long taskId, @RequestBody TaskPutDTO taskPutDTO, @RequestHeader("Authorization") String userToken) {
        // Validate the user token
        taskService.validateUserToken(userToken);
        // Checks if user is the creator of the task and if task exists
        taskService.validateCreator(userToken, taskId);
        // Retrieve the existing task
        Task existingTask = taskService.getTaskById(taskId);
        // convert putDTO to entity
        Task task = DTOMapper.INSTANCE.convertTaskPutDTOtoEntity(taskPutDTO);
        Task updatedTask = taskService.updateTask(existingTask, task);
        // Convert the updated entity back to a DTO for the response
        return DTOMapper.INSTANCE.convertEntityToTaskGetDTO(updatedTask);
    }
    
    //decided on Patch since it only requires the update of one singular field
    @PatchMapping("/tasks/{taskId}/claim")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public TaskGetDTO claimTasks(@PathVariable Long taskId, @RequestHeader("Authorization") String userToken) {
        // Validate the user token        
        taskService.validateUserToken(userToken);
        //Retrieves Task by Id or throws Http error if the task doesn't exist
        Task existingTask = taskService.getTaskById(taskId);
        //Claims the task for the user and assigns the user to the correct field 
        Task claimed = taskService.claimTask(existingTask,userToken);               
        // Convert the updated entity back to a DTO for the response
        return DTOMapper.INSTANCE.convertEntityToTaskGetDTO(claimed);
    }

    @PutMapping("/tasks/{taskId}/quit")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public List<TaskGetDTO> quitTasks(@PathVariable Long taskId, @RequestHeader("Authorization") String userToken) {
        
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet");

    }

    @PutMapping("/tasks/{taskId}/expire")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public List<TaskGetDTO> expireTasks(@PathVariable Long taskId, @RequestHeader("Authorization") String userToken) {
        
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet");

    }

    @DeleteMapping("/tasks/{taskId}/finish")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public List<TaskGetDTO> finishTasks(@PathVariable Long taskId, @RequestHeader("Authorization") String userToken) {
        
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet");

    }
    
    @DeleteMapping("/tasks/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTask(@PathVariable Long taskId, @RequestHeader("Authorization") String userToken) {
        // Validate the user token
        taskService.validateUserToken(userToken);
        // Checks if user is the creator of the task and if task exists
        taskService.validateCreator(userToken, taskId);
        // Delete the task using the service
        taskService.deleteTask(taskId);
    }
}