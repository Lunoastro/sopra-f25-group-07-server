package ch.uzh.ifi.hase.soprafs24.controller;

import ch.uzh.ifi.hase.soprafs24.entity.User;
import ch.uzh.ifi.hase.soprafs24.entity.Task;
import ch.uzh.ifi.hase.soprafs24.repository.TaskRepository;
import ch.uzh.ifi.hase.soprafs24.rest.dto.TaskGetDTO; 
import ch.uzh.ifi.hase.soprafs24.rest.dto.TaskPostDTO;
import ch.uzh.ifi.hase.soprafs24.rest.dto.TaskDeleteDTO;
import ch.uzh.ifi.hase.soprafs24.rest.dto.TaskPutDTO;
import ch.uzh.ifi.hase.soprafs24.rest.mapper.DTOMapper;
import ch.uzh.ifi.hase.soprafs24.service.TaskService;
import ch.uzh.ifi.hase.soprafs24.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;



import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;


/**
 * User Controller
 * This class is responsible for handling all REST request that are related to
 * the user.
 * The controller will receive the request and delegate the execution to the
 * UserService and finally return the result.
 */
@RestController
public class TaskController {

  private final TaskService taskService;
  private final TaskRepository taskRepository;

  

  TaskController(TaskService taskService, TaskRepository taskRepository) {
    this.taskService = taskService;
    this.taskRepository = taskRepository;
  }
  /*
   * 
   * TODO:
   * - Define POST /tasks/create to allow task creation.
   * - Ensure each task has a title, deadline, and optional description.
   * - Ensure the creator's userId is stored with the task.
   * 
   */
    @GetMapping("/tasks")
    @ResponseStatus(HttpStatus.OK)
    public List<TaskGetDTO> getAllTasks() {
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
    public TaskGetDTO createTask(@RequestBody TaskPostDTO taskPostDTO) {
        // Convert the incoming DTO to an entity
        Task task = DTOMapper.INSTANCE.convertTaskPostDTOtoEntity(taskPostDTO);
        // validate the task
        taskService.validateTask(task);
        // Create the task using the service
        Task createdTask = taskService.createTask(task);
        // Convert the created entity back to a DTO for the response
        return DTOMapper.INSTANCE.convertEntityToTaskGetDTO(createdTask);
    }

    @GetMapping("/tasks/{taskId}")
    @ResponseStatus(HttpStatus.OK)
    public TaskGetDTO getTask(@PathVariable Long taskId) {
        // Retrieve the task using the service
        Task task = taskService.getTaskById(taskId);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        // Convert the entity to a DTO for the response
        return DTOMapper.INSTANCE.convertEntityToTaskGetDTO(task);
    }
    @PatchMapping("/tasks/{taskId}/edit")
    @ResponseStatus(HttpStatus.OK)
    public TaskGetDTO updateTask(@PathVariable Long taskId, @RequestBody TaskPutDTO taskPutDTO) {
        // Retrieve the existing task
        Task existingTask = taskService.getTaskById(taskId);
        if (existingTask == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        // Update only the fields that are provided in the DTO
        if (taskPutDTO.getTaskName() != null) {
            existingTask.setTaskName(taskPutDTO.getTaskName());
        }
        if (taskPutDTO.getTaskDescription() != null) {
            existingTask.setTaskDescription(taskPutDTO.getTaskDescription());
        }
        if (taskPutDTO.getDeadline() != null) {
            existingTask.setDeadline(taskPutDTO.getDeadline());
        }
        // Save the updated task using the service
        Task updatedTask = taskService.updateTask(existingTask);
        // Convert the updated entity back to a DTO for the response
        return DTOMapper.INSTANCE.convertEntityToTaskGetDTO(updatedTask);
    }
    
    @DeleteMapping("/tasks/{taskId}/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTask(@PathVariable Long taskId) {
        // Check if the task exists
        Task existingTask = taskService.getTaskById(taskId);
        if (existingTask == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        // Delete the task using the service
        taskService.deleteTask(taskId);
    }
}