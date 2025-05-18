
package ch.uzh.ifi.hase.soprafs24.listener;


import ch.uzh.ifi.hase.soprafs24.entity.User;
import ch.uzh.ifi.hase.soprafs24.rest.dto.user.UserGetDTO;
import ch.uzh.ifi.hase.soprafs24.service.TeamService;
import ch.uzh.ifi.hase.soprafs24.service.UserService;
import ch.uzh.ifi.hase.soprafs24.service.WebSocketNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.persistence.PostPersist;
import javax.persistence.PostRemove;
import javax.persistence.PostUpdate;
import java.util.Collections;
import java.util.List;
@Component
public class UserEntityListener {
    private static final String ENTITY_TYPE = "TEAM";
    private final Logger log = LoggerFactory.getLogger(UserEntityListener.class);
    private final WebSocketNotificationService notificationService;
    private final TeamService teamService; 

    @Autowired
    public UserEntityListener(@Lazy WebSocketNotificationService notificationService,
            @Lazy TeamService teamService,
            @Lazy UserService userService) {
        this.notificationService = notificationService;
        this.teamService = teamService;
    }

    

    
    private void sendUserChangeNotification(User user, String action) {
        if (user == null || user.getId() == null) {
            log.warn("User or User.id is null. Cannot send {} notification.", action);
            return;
        }

        final Long userId = user.getId();
        final Long teamId = user.getTeamId(); 

        if (teamId == null) {
            log.debug("User {} (Action: {}) has no assigned teamId. No team notification sent.", userId, action);
            return;
        }

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        
                        List<UserGetDTO> teamMembersPayload = teamService.getCurrentMembersForTeam(teamId);

                        if (teamMembersPayload == null) { 
                                                          
                            log.warn(
                                    "Failed to get current members for team {} after user {} {}. No notification sent.",
                                    teamId, userId, action);
                            teamMembersPayload = Collections.emptyList(); 
                        }
                        log.debug(
                                "UserEntityListener (after commit): Notifying team {} due to user {} {} (Action: {}). Payload: List of {} members.",
                                teamId, userId, action, action, teamMembersPayload.size());
                        notificationService.notifyTeamMembers(teamId, ENTITY_TYPE, teamMembersPayload);

                    } catch (Exception e) {
                        log.error(
                                "Error sending {} notification from UserEntityListener after commit for user {} (team {}): {}",
                                action, userId, teamId, e.getMessage(), e);
                    }
                }
            });
        } else {
            log.warn(
                    "UserEntityListener: No active transaction for user action {}. Notification for user {} (team {}) might not reflect committed state.",
                    action, userId, teamId);
        }
    }

    
    private void sendUserRemoveNotification(User user, String action) {
        if (user == null || user.getId() == null) {
            log.warn("User or User.id is null. Cannot send {} notification.", action);
            return;
        }

        final Long userId = user.getId();
        final Long teamId = user.getTeamId(); 

        if (teamId == null) {
            log.debug(
                    "Deleted user {} (Action: {}) had no assigned teamId. No specific team notification for member list sent.",
                    userId, action);
            
            
            
            
            
            
            
            
            
            log.debug("User {} was deleted and had no team. No team member list to send.", userId);
            return;
        }

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        
                        
                        List<UserGetDTO> teamMembersPayload = teamService.getCurrentMembersForTeam(teamId);

                        if (teamMembersPayload == null) {
                            log.warn(
                                    "Failed to get current members for team {} after user {} deletion. No notification sent.",
                                    teamId, userId);
                            teamMembersPayload = Collections.emptyList();
                        }

                        log.debug(
                                "UserEntityListener (after commit): Notifying team {} due to user {} DELETION (Action: {}). Payload: List of {} members.",
                                teamId, userId, action, teamMembersPayload.size());
                        notificationService.notifyTeamMembers(teamId, ENTITY_TYPE, teamMembersPayload);

                    } catch (Exception e) {
                        log.error(
                                "Error sending {} (user deletion) notification from UserEntityListener after commit for user {} (team {}): {}",
                                action, userId, teamId, e.getMessage(), e);
                    }
                }
            });
        } else {
            log.warn(
                    "UserEntityListener: No active transaction for user action {}. Notification for user {} (team {}) might not reflect committed state.",
                    action, userId, teamId);
        }
    }

    @PostPersist
    public void afterUserPersist(User user) {
        log.debug("UserEntityListener: @PostPersist triggered for user ID: {}.", user.getId());
        
        if (user.getTeamId() != null) {
            log.debug("New user {} is assigned to team {}. Triggering team members update notification.", user.getId(),
                    user.getTeamId());
            sendUserChangeNotification(user, "USER_PERSISTED_IN_TEAM");
        } else {
            log.debug("New user {} has no teamId. No team notification for user creation itself.", user.getId());
        }
    }

    @PostUpdate
    public void afterUserUpdate(User user) {
        log.debug("UserEntityListener: @PostUpdate triggered for user ID: {}", user.getId());
        
        
        
        
        sendUserChangeNotification(user, "USER_UPDATED");
    }

    @PostRemove
    public void afterUserRemove(User user) {
        log.debug("UserEntityListener: @PostRemove triggered for user ID: {}", user.getId());
        
        
        sendUserRemoveNotification(user, "USER_REMOVED");
    }
}