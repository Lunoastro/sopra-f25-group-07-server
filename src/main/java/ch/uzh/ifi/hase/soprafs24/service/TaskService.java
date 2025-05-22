package ch.uzh.ifi.hase.soprafs24.service;

import ch.uzh.ifi.hase.soprafs24.entity.Task;
import ch.uzh.ifi.hase.soprafs24.entity.Team;
import ch.uzh.ifi.hase.soprafs24.repository.TaskRepository;
import ch.uzh.ifi.hase.soprafs24.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs24.repository.TeamRepository;
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
import java.util.Calendar;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.Comparator;

@Service
@Transactional(rollbackFor = Exception.class)
public class TaskService {

    private final UserService userService;
    private final Logger log = LoggerFactory.getLogger(TaskService.class);
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final CalendarService calendarService;
    private String recurringTask = "recurring";
    private String additionalTask = "additional";

    @Autowired
    public TaskService(@Qualifier("taskRepository") TaskRepository taskRepository,
            @Qualifier("userRepository") UserRepository userRepository,
            @Qualifier("teamRepository") TeamRepository teamRepository,
            @Qualifier("userService") UserService userService,
            @Qualifier("calendarService") CalendarService calendarService) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
        this.userService = userService;
        this.calendarService = calendarService;
    }

    /** Copilot generated this documentation
     * Locks a task for a specific user.
     *
     * @param taskId The ID of the task to lock.
     * @param userId The ID of the user who is locking the task.
     * @return The updated Task entity.
     * @throws ResponseStatusException if the task is not found, or if the task is
     *                                 already locked by another user.
     */
    @Transactional
    public Task lockTask(Long taskId, Long userId) {
        log.debug("Attempting to lock task {} for user {}", taskId, userId);

        Task task = taskRepository.findById(taskId)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found with ID: " + taskId));

        if (task.getLockedByUser() != null && !task.getLockedByUser().equals(userId)) {
            log.warn("Task {} is already locked by user {}. User {} cannot lock it.", taskId, task.getLockedByUser(),
                    userId);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task is already locked by another user.");
        }

        task.setLockedByUser(userId);
        Task savedTask = taskRepository.save(task);
        // The TaskEntityListener will be triggered by the save operation.
        log.info("Task {} locked by user {}", taskId, userId);
        return savedTask;
    }

    /** Copilot generated this documentation 
     * Unlocks a task, allowing any authorized user to unlock it if they were the
     * one who locked it.
     *
     * @param taskId The ID of the task to unlock.
     * @param userId The ID of the user attempting to unlock the task.
     * @return The updated Task entity.
     * @throws ResponseStatusException if the task is not found, not locked, or
     *                                 locked by a different user.
     */
    @Transactional
    public Task unlockTask(Long taskId, Long userId) {
        log.debug("Attempting to unlock task {} by user {}", taskId, userId);

        Task task = taskRepository.findById(taskId)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found with ID: " + taskId));

        if (task.getLockedByUser() == null) {
            log.warn("Task {} is not locked. User {} cannot unlock it.", taskId, userId);
            // Depending on desired behavior, you might allow this or throw an error.
            // For now, let's consider it a bad request if trying to unlock an already
            // unlocked task.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task is not currently locked.");
        }

        if (!task.getLockedByUser().equals(userId)) {
            log.warn("Task {} is locked by user {}. User {} cannot unlock it.", taskId, task.getLockedByUser(), userId);
            // Add logic here if you want to allow admins or other roles to override locks
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not the user who locked this task.");
        }

        task.setLockedByUser(null);
        Task savedTask = taskRepository.save(task);
        // The TaskEntityListener will be triggered by the save operation.
        log.info("Task {} unlocked by user {}", taskId, userId);
        return savedTask;
    }

    // validate PostDTO based on the fields
    public void validatePostDto(TaskPostDTO dto) {
        if (dto.getName() == null || dto.getName().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task name cannot be null or empty");
        }
        if (dto.getValue() == null || dto.getValue() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Task value cannot be null or less than or equal to 0");
        }
        if (dto.getDeadline() == null) { // short type check (recurring or additional task)
            validateRecurringPostDto(dto); // recurring dto checks
        } else {
            if (dto.getDeadline().before(new Date())) {
                throw new IllegalArgumentException("Invalid or past deadline provided.");
            }
        }
    }

    private void validateRecurringPostDto(TaskPostDTO dto) {
        if (dto.getFrequency() == null || dto.getFrequency() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Task frequency cannot be null or less than or equal to 0");
        }
        if (dto.getStartDate() != null && dto.getStartDate().before(new Date())) {
            throw new IllegalArgumentException("Invalid or past start date provided.");
        }
        int half = dto.getFrequency() / 2;
        if (half == 0) { // SPECIAL CASE: if frequency is 1, we set half to 1 as daysVisible can never be
                         // 0
            half = 1;
        }
        if (dto.getDaysVisible() != null && (dto.getDaysVisible() > half || dto.getDaysVisible() <= 0)) { // half also
                                                                                                          // includes 0
                                                                                                          // and
                                                                                                          // negative
                                                                                                          // values
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Task daysVisible cannot be null or greater than half of frequency (exception: if frequency = 1, daysVisible = 1)");
        }
    }

    public void validateToBeEditedFields(Task task, Task taskPutDTO) {
        validateCommonTaskPutDto(task, taskPutDTO); // validate common fields
        if (recurringTask.equals(checkTaskType(task))) {
            validateRecurringPutDto(task, taskPutDTO); // validate recurring fields
            checkDaysVisible(task); // check if daysVisible is valid (daysVisible >= half of frequency)
        } else {
            if (taskPutDTO.getDeadline() != null) { // validate additional task
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
            calculateDeadline(task);
        }
        int half = getHalfFrequency(task);
        if (taskPutDTO.getDaysVisible() != null) {
            if (taskPutDTO.getDaysVisible() > half || taskPutDTO.getDaysVisible() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "daysVisible can be at most half of the frequency (or 1 if frequency is 1) but not lower than 1");
            }
            task.setDaysVisible(taskPutDTO.getDaysVisible());
        }
        if (taskPutDTO.getStartDate() != null) {
            if (taskPutDTO.getStartDate().before(new Date())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start date must be in the future");
            }
            task.setStartDate(taskPutDTO.getStartDate());
            calculateDeadline(task);
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
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Not authorized to edit: task is currently claimed by another user");
        }
    }

    public void validateRecurringEdit(String userToken, Long taskId) {
        Task task = taskRepository.findTaskById(taskId);
        String taskType = checkTaskType(task);
        if (taskType.equals(additionalTask)) { // if addtional task, validate more
            validateCreator(userToken, taskId);
        }
        // if task is recurring, we dont test anything else
    }

    public void validateTaskInTeam(String userToken, Long taskId) {
        Task task = taskRepository.findTaskById(taskId);
        Long userTeamId = userRepository.findByToken(userToken).getTeamId();
        if (!task.getTeamId().equals(userTeamId)) { // if task and user are not in the same team
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Task does not belong to the team of the user");
        }
    }

    public List<Task> getFilteredTasks(Boolean isActive, String type) {
        // If both filters are null, return all tasks
        List<Task> allTasks = getAllTasks();
        // Filter by activeStatus (if active or inactive)
        if (isActive != null) {
            allTasks = allTasks.stream()
                    .filter(task -> isActive.equals(task.getActiveStatus())) // True = active Tasks, False = inactive
                                                                             // Tasks
                    .collect(Collectors.toList());
        }
        // Filter by type (recurring)
        if (type != null) {
            allTasks = allTasks.stream()
                    .filter(task -> checkTaskType(task).equalsIgnoreCase(type)) // Check if frequency is null ->
                                                                                // additional task
                    .collect(Collectors.toList());
        }
        return allTasks;
    }

    public List<Task> luckyDrawTasks(Long userTeamId) {
        if (userTeamId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User team ID cannot be null");
        }
        List<Task> activeTasks = getFilteredTasks(true, null);
        // Filter out tasks that are already claimed
        if (activeTasks == null || activeTasks.isEmpty()) {
            // Return an empty list if no tasks are provided
            return List.of();
        }
        // Filter tasks that are not assigned to anyone and belong to the user's team
        List<Task> unclaimedTasks = activeTasks.stream()
                .filter(task -> task.getIsAssignedTo() == null) // Check if the task is not assigned to anyone
                .filter(task -> task.getTeamId().equals(userTeamId)) // Check if the task belongs to the user's team
                .collect(Collectors.toList());

        for (Task task : unclaimedTasks) {
            // Set the color to null for unclaimed tasks
            task.setLuckyDraw(true);
        }
        return unclaimedTasks;
    }

    public List<Task> autodistributeTasks(Long userTeamId) {
        // Step 1: Load the team entity
        Team team = teamRepository.findTeamById(userTeamId);
        if (team == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found with ID " + userTeamId);
        }

        // Step 2: Get the team members (user IDs), then load full User entities
        List<Long> memberIds = team.getMembers();
        List<User> teamMembers = userRepository.findAllById(memberIds);
        if (teamMembers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No users found for team ID " + userTeamId);
        }

        // Step 3: Get all unclaimed tasks for this team
        List<Task> unclaimedTasks = getFilteredTasks(true, null).stream()
                .filter(task -> task.getIsAssignedTo() == null)
                .filter(task -> task.getTeamId().equals(userTeamId))
                .sorted(Comparator.comparingInt(Task::getValue).reversed()) // Sort by XP desc
                .collect(Collectors.toList());

        if (unclaimedTasks.isEmpty()) {
            return List.of();
        }

        // Step 4: Sort users by XP ascending
        teamMembers.sort(Comparator.comparingInt(User::getXp));

        // Step 5: Round-robin XP assignment
        int userIndex = 0;
        for (Task task : unclaimedTasks) {
            User user = teamMembers.get(userIndex);
            task.setIsAssignedTo(user.getId());
            task.setColor(user.getColor());

            userRepository.save(user);
            taskRepository.save(task);

            userIndex = (userIndex + 1) % teamMembers.size();
        }

        return unclaimedTasks;
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

    public List<Task> getTasksAssignedToUser(Long userId) {
        return taskRepository.findAll().stream()
                .filter(task -> userId.equals(task.getIsAssignedTo()))
                .collect(Collectors.toList());
    }

    public List<Task> getTasksCreatedByUser(Long userId) {
        return taskRepository.findAll().stream()
                .filter(task -> userId.equals(task.getcreatorId()))
                .collect(Collectors.toList());
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
        // store the name of the creator
        task.setCreatorName(userRepository.findByToken(userToken).getUsername());
        // enforce that the task colour is initially set to white
        task.setColor(null);
        // set the task teamId
        task.setTeamId(userRepository.findByToken(userToken).getTeamId());
        // store status of the task
        task.setActiveStatus(true);
        // if start Date wasnt given, we use a default value
        if (task.getStartDate() == null) {
            task.setStartDate((task.getCreationDate()));
        }
        if (recurringTask.equals(taskType)) { // check if task is recurring
            // if daysVisible was not given, we use a default value
            if (task.getDaysVisible() == null) {
                int half = getHalfFrequency(task);
                task.setDaysVisible(half); // half of the frequency is the default maximum cooldown period
            }
            // calculation and setting of new deadline; start Date + frequency = deadline
            calculateDeadline(task);
        } else { // if task is additional task
            calculateDaysVisible(task); // set daysVisible to the difference between deadline and creation date -> easy
                                        // filtering for pinboard
        }
        calendarService.syncSingleTask(task, task.getcreatorId());
        taskRepository.save(task);
        taskRepository.flush();
        // Notify all users in the team about the new task

        log.info("Task with name: {} created successfully", task.getName());
        return task;
    }

    public Task claimTask(Task task, String userToken) {
        log.debug("Claiming task with name: {}", task.getName());
        // verify that the task has not yet been claimed
        verifyClaimStatus(task);
        validateUserToken(userToken);
        checkIsPaused(task);
        // store the userId and name of the assignee
        User user = userRepository.findByToken(userToken);
        task.setIsAssignedTo(user.getId());
        task.setAssigneeName(user.getUsername());
        // set the task color to the color of the user who claimed it
        if (user.getColor() != null) {
            task.setColor(user.getColor());
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User has no color set");
        }
        taskRepository.save(task);
        taskRepository.flush();
        // Notify all users in the team about the claimed task

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
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are not assigned to this task, so you cannot quit it.");
        }
        // Check if the task is has been lucky drawn
        verifyLuckyDraw(task);
        // If checks pass, unassign the task
        unassignTask(task);

        log.info("Task with id: {} unassigned successfully", taskId);
        log.info("User {} successfully quit task {}", userId, taskId);
    }

    public void updateAllTaskColors(User user) { // update all tasks of a user with the color of the user
        List<Task> userTasks = taskRepository.findTaskByIsAssignedTo(user.getId());
        for (Task task : userTasks) {
            task.setColor(user.getColor());
        }
        taskRepository.saveAll(userTasks);
    }

    public void deleteTask(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        checkLockedByUser(task);
        checkIsPaused(task);
        verifyLuckyDraw(task);
        task.setActiveStatus(false);
        calendarService.syncSingleTask(task, task.getcreatorId());
        taskRepository.deleteById(taskId);
        taskRepository.flush();
        // Notify all users in the team about the deleted task

        log.info("Task with id: {} deleted successfully", taskId);
    }

    public Task updateTask(Task task, Task taskPutDTO) {
        checkLockedByUser(task);
        checkIsPaused(task);
        validateToBeEditedFields(task, taskPutDTO);
        calendarService.syncSingleTask(task, task.getcreatorId());
        taskRepository.save(task);
        taskRepository.flush();
        log.info("Task with name: {} updated successfully", task.getName());
        return task;
    }

    public void deductExperiencePointsFromAll(Long teamId, Integer experiencePoints) {
        List<User> teamMembers = userRepository.findByTeamId(teamId);

        if (teamMembers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No users found for the given team.");
        }
        // rounds down experience points to the nearest integer
        int deductionPerUser = experiencePoints / teamMembers.size();

        for (User user : teamMembers) {
            userService.deductExperiencePoints(user.getId(), deductionPerUser);
        }
    }

    public void saveTask(Task task) {
        taskRepository.save(task);
    }

    public Task getTaskById(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
    }

    public void pauseTask(Task task) { // -> will need to be updated to pause all tasks of the entire team; fetch all
                                       // tasks then pause them
        task.setPaused(true);
        task.setPausedDate(new Date());
        taskRepository.save(task);
    }

    public void unpauseTask(Task task) { // -> will need to be updated to unpause all tasks of the entire team; fetch
                                         // all tasks then unpause them + update every deadline using total paused time
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
        task.setAssigneeName(null);
        task.setColor(null); // Reset color to default when unassigned
        taskRepository.save(task);
    }

    // -------------------------------------helper functions
    // here-------------------------------------------------

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

    private void verifyLuckyDraw(Task task) {
        // Check if the task is has been lucky drawn
        if (Boolean.TRUE.equals(task.getLuckyDraw())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "You cannot quit a task that has been lucky drawn.");
        }
    }

    public void unLuckyDraw(Task task) {
        // Check if the task is has been lucky drawn
        if (Boolean.TRUE.equals(task.getLuckyDraw())) {
            task.setLuckyDraw(false);
        }
    }

    private void verifyClaimStatus(Task task) {
        if (task.getIsAssignedTo() != null) {
            log.debug("Task with name: {} is already claimed by user with id: {}", task.getName(),
                    task.getIsAssignedTo());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task already claimed (Needs to be released first)");
        }
    }

    private int getHalfFrequency(Task task) {
        int half;
        if (task.getFrequency() == 1) { // SPECIAL CASE: if frequency is 1, we set half to 1 as daysVisible can never be
                                        // 0
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
        if (recurringTask.equals(taskType) && task.getDaysVisible() > getHalfFrequency(task)) {
            // check daysVisible >= half of frequency and special case for frequency = 1
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "daysVisible can at most be half of the frequency (or 1 if frequency is 1)");
        }
    }

    public void checkLockedByUser(Task task) {
        if (task.getLockedByUser() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task is locked by user: " + task.getLockedByUser());
        }
    }

    private List<Task> getAllTasks() {
        return taskRepository.findAll();
    }
}
