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
import ch.uzh.ifi.hase.soprafs24.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs24.service.TeamService;
import ch.uzh.ifi.hase.soprafs24.service.UserService;
import java.util.Date;
import java.util.Calendar;

import java.util.ArrayList;
import java.util.List;


@RestController
public class TaskController {

  private final TaskService taskService;
  private final UserRepository userrepository;
  private final TeamService teamService;
  private final UserService userService;
  private String additionalTask = "additional"; 
  

  TaskController(TaskService taskService, UserRepository userrepository, TeamService teamService, UserService userService) {
    this.taskService = taskService;
    this.userrepository = userrepository;
    this.teamService = teamService;
    this.userService = userService;
  }
    @PostMapping("/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskGetDTO createTask(@RequestBody TaskPostDTO taskPostDTO, @RequestHeader("Authorization") String authorizationHeader) {
        // Extract the token from the Bearer header
        String userToken = validateAuthorizationHeader(authorizationHeader);
        teamService.validateTeamPaused(userToken);
        // validate the DTO before converting to catch errors
        taskService.validatePostDto(taskPostDTO);
        // Convert the incoming DTO to an entity
        Task task = DTOMapper.INSTANCE.convertTaskPostDTOtoEntity(taskPostDTO);
        // send the task to the service for creation
        Task createdTask = taskService.createTask(task, userToken);
        // Convert the created entity back to a DTO for the response
        return DTOMapper.INSTANCE.convertEntityToTaskGetDTO(createdTask);
    }

    @PostMapping("/tasks/luckyDraw")
    public List<TaskGetDTO> luckyDraw(@RequestHeader("Authorization") String authorizationHeader) {
        // Extract the token from the Bearer header
        String userToken = validateAuthorizationHeader(authorizationHeader);
        teamService.validateTeamPaused(userToken);
        Long userTeamId = userrepository.findByToken(userToken).getTeamId();
        // Retrieve all active tasks using the service
        List<Task> updatedTasks = taskService.luckyDrawTasks(userTeamId);

        List<TaskGetDTO> taskGetDTOs = new ArrayList<>();
        for (Task task : updatedTasks) {
            taskGetDTOs.add(DTOMapper.INSTANCE.convertEntityToTaskGetDTO(task));
        }
        return taskGetDTOs;
    }

    @PostMapping("/tasks/autodistribute")
    public List<TaskGetDTO> autodistribute(@RequestHeader("Authorization") String authorizationHeader) {
        // Extract the token from the Bearer header
        String userToken = validateAuthorizationHeader(authorizationHeader);
        teamService.validateTeamPaused(userToken);
        Long userTeamId = userrepository.findByToken(userToken).getTeamId();
        // Call service to do the actual task distribution and XP update
        List<Task> updatedTasks = taskService.autodistributeTasks(userTeamId);
   
        List<TaskGetDTO> taskGetDTOs = new ArrayList<>();
        for (Task task : updatedTasks) {
            taskGetDTOs.add(DTOMapper.INSTANCE.convertEntityToTaskGetDTO(task));
        }
        return taskGetDTOs;
    }

    @GetMapping("/tasks")
    @ResponseStatus(HttpStatus.OK)
    public List<TaskGetDTO> getTasks(@RequestParam(required = false) Boolean isActive,
                                        @RequestParam(required = false) String type,
                                        @RequestHeader("Authorization") String authorizationHeader) {
        // Validate the user token
        String userToken = validateAuthorizationHeader(authorizationHeader);
        teamService.validateTeamPaused(userToken);
        // Retrieve all tasks using the service
        List<Task> tasks = taskService.getFilteredTasks(isActive, type);
        // Convert the list of entities to a list of DTOs for the response
        List<TaskGetDTO> taskGetDTOs = new ArrayList<>();
        Long userTeamId = userrepository.findByToken(userToken).getTeamId();
        for (Task task : tasks) {
            if (task.getTeamId().equals(userTeamId)) {
            taskGetDTOs.add(DTOMapper.INSTANCE.convertEntityToTaskGetDTO(task));
            }
        }
        return taskGetDTOs;
    }

