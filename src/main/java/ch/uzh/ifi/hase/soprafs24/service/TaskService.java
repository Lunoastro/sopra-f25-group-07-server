package ch.uzh.ifi.hase.soprafs24.service;

import ch.uzh.ifi.hase.soprafs24.entity.Task;
import ch.uzh.ifi.hase.soprafs24.repository.TaskRepository;
import ch.uzh.ifi.hase.soprafs24.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs24.rest.dto.task.TaskGetDTO;
import ch.uzh.ifi.hase.soprafs24.rest.dto.task.TaskPostDTO;
import ch.uzh.ifi.hase.soprafs24.rest.mapper.DTOMapper;
import ch.uzh.ifi.hase.soprafs24.entity.User;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Date;
import java.util.Calendar;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import java.util.Collections;
import java.util.stream.Collectors;


@Service
@Transactional
public class TaskService {

    private final UserService userService;
    private final Logger log = LoggerFactory.getLogger(TaskService.class);
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final CalendarService calendarService;
    private final WebSocketNotificationService notificationService;
    private String recurringTask = "recurring"; 
    private String additionalTask = "additional"; 

    @Autowired
    public TaskService(@Qualifier("taskRepository") TaskRepository taskRepository,
            @Qualifier("userRepository") UserRepository userRepository, 
            @Qualifier("userService") UserService userService,
            @Qualifier("calendarService") CalendarService calendarService,
            @Qualifier("webSocketNotificationService") WebSocketNotificationService notificationService) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.calendarService = calendarService;
        this.notificationService = notificationService;
    }

    // validate PostDTO based on the fields
    public void validatePostDto(TaskPostDTO dto) {
        if (dto.getName() == null || dto.getName().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task name cannot be null or empty");
        }
        if (taskRepository.findTaskByName(dto.getName()) != null && !dto.getName().equals(taskRepository.findTaskByName(dto.getName()).getName())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task name already exists");
        }
        if (dto.getValue() == null || dto.getValue() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task value cannot be null or less than or equal to 0");
        }
        if (dto.getDeadline() == null) { //short type check (recurring or additional task)
            validateRecurringPostDto(dto); //recurring dto checks
        } else {
            if (dto.getDeadline().before(new Date())) {
                throw new IllegalArgumentException("Invalid or past deadline provided.");
            }
        }
    }

    private void validateRecurringPostDto(TaskPostDTO dto) {
        if (dto.getFrequency() == null || dto.getFrequency() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task frequency cannot be null or less than or equal to 0");
        }
        if (dto.getStartDate() != null && dto.getStartDate().before(new Date())) {
            throw new IllegalArgumentException("Invalid or past start date provided.");
        }
        if (dto.getDaysVisible() == null && dto.getDaysVisible() > dto.getFrequency()) { // half also includes 0 and negative values
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task daysVisible cannot be null or greater than half of frequency (exception: if frequency = 1, daysVisible = 1)");
        }
    }

    public void validateToBeEditedFields(Task task, Task taskPutDTO) {
        validateCommonTaskPutDto(task, taskPutDTO); // validate common fields
        if (recurringTask.equals(checkTaskType(task))) {
            validateRecurringPutDto(task, taskPutDTO); // validate recurring fields
            calculateDeadline(task);
            checkDaysVisible(task); // check if daysVisible is valid (daysVisible >= half of frequency)
        } else {
            if (taskPutDTO.getDeadline() != null) { //validate additional task
                if (taskPutDTO.getDeadline().before(new Date())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deadline must be in the future");
                }
                task.setDeadline(taskPutDTO.getDeadline());
                calculateDaysVisible(task);
            }
        }
    }
    
    private void validateCommonTaskPutDto(Task task, Task taskPutDTO) {
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task cannot be null");
        }
        if (taskPutDTO.getName() != null) {
            if (taskPutDTO.getName().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task name cannot be empty");
            }
            if (taskRepository.findTaskByName(taskPutDTO.getName()) != null && !taskPutDTO.getName().equals(task.getName())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Task name already exists");
            }
            task.setName(taskPutDTO.getName());
        }
        if (taskPutDTO.getValue() != null) {
            if (taskPutDTO.getValue() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task value must be greater than 0");
            }
            task.setValue(taskPutDTO.getValue());
        }
        if (taskPutDTO.getDescription() != null) {
            task.setDescription(taskPutDTO.getDescription());
        }
        if (taskPutDTO.getIsAssignedTo() != null) {
            verifyClaimStatus(task);
            task.setIsAssignedTo(taskPutDTO.getIsAssignedTo());
        }
    }

    public void validateRecurringPutDto(Task task, Task taskPutDTO) {
        if (taskPutDTO.getActiveStatus() != null && !taskPutDTO.getActiveStatus().equals(task.getActiveStatus())) {
            task.setActiveStatus(taskPutDTO.getActiveStatus());
        }
        if (taskPutDTO.getFrequency() != null) {
            if (taskPutDTO.getFrequency() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Frequency must be greater than 0");
            }
            task.setFrequency(taskPutDTO.getFrequency());
        }
        int half = getHalfFrequency(task);
        if (taskPutDTO.getDaysVisible() != null) {
            if (taskPutDTO.getDaysVisible() < half || taskPutDTO.getDaysVisible() > task.getFrequency()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "daysVisible must be at least half of the frequency (or 1 if frequency is 1) but not greater than the frequency");
            }
            task.setDaysVisible(taskPutDTO.getDaysVisible());
        }
        if (taskPutDTO.getStartDate() != null) {
            if (taskPutDTO.getStartDate().before(new Date())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start date must be in the future");
            }
            task.setStartDate(taskPutDTO.getStartDate());
        }
    }

    public void validateUserToken(String token) {
        User user = userRepository.findByToken(token);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found");
        }
        if (user.getTeamId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not in team");
        }
        if (!userService.validateToken(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authorized (login required)");
        }
    }

    public void validateCreator(String userToken, Long taskId) {
        User user = userRepository.findByToken(userToken);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found");
        }
        Task task = taskRepository.findTaskById(taskId);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        if (!task.getcreatorId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to edit this task");
        }
        Long assignee = task.getIsAssignedTo(); // may be null
        if (assignee != null && !assignee.equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to edit: task is currently claimed by another user");
        }
    }

    public void validateRecurringEdit(String userToken, Long taskId) {
        Task task = taskRepository.findTaskById(taskId);
        String taskType = checkTaskType(task);
        if (taskType.equals(additionalTask)) { // if addtional task, validate more
            validateCreator(userToken, taskId);
        }
        //if task is recurring, we dont test anything else
    }

    public void validateTaskInTeam(String userToken, Long taskId) {
        Task task = taskRepository.findTaskById(taskId);
        Long userTeamId = userRepository.findByToken(userToken).getTeamId();
        if (!task.getTeamId().equals(userTeamId)) { //if task and user are not in the same team
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Task does not belong to the team of the user");
        }
    }

    public List<Task> getFilteredTasks(Boolean isActive, String type) {
        // If both filters are null, return all tasks
        List<Task> allTasks = getAllTasks();
        // Filter by activeStatus (if active or inactive)
        if (isActive != null) {
            allTasks = allTasks.stream()
                           .filter(task -> isActive.equals(task.getActiveStatus())) // True = active Tasks, False = inactive Tasks
                        .collect(Collectors.toList());
        }
        // Filter by type (recurring)
        if (type != null) {
            allTasks = allTasks.stream()
                            .filter(task -> checkTaskType(task).equalsIgnoreCase(type)) // Check if frequency is null -> additional task
                            .collect(Collectors.toList());
        }
        return allTasks;
    }

    public void pauseAllTasksInTeam() {
        List<Task> tasks = getAllTasks();
        for (Task task : tasks) {
            task.setPaused(true);
            task.setPausedDate(new Date());
        }
        taskRepository.saveAll(tasks);
    }

    public void unpauseAllTasksInTeam() {
        List<Task> tasks = getAllTasks();
        for (Task task : tasks) {
            task.setPaused(false);
            task.setUnpausedDate(new Date());
            newDeadline(task); // update the deadline based on the paused time
        }
        taskRepository.saveAll(tasks);
    }

    private void newDeadline(Task task) {
        // Calculate paused time in milliseconds
        long pausedTimeMillis = task.getUnpausedDate().getTime() - task.getPausedDate().getTime();
        
        // Convert to days (as double), then round up to next full day
        double pausedDays = (double) pausedTimeMillis / (1000 * 60 * 60 * 24);
        int daysToAdd = (int) Math.ceil(pausedDays);
    
        // Add the rounded-up number of days to the deadline
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(task.getDeadline());
        calendar.add(Calendar.DAY_OF_MONTH, daysToAdd);
        task.setDeadline(calendar.getTime());

        // Recalculate daysVisible based on the new deadline (only for additional tasks)
        String type = checkTaskType(task);
        if (additionalTask.equals(type)) {
            calculateDaysVisible(task); // recalculate daysVisible
        }
    }
    
    public void checkIsPaused(Task task) { // check if task is paused
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task cannot be null");
        }
        if (task.isPaused()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Task is paused");
        }
    }

    public Task createTask(Task task, String userToken) {
        verifyTaskExistence(task);
        validateUserToken(userToken);
        checkIsPaused(task);
        String taskType = checkTaskType(task);
        log.debug("Creating a new task with name: {}", task.getName());
        // set the task creation date
        task.setCreationDate(new Date(new Date().getTime() + 3600 * 1000));
        // store the userId of the creator
        task.setcreatorId(userRepository.findByToken(userToken).getId());
        // enforce that the task colour is initially set to white 
        task.setColor(null);
        // set the task teamId
        task.setTeamId(userRepository.findByToken(userToken).getTeamId());
        // store status of the task
        task.setActiveStatus(true);
        if (recurringTask.equals(taskType)) { // check if task is recurring
            // if start Date wasnt given, we use a default value
            if (task.getStartDate() == null) { 
                task.setStartDate((task.getCreationDate()));
            }
            // if daysVisible was not given, we use a default value
            if (task.getDaysVisible() == null) {
                int half = getHalfFrequency(task);
                task.setDaysVisible(half); // half of the frequency is the default minimum cooldown period
            }
            // calculation and setting of new deadline; start Date + frequency = deadline
            calculateDeadline(task);
        }
        else { // if task is additional task
            calculateDaysVisible(task); // set daysVisible to the difference between deadline and creation date -> easy filtering for pinboard
        }
        calendarService.syncSingleTask(task, task.getcreatorId());
        taskRepository.save(task);
        taskRepository.flush();
        // Notify all users in the team about the new task
        notificationService.notifyTeamMembers(task.getTeamId(), "task", getCurrentTasksForTeamDTO(task.getTeamId()));
        log.info("Task with name: {} created successfully", task.getName());
        return task;
    }

    public Task claimTask(Task task, String userToken) {
        log.debug("Claiming task with name: {}", task.getName());
        // verify that the task has not yet been claimed
        verifyClaimStatus(task);
        validateUserToken(userToken);
        checkIsPaused(task);
        // store the userId of the creator
        User user = userRepository.findByToken(userToken);
        task.setIsAssignedTo(user.getId());
        //set the task color to the color of the user who claimed it
        if(user.getColor() != null) {
            task.setColor(user.getColor());
        } else{
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User has no color set");
        }
        taskRepository.save(task);
        taskRepository.flush();
        // Notify all users in the team about the claimed task
        notificationService.notifyTeamMembers(task.getTeamId(), "task", getCurrentTasksForTeamDTO(task.getTeamId()));
        log.info("Task with name: {} claimed successfully by user with id: {}", task.getName(), user.getId());
        return task;
    }

    public void quitTask(Long taskId, Long userId) {
        log.debug("User {} attempting to quit task {}", userId, taskId);
        Task task = getTaskById(taskId); // Throws 404 if not found

        // Check if the task is actually assigned
        if (task.getIsAssignedTo() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task is not currently assigned.");
        }
        // Check if the user is the one assigned to the task
        if (!Objects.equals(task.getIsAssignedTo(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not assigned to this task, so you cannot quit it.");
        }


        // If checks pass, unassign the task
        unassignTask(task);
        log.info("User {} successfully quit task {}", userId, taskId);
    }
    public void updateAllTaskColors(User user) { // update all tasks of a user with the color of the user -> FUTURE USE ONCE WE HAVE WEBSOCKET
        List<Task> userTasks = taskRepository.findTaskByIsAssignedTo(user.getId());
        for (Task task : userTasks) {
            task.setColor(user.getColor());
        }
        taskRepository.saveAll(userTasks);
    }

    public void deleteTask(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        checkIsPaused(task);
        calendarService.syncSingleTask(task, task.getcreatorId());
        taskRepository.deleteById(taskId);
        taskRepository.flush();
        // Notify all users in the team about the deleted task
        notificationService.notifyTeamMembers(task.getTeamId(), "task", getCurrentTasksForTeamDTO(task.getTeamId()));
        log.info("Task with id: {} deleted successfully", taskId);
    }

    public Task updateTask(Task task, Task taskPutDTO) {
        checkIsPaused(task);
        validateToBeEditedFields(task, taskPutDTO);
        calendarService.syncSingleTask(task, task.getcreatorId());
        taskRepository.save(task);
        taskRepository.flush();
        // Notify all users in the team about the updated task
        notificationService.notifyTeamMembers(task.getTeamId(), "task", getCurrentTasksForTeamDTO(task.getTeamId()));
        log.info("Task with name: {} updated successfully", task.getName());
        return task;
    }

    public void saveTask(Task task) {
        taskRepository.save(task);
    }
    
    public Task getTaskById(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
    }

    public void pauseTask(Task task) { // -> will need to be updated to pause all tasks of the entire team; fetch all tasks then pause them
        task.setPaused(true);
        task.setPausedDate(new Date());
        taskRepository.save(task);
    }

    public void unpauseTask(Task task) { // -> will need to be updated to unpause all tasks of the entire team; fetch all tasks then unpause them + update every deadline using total paused time
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
        task.setColor(null); // Reset color to default when unassigned
        taskRepository.save(task);
    }

    //-------------------------------------helper functions here-------------------------------------------------
    private List<TaskGetDTO> getCurrentTasksForTeamDTO(Long teamId) {
        if (teamId == null) {
            return Collections.emptyList();
        }
        List<Task> teamTasks = taskRepository.findAll().stream()
                                    .filter(t -> teamId.equals(t.getTeamId()))
                                    .toList();
    
        return teamTasks.stream()
                        .map(DTOMapper.INSTANCE::convertEntityToTaskGetDTO)
                        .toList();
    }

    public String checkTaskType(Task task) {
        String taskType;
        if (task.getFrequency() != null) {
            taskType = recurringTask;
        } else {
            taskType = additionalTask;
        }
        return taskType;
    }

    // validate the task based on the fields
    private void verifyTaskExistence(Task task) {
        if (taskRepository.findTaskById(task.getId()) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task already exists");
        }
    }

    private void verifyClaimStatus(Task task) {
        if (task.getIsAssignedTo() != null) {
            log.debug("Task with name: {} is already claimed by user with id: {}", task.getName(), task.getIsAssignedTo());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task already claimed (Needs to be released first)");
        }
    }

    private int getHalfFrequency(Task task) {
        int half;
        if (task.getFrequency() == 1) { // SPECIAL CASE: if frequency is 1, we set half to 1 as daysVisible can never be 0
            half = 1;
        } else {
            half = task.getFrequency() / 2; // half of the frequency is the default minimum cooldown period 
        }
        return half;
    }

    public void calculateDeadline(Task task) {
        // calculate deadline = startDate + frequency days -> recurring task
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(task.getStartDate());
        calendar.add(Calendar.DATE, task.getFrequency());
        Date deadline = calendar.getTime();
        task.setDeadline(deadline);
    }

    public void calculateDaysVisible(Task task) {
        long millisDiff = task.getDeadline().getTime() - task.getCreationDate().getTime();
        double diffInDays = (double) millisDiff / (1000 * 60 * 60 * 24);
        int daysDiff = (int) Math.ceil(diffInDays);
        task.setDaysVisible(daysDiff);
    }
    
    private void checkDaysVisible(Task task) {
        String taskType = checkTaskType(task);
        if (recurringTask.equals(taskType) && task.getDaysVisible() < getHalfFrequency(task) && task.getDaysVisible() != 1) {
            // check daysVisible >= half of frequency and special case for frequency = 1
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "daysVisible must be at least half of the frequency (or 1 if frequency is 1)");
        } 
    }

    private List<Task> getAllTasks() {
        return taskRepository.findAll();
    }
}
