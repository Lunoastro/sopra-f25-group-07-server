package ch.uzh.ifi.hase.soprafs24.service;

import ch.uzh.ifi.hase.soprafs24.entity.Task;
import ch.uzh.ifi.hase.soprafs24.repository.TaskRepository;
import ch.uzh.ifi.hase.soprafs24.repository.UserRepository;
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
    private final Logger log = LoggerFactory.getLogger(TeamService.class);
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    @Autowired
    public TaskService(@Qualifier("taskRepository") TaskRepository taskRepository,
            @Qualifier("userRepository") UserRepository userRepository, UserService userService) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.userService = userService;
    }

    // validate the task based on the fields
    public void validateTask(Task task) {
        if (task.getTaskName() == null || task.getTaskName().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task name cannot be null or empty");
        }
        if (task.getDeadline() == null || task.getDeadline().before(new Date())) {
            throw new IllegalArgumentException("Invalid or past deadline provided.");
        }
        if (taskRepository.findByTaskId(task.getTaskId()) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task already exists");
        }
    }

    public void validate_ToBeEditedFields(Task task, Task taskPutDTO) {
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task cannot be null");
        }
        // Update only the fields that are provided in the DTO
        if (taskPutDTO.getTaskName() != null) {
            task.setTaskName(taskPutDTO.getTaskName());
        }
        if (taskPutDTO.getTaskDescription() != null) {
            task.setTaskDescription(taskPutDTO.getTaskDescription());
        }
        if (taskPutDTO.getDeadline() != null) {
            task.setDeadline(taskPutDTO.getDeadline());
        }
        if (taskPutDTO.getTaskColor() != null) {
            task.setTaskColor(taskPutDTO.getTaskColor());
        }
        if (taskPutDTO.isActiveStatus() != task.isActiveStatus()) {
            task.setActiveStatus(taskPutDTO.isActiveStatus());
        }
    }

    public void validate_userToken(String userToken) {
        String token = UserService.check_if_malformed_token(userToken);
        User user = userRepository.findByToken(token);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found");
        }
        if (!userService.validateToken(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authorized (login required)");
        }
    }

    public void validate_creator(String userToken, Long taskId) {
        String token = UserService.check_if_malformed_token(userToken);
        User user = userRepository.findByToken(token);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found");
        }
        Task task = taskRepository.findByTaskId(taskId);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        if (!task.getIsAssignedTo().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to edit this task");
        }
    }

    // check uniqueness of the task name

    public Task createTask(Task task, String userToken) {
        validateTask(task);
        validate_userToken(userToken);
        log.debug("Creating a new task with name: {}", task.getTaskName());
        // set the task creation date
        task.setTaskCreationDate(new Date(new Date().getTime() + 3600 * 1000));
        // store the userId of the creator
        task.setIsAssignedTo(userRepository.findByToken(userToken.substring(7)).getId());
        return taskRepository.save(task);
        

    }


    public void deleteTask(Long taskId) {
        taskRepository.deleteById(taskId);
    }

    public Task updateTask(Task task, Task taskPutDTO) {
        validate_ToBeEditedFields(task, taskPutDTO);
        return taskRepository.save(task);
    }

    public List<Task> getAllTasks() {
        return taskRepository.findAll();
    }
    public Task getTaskById(Long taskId) {
        return taskRepository.findById(taskId).orElse(null);
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