    @GetMapping("/tasks/{taskId}")
    @ResponseStatus(HttpStatus.OK)
    public TaskGetDTO getTask(@PathVariable Long taskId, @RequestHeader("Authorization") String authorizationHeader) {
        // Validate the user token
        String userToken = validateAuthorizationHeader(authorizationHeader);
        teamService.validateTeamPaused(userToken);
        // Retrieve the task using the service
        Task task = taskService.getTaskById(taskId);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        // Convert the entity to a DTO for the response
        return DTOMapper.INSTANCE.convertEntityToTaskGetDTO(task);
    }

    @GetMapping("/tasks/{taskId}/isEditable")
    @ResponseStatus(HttpStatus.OK)
    public Boolean getIsEditable(@PathVariable Long taskId, @RequestHeader("Authorization") String authorizationHeader) {
        try {
            // Validate the user token
            String userToken = validateAuthorizationHeader(authorizationHeader);
            teamService.validateTeamPaused(userToken);
            // Validate whether the user can edit the task
            taskService.validateCreator(userToken, taskId);
            Task task = taskService.getTaskById(taskId);
            if (!additionalTask.equals(taskService.checkTaskType(task))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Task is not editable");
            }
            return true; // all checks passed
        } catch (ResponseStatusException e) {
            return false;
        }
    }

    @PutMapping("/tasks/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public TaskGetDTO updateTask(@PathVariable Long taskId, @RequestBody TaskPutDTO taskPutDTO, @RequestHeader("Authorization") String authorizationHeader) {
        // Validate the user token
        String userToken = validateAuthorizationHeader(authorizationHeader);
        teamService.validateTeamPaused(userToken);
        // check if task is in the same team as the user
        taskService.validateTaskInTeam(userToken, taskId);
        // Checks if user is the creator of the task and if task exists
        taskService.validateRecurringEdit(userToken, taskId);
        // Retrieve the existing task
        Task existingTask = taskService.getTaskById(taskId);
        // convert putDTO to entity
        Task task = DTOMapper.INSTANCE.convertTaskPutDTOtoEntity(taskPutDTO);
        Long userId = userrepository.findByToken(userToken).getId();
        Task updatedTask = taskService.updateTask(existingTask, task,userId);
        // Convert the updated entity back to a DTO for the response
        return DTOMapper.INSTANCE.convertEntityToTaskGetDTO(updatedTask);
    }
    
    //decided on Patch since it only requires the update of one singular field
    @PatchMapping("/tasks/{taskId}/claim")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public TaskGetDTO claimTasks(@PathVariable Long taskId, @RequestHeader("Authorization") String authorizationHeader) {
        // Validate the user token        
        String userToken = validateAuthorizationHeader(authorizationHeader);
        teamService.validateTeamPaused(userToken);
        //Retrieves Task by Id or throws Http error if the task doesn't exist
        Task existingTask = taskService.getTaskById(taskId);
        //Claims the task for the user and assigns the user to the correct field 
        Task claimed = taskService.claimTask(existingTask,userToken);               
        // Convert the updated entity back to a DTO for the response
        return DTOMapper.INSTANCE.convertEntityToTaskGetDTO(claimed);
    }

    @PatchMapping("/tasks/{taskId}/quit")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void quitTasks(@PathVariable Long taskId, 
                                      @RequestHeader("Authorization") String authorizationHeader){
        
        // Validate the user token
        String userToken = validateAuthorizationHeader(authorizationHeader);
        teamService.validateTeamPaused(userToken);
        // check if task is in the same team as the user
        taskService.validateTaskInTeam(userToken, taskId);
        // Unclaims the task for the user and assigns the user to the correct field
        taskService.quitTask(taskId,userrepository.findByToken(userToken).getId());
    }


