
package ch.uzh.ifi.hase.soprafs24.listener;

import ch.uzh.ifi.hase.soprafs24.entity.Task;
import ch.uzh.ifi.hase.soprafs24.repository.TaskRepository;
import ch.uzh.ifi.hase.soprafs24.rest.dto.task.TaskGetDTO;
import ch.uzh.ifi.hase.soprafs24.rest.mapper.DTOMapper;
import ch.uzh.ifi.hase.soprafs24.service.WebSocketNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;

import java.util.Collections;
import java.util.List;

import javax.persistence.PostPersist;
import javax.persistence.PostRemove;
import javax.persistence.PostUpdate;

@Component
public class TaskEntityListener {

    private final Logger log = LoggerFactory.getLogger(TaskEntityListener.class);
    private static final String ENTITY_TYPE = "TASKS";
    private final WebSocketNotificationService notificationService;
    private final TaskRepository taskRepository;

    @Autowired
    public TaskEntityListener(@Lazy WebSocketNotificationService notificationService,@Lazy TaskRepository taskRepository ) {
        this.notificationService = notificationService;
        this.taskRepository = taskRepository;
    }
    List<TaskGetDTO> getCurrentTasksForTeamDTO(Long teamId) {
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

    private void sendNotification(Task task, String action) {
        if (task == null || task.getTeamId() == null) {
            log.warn("Task or Task.teamId is null. Cannot send notification for action: {}", action);
            return;
        }
        log.debug("TaskEntityListener: Sending notification for task ID: {}, Team ID: {}, Action: {}", task.getId(), task.getTeamId(), action);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    log.debug("Entity Listener (after commit): Notifying team {} about task {} (Action: {})", task.getTeamId(), task.getId(), action);
                    try {
                        notificationService.notifyTeamMembers(
                                task.getTeamId(),
                                ENTITY_TYPE, 
                                getCurrentTasksForTeamDTO(task.getTeamId()) 
                        );
                    } catch (Exception e) {
                        log.error("Error sending notification from TaskEntityListener after commit for task {}: {}", task.getId(), e.getMessage(), e);
                    }
                }
            });
        } else {
            
            
            log.debug("Entity Listener (no active transaction): Notifying team {} about task {} (Action: {})", task.getTeamId(), task.getId(), action);
             try {
                notificationService.notifyTeamMembers(
                        task.getTeamId(),
                        ENTITY_TYPE,
                        getCurrentTasksForTeamDTO(task.getTeamId())
                );
            } catch (Exception e) {
                log.error("Error sending notification from TaskEntityListener (no transaction) for task {}: {}", task.getId(), e.getMessage(), e);
            }
        }
    }

    @PostPersist
    public void afterTaskPersist(Task task) {
        log.debug("TaskEntityListener: @PostPersist triggered for task ID: {}", task.getId());
        sendNotification(task, "PERSIST");
    }

    @PostUpdate
    public void afterTaskUpdate(Task task) {
        log.debug("TaskEntityListener: @PostUpdate triggered for task ID: {}", task.getId());
        sendNotification(task, "UPDATE");
    }

    @PostRemove
    public void afterTaskRemove(Task task) {
        log.debug("TaskEntityListener: @PostRemove triggered for task ID: {}, Team ID: {}", task.getId(), task.getTeamId());
        
        sendNotification(task, "REMOVE");
    }
}