package ch.uzh.ifi.hase.soprafs24.service;

import ch.uzh.ifi.hase.soprafs24.entity.Task;
import ch.uzh.ifi.hase.soprafs24.repository.TaskRepository;
import ch.uzh.ifi.hase.soprafs24.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs24.rest.dto.TaskPutDTO;

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
    private final Logger log = LoggerFactory.getLogger(TeamService.class);
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    @Autowired
    public TaskService(@Qualifier("taskRepository") TaskRepository taskRepository,
            @Qualifier("userRepository") UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
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

    // check uniqueness of the task name

    public Task createTask(Task task, long userId) {
        validateTask(task);
        
        log.debug("Creating a new task with name: {} for user with ID: {}", task.getTaskName(), userId);
        String userName = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"))
            .getUsername();
        log.debug("Task is being created for user: {}", userName);
        
        // set the task creation date
        task.setTaskCreationDate(new Date(new Date().getTime() + 3600 * 1000));
        // store the userId of the creator
        task.setIsAssignedTo(userId);
        return taskRepository.save(task);

    }

    // update fields
    public void updateFields(Task existingTask, TaskPutDTO taskPutDTO) {
        if (taskPutDTO.getTaskName() != null) {
            existingTask.setTaskName((taskPutDTO.getTaskName()));
        }
        if (taskPutDTO.getTaskDescription() != null) {
            existingTask.setTaskDescription(taskPutDTO.getTaskDescription());
        }
        
        if (taskPutDTO.getDeadline() != null) {
            existingTask.setDeadline(taskPutDTO.getDeadline());
        }
    }


    public void deleteTask(Long taskId) {
        taskRepository.deleteById(taskId);
    }

    public Task updateTask(Task task) {
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