    @GetMapping("/tasks/expire")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public List<TaskGetDTO> expireTasks(@RequestHeader(value = "X-CRON-AUTH", required = false) String authHeader) {
        if (!"mySecureCronToken987".equals(authHeader)) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Access denied. Cron job only.");
        }

        // Get all tasks in the system
        List<Task> allTasks = taskService.getAllTasks();

        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        Date currentDate = today.getTime();

        List<TaskGetDTO> expiredTasks = new ArrayList<>();

        for (Task task : allTasks) {
            if (!Boolean.TRUE.equals(task.getActiveStatus())) continue;

            if (task.getDeadline().before(currentDate)) {
                if (task.getIsAssignedTo() != null) {
                    userService.deductExperiencePoints(task.getIsAssignedTo(), task.getValue());
                } else {
                    taskService.deductExperiencePointsFromAll(task.getTeamId(), task.getValue());
                }

                Date oldDeadline = task.getDeadline();
                task.setStartDate(oldDeadline);

                String taskType = taskService.checkTaskType(task);
                if (additionalTask.equals(taskType)) {
                    int daysVisible = task.getDaysVisible();
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTime(task.getStartDate());
                    calendar.add(Calendar.DATE, daysVisible);
                    task.setDeadline(calendar.getTime());
                } else {
                    taskService.calculateDeadlineOnExpire(task);
                }

                taskService.unLuckyDraw(task);
                taskService.unassignTask(task);
                taskService.saveTask(task);

                expiredTasks.add(DTOMapper.INSTANCE.convertEntityToTaskGetDTO(task));
            }
        }

        return expiredTasks;
    }


    @DeleteMapping("/tasks/{taskId}/finish")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public List<TaskGetDTO> finishTasks(@PathVariable Long taskId, @RequestHeader("Authorization") String authorizationHeader) {
        // Validate the user token
        String userToken = validateAuthorizationHeader(authorizationHeader);
        teamService.validateTeamPaused(userToken);
        
        // Check if task is in the same team as the user
        taskService.validateTaskInTeam(userToken, taskId);
        
        // Get the task by ID
        Task task = taskService.getTaskById(taskId);
        
        Long userId = userrepository.findByToken(userToken).getId();
        if (task.getIsAssignedTo() == null || !task.getIsAssignedTo().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only finish tasks that you have claimed");
        }

        if (!taskService.isTaskVisibleOrFinishable(task)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Task on cooldown. The task cannot be finished now");
        }

        userService.addExperiencePoints(userId, task.getValue());
        
        List<TaskGetDTO> updatedTasks = new ArrayList<>();
        
        // Lucky draw effect is removed
        taskService.unLuckyDraw(task);

        String taskType = taskService.checkTaskType(task);
        if (additionalTask.equals(taskType)) {
            taskService.deleteTask(taskId,userId);
        } else {
        
        taskService.calculateDeadlineOnFinish(task);
        
        taskService.unassignTask(task);

        taskService.finishedTaskDate(task);
        
        taskService.saveTask(task);
        
        updatedTasks.add(DTOMapper.INSTANCE.convertEntityToTaskGetDTO(task));
    }
    return updatedTasks;
}
    
    @DeleteMapping("/tasks/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTask(@PathVariable Long taskId, @RequestHeader("Authorization") String authorizationHeader) {
        // Validate the user token
        String userToken = validateAuthorizationHeader(authorizationHeader);
        teamService.validateTeamPaused(userToken);
        // check if task is in the same team as the user
        taskService.validateTaskInTeam(userToken, taskId);
        // Checks if user is the creator of the task and if task exists
        taskService.validateRecurringEdit(userToken, taskId);
        // Delete the task using the service
        taskService.deleteTask(taskId,userrepository.findByToken(userToken).getId());
    }

    private String validateAuthorizationHeader(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.trim().isEmpty() || !authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized: Missing or invalid Authorization header.");
        }
        return authorizationHeader.substring(7);  // Remove "Bearer " prefix
      }
}