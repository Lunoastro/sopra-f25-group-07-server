package ch.uzh.ifi.hase.soprafs24.service;

import ch.uzh.ifi.hase.soprafs24.entity.Task;
import ch.uzh.ifi.hase.soprafs24.repository.TaskRepository;
import ch.uzh.ifi.hase.soprafs24.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Date;

public class TaskService {
    private final Logger log = LoggerFactory.getLogger(TeamService.class);
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    @Autowired
    public TaskService(@Qualifier("TaskRepository") TaskRepository taskRepository,
            @Qualifier("UserRepository") UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
    }

    // validate the task based on the fields
    public void validateTask(Task task) {
        if (task.getTaskName() == null || task.getTaskName().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task name cannot be null or empty");
        }
        if (task.getDeadline() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deadline cannot be null");
        }
        if (task.getTaskDescription() == null || task.getTaskDescription().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task description cannot be null or empty");
        }
    }

    // check uniqueness of the task name

    public Task createTask(Task task) {
        log.debug("Creating a new task with name: {}", task.getTaskName());
        // Check if the task name already exists
        Task existingTask = taskRepository.findByTaskName(task.getTaskName());
        validateTask(existingTask);
        // set the fields
        return taskRepository.save(task);

    }

    public Task getTaskById(Long taskId) {
        return taskRepository.findById(taskId).orElse(null);
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

    /*
     * TODO:
     *  assign color, set “isAssignedTo” to ColorId)
     * Ensure each task has a title, deadline, and optional description.
     */

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
