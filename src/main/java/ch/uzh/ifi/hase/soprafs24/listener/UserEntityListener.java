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
    private static final String ENTITY_TYPE = "MEMBERS";
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

    /*
     * Contains the core logic for fetching team members and sending the
     * notification.
     * This method is intended to be called after a transaction commits.
     */

    void performNotificationLogic(User user, String actionDetails) {
        if (user == null || user.getId() == null) {
            log.warn("User or User.id is null. Cannot perform notification logic for action '{}'.", actionDetails);
            return;
        }

        final Long userId = user.getId();
        final Long teamId = user.getTeamId();

        if (teamId == null) {
            log.debug("User {} (Action: '{}') has no assigned teamId. No team-specific notification will be sent.",
                    userId, actionDetails);

            return;
        }

        try {
            List<UserGetDTO> teamMembersPayload = teamService.getCurrentMembersForTeam(teamId);
            if (teamMembersPayload == null) {
                log.warn(
                        "Failed to get current members for team {} after user {} {}. Using empty list for notification payload.",
                        teamId, userId, actionDetails);
                teamMembersPayload = Collections.emptyList();
            }

            log.debug(
                    "User Listener: Notifying team {} due to user {} (Action: '{}'). Payload contains {} members.",
                    teamId, userId, actionDetails, teamMembersPayload.size());
            notificationService.notifyTeamMembers(teamId, ENTITY_TYPE, teamMembersPayload);

        } catch (Exception e) {

            log.error("Error preparing or sending notification for user {} (team {}) during action '{}': {}",
                    userId, teamId, actionDetails, e.getMessage(), e);
        }
    }

    /**
     * Registers the notification logic to be executed after the current transaction
     * commits.
     * If no transaction is active, logs a warning and does not send a notification.
     */
    private void sendNotificationAfterCommit(User user, String action) {

        if (user == null || user.getId() == null) {
            log.warn("User or User.id is null in sendNotificationAfterCommit. Action: {}. Skipping registration.",
                    action);
            return;
        }
        final Long userIdForLog = user.getId();
        final Long teamIdForLog = user.getTeamId();

        if (TransactionSynchronizationManager.isActualTransactionActive()) {

            final User userSnapshot = user;

            log.debug("User Listener: Registering synchronization for user {} (Team: {}), Action: {}",
                    userIdForLog, teamIdForLog, action);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    log.debug("User Listener (after commit): Executing notification for user {} (Action: {})",
                            userSnapshot.getId(), action);
                    performNotificationLogic(userSnapshot, action + "_AFTER_COMMIT");
                }

                @Override
                public void afterCompletion(int status) {
                    if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                        log.info(
                                "User Listener (after completion - ROLLED_BACK): Transaction for user {} (Action: {}) was rolled back. Notification was not sent.",
                                userIdForLog, action);
                    } else if (status == TransactionSynchronization.STATUS_COMMITTED) {
                        log.info(
                                "User Listener (after completion - COMMITTED): Transaction for user {} (Action: {}) committed. Notification should have been processed.",
                                userIdForLog, action);
                    } else {
                        log.info(
                                "User Listener (after completion - status {}): Transaction for user {} (Action: {}) completed.",
                                status, userIdForLog, action);
                    }
                }
            });
        } else {

            log.warn(
                    "User Listener: No active transaction for user action {}. Notification for user {} (team {}) WILL NOT BE SENT to ensure it's only after successful commit.",
                    action, userIdForLog, teamIdForLog);
        }
    }

    @PostPersist
    public void afterUserPersist(User user) {
        if (user == null || user.getId() == null) {
            log.warn("User Listener: @PostPersist triggered with null user or user ID. Skipping.");
            return;
        }
        log.debug("User Listener: @PostPersist triggered for user ID: {}.", user.getId());

        if (user.getTeamId() != null) {
            log.debug("New user {} is assigned to team {}. Registering notification for after commit.", user.getId(),
                    user.getTeamId());
            sendNotificationAfterCommit(user, "USER_PERSISTED_JOINED_TEAM");
        } else {
            log.debug("New user {} has no teamId. No team-specific notification to register for user creation.",
                    user.getId());

        }
    }

    @PostUpdate
    public void afterUserUpdate(User user) {
        if (user == null || user.getId() == null) {
            log.warn("User Listener: @PostUpdate triggered with null user or user ID. Skipping.");
            return;
        }
        log.debug("User Listener: @PostUpdate triggered for user ID: {}", user.getId());

        sendNotificationAfterCommit(user, "USER_UPDATED");
    }

    @PostRemove
    public void afterUserRemove(User user) {
        if (user == null || user.getId() == null) {
            log.warn("User Listener: @PostRemove triggered with null user or user ID. Skipping.");
            return;
        }
        log.debug("User Listener: @PostRemove triggered for user ID: {}", user.getId());

        sendNotificationAfterCommit(user, "USER_REMOVED");
    }
}