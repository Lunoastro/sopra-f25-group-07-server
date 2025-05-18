
package ch.uzh.ifi.hase.soprafs24.listener;

import ch.uzh.ifi.hase.soprafs24.entity.User;

import ch.uzh.ifi.hase.soprafs24.rest.dto.user.UserGetDTO;
import ch.uzh.ifi.hase.soprafs24.rest.mapper.DTOMapper;
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
import java.util.HashMap;
import java.util.Map;

@Component
public class UserEntityListener {

    private final Logger log = LoggerFactory.getLogger(UserEntityListener.class);

    private final WebSocketNotificationService notificationService;
    private final UserService userService;

    @Autowired
    public UserEntityListener(@Lazy WebSocketNotificationService notificationService,
            @Lazy UserService userService) {
        this.notificationService = notificationService;
        this.userService = userService;
    }

    private void sendUserUpdateNotification(User user, String action) {
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

                        User freshUser = userService.getUserById(userId);
                        if (freshUser == null) {
                            log.warn("User with ID {} not found after commit for action {}. Skipping notification.",
                                    userId, action);
                            return;
                        }

                        if (freshUser.getTeamId() == null || !freshUser.getTeamId().equals(teamId)) {
                            log.warn(
                                    "User {} teamId changed during transaction or is null after commit (was {}). Notifying original teamId {}.",
                                    userId, freshUser.getTeamId(), teamId);

                            if (freshUser.getTeamId() == null) {

                                log.debug(
                                        "User {} is no longer in team {} after commit. No 'user_updated' sent to that team.",
                                        userId, teamId);
                                return;
                            }

                            UserGetDTO userGetDTO = DTOMapper.INSTANCE.convertEntityToUserGetDTO(freshUser);
                            log.debug(
                                    "UserEntityListener (after commit): Notifying team {} about user {} update (Action: {})",
                                    freshUser.getTeamId(), userId, action);
                            notificationService.notifyTeamMembers(freshUser.getTeamId(), "user_updated", userGetDTO);
                            return;
                        }

                        UserGetDTO userGetDTO = DTOMapper.INSTANCE.convertEntityToUserGetDTO(freshUser);
                        log.debug(
                                "UserEntityListener (after commit): Notifying team {} about user {} update (Action: {})",
                                teamId, userId, action);
                        notificationService.notifyTeamMembers(teamId, "user_updated", userGetDTO);
                    } catch (Exception e) {
                        log.error("Error sending {} notification from UserEntityListener after commit for user {}: {}",
                                action, userId, e.getMessage(), e);
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
            log.debug("Deleted user {} (Action: {}) had no assigned teamId. No team notification sent.", userId,
                    action);
            return;
        }

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        Map<String, Object> deletePayload = new HashMap<>();
                        deletePayload.put("id", userId);

                        log.debug(
                                "UserEntityListener (after commit): Notifying team {} about deleted user {} (Action: {})",
                                teamId, userId, action);
                        notificationService.notifyTeamMembers(teamId, "user_deleted", deletePayload);
                    } catch (Exception e) {
                        log.error("Error sending {} notification from UserEntityListener after commit for user {}: {}",
                                action, userId, e.getMessage(), e);
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
            log.debug("New user {} is assigned to team {}. Triggering update-like notification.", user.getId(),
                    user.getTeamId());
            sendUserUpdateNotification(user, "PERSIST_WITH_TEAM");
        } else {
            log.debug("New user {} has no teamId. No team notification for user creation itself.", user.getId());
        }
    }

    @PostUpdate
    public void afterUserUpdate(User user) {
        log.debug("UserEntityListener: @PostUpdate triggered for user ID: {}", user.getId());

        sendUserUpdateNotification(user, "UPDATE");
    }

    @PostRemove
    public void afterUserRemove(User user) {
        log.debug("UserEntityListener: @PostRemove triggered for user ID: {}", user.getId());
        sendUserRemoveNotification(user, "REMOVE");
    }
}