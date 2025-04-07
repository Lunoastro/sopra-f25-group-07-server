package ch.uzh.ifi.hase.soprafs24.service;

import ch.uzh.ifi.hase.soprafs24.entity.Task;
import ch.uzh.ifi.hase.soprafs24.repository.TaskRepository;
import ch.uzh.ifi.hase.soprafs24.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs24.rest.dto.task.TaskPostDTO;
import ch.uzh.ifi.hase.soprafs24.entity.User;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Date;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@Transactional
public class TaskService {

    private final UserService userService;
    private final Logger log = LoggerFactory.getLogger(TaskService.class);
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    @Autowired
    public TaskService(@Qualifier("taskRepository") TaskRepository taskRepository,
            @Qualifier("userRepository") UserRepository userRepository, UserService userService) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.userService = userService;
    }

    // validate PostDTO based on the fields
    public void validatePostDto(TaskPostDTO dto) {
    if (dto.getName() == null || dto.getName().isEmpty()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task name cannot be null or empty");
    }
    if (dto.getDeadline() == null || dto.getDeadline().before(new Date())) {
        throw new IllegalArgumentException("Invalid or past deadline provided.");
    }
}

    // validate the task based on the fields
    public void verifyTaskExistence(Task task) {
        if (taskRepository.findTaskById(task.getId()) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task already exists");
        }
    }

    public void verifyClaimStatus(Task task) {
        if (taskRepository.findTaskById(task.getIsAssignedTo()) != null) {
            log.debug("Task with name: {} is already claimed by user with id: {}", task.getName(), task.getIsAssignedTo());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task already claimed");
        }
    }

    public void validateToBeEditedFields(Task task, Task taskPutDTO) {
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task cannot be null");
        }
        if (taskPutDTO.getName() != null) {
            task.setName(taskPutDTO.getName());
        }
        // since a unclaimed status is allowed the null check doesn't work here
        if(taskPutDTO.getIsAssignedTo() != task.getIsAssignedTo()) {
            task.setIsAssignedTo(taskPutDTO.getIsAssignedTo());
        }
        if (taskPutDTO.getDescription() != null) {
            task.setDescription(taskPutDTO.getDescription());
        }
        if (taskPutDTO.getDeadline() != null) {
            task.setDeadline(taskPutDTO.getDeadline());
        }
        if (taskPutDTO.getColor() != null ) {
            task.setColor(taskPutDTO.getColor());
        }
        if (taskPutDTO.getActiveStatus() != task.getActiveStatus()) {
            task.setActiveStatus(taskPutDTO.getActiveStatus());
        }
    }

    public void validateUserToken(String userToken) {
        String token = UserService.verifyToken(userToken);
        User user = userRepository.findByToken(token);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found");
        }
        if (!userService.validateToken(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authorized (login required)");
        }
    }

    public void validateCreator(String userToken, Long taskId) {
        String token = UserService.verifyToken(userToken);
        User user = userRepository.findByToken(token);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found");
        }
        Task task = taskRepository.findTaskById(taskId);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        if (!task.getCreatorId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to edit this task");
        }
    }

    public List<Task> getFilteredTasks(Boolean isActive, String type) {
        // If both filters are null, return all tasks
        List<Task> allTasks = getAllTasks(); 
        
        // Filter by activeStatus (if active or inactive)
        if (isActive != null) {
            allTasks = allTasks.stream()
                           .filter(task -> task.getActiveStatus() == isActive) // True = active Tasks, False = inactive Tasks
                           .toList();
        }
    
        // Filter by type (recurring)
        if (type != null  && type.equalsIgnoreCase("recurring")) {
            allTasks = allTasks.stream()
                            .filter(task -> task.getFrequency() != null) // Check if frequency is null -> additional task
                            .toList();
        }
        return allTasks;
    }

    public Task createTask(Task task, String userToken) {
        verifyTaskExistence(task);
        validateUserToken(userToken);
        log.debug("Creating a new task with name: {}", task.getName());
        // set the task creation date
        task.setCreationDate(new Date(new Date().getTime() + 3600 * 1000));
        // store the userId of the creator
        task.setCreatorId(userRepository.findByToken(userToken.substring(7)).getId());
        return taskRepository.save(task);
    }

    public Task claimTask(Task task, String userToken) {
        log.debug("Claiming task with name: {}", task.getName());
        // verify that the task has not yet been claimed
        verifyClaimStatus(task);
        validateUserToken(userToken);
        // store the userId of the creator
        task.setIsAssignedTo(userRepository.findByToken(userToken.substring(7)).getId());
        return taskRepository.save(task);
    }

    public void deleteTask(Long taskId) {
        taskRepository.deleteById(taskId);
    }

    public Task updateTask(Task task, Task taskPutDTO) {
        validateToBeEditedFields(task, taskPutDTO);
        return taskRepository.save(task);
    }

    private List<Task> getAllTasks() {
        return taskRepository.findAll();
    }
    
    public Task getTaskById(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
    }

    public void pauseTask(Task task) {
        task.setPaused(true);
        task.setPausedDate(new Date());
        taskRepository.save(task);
    }

    public void unpauseTask(Task task) {
        task.setPaused(false);
        task.setUnpausedDate(new Date());
        taskRepository.save(task);
    }

    public void assignTask(Task task, Long userId) {
        task.setIsAssignedTo(userId);
        taskRepository.save(task);
    }
    public void unassignTask(Task task) {
        task.setIsAssignedTo(null);
        taskRepository.save(task);
    }
}
