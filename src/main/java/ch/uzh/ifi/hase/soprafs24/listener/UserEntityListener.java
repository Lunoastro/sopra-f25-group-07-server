package ch.uzh.ifi.hase.soprafs24.listener;

import ch.uzh.ifi.hase.soprafs24.entity.User;
import ch.uzh.ifi.hase.soprafs24.rest.dto.user.UserGetDTO;
import ch.uzh.ifi.hase.soprafs24.service.TeamService;
import ch.uzh.ifi.hase.soprafs24.service.UserService;
import ch.uzh.ifi.hase.soprafs24.service.WebSocketNotificationService;
import ch.uzh.ifi.hase.soprafs24.websocket.SocketHandler;
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
    private final SocketHandler socketHandler;

    @Autowired
    public UserEntityListener(@Lazy WebSocketNotificationService notificationService,
            @Lazy TeamService teamService,
            @Lazy UserService userService,
            @Lazy SocketHandler socketHandler) {
        this.notificationService = notificationService;
        this.teamService = teamService;
        this.socketHandler = socketHandler;
    }

    private User cloneUserForSnapshot(User originalUser) {
        if (originalUser == null)
            return null;
        User snapshot = new User();
        snapshot.setId(originalUser.getId());
        snapshot.setTeamId(originalUser.getTeamId());

        return snapshot;
    }

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

    void sendNotificationAfterCommit(User user, String action) {
        if (user == null || user.getId() == null) {
            log.warn("User or User.id is null in sendNotificationAfterCommit. Action: {}. Skipping registration.",
                    action);
            return;
        }
        final Long userIdForLog = user.getId();

        final Long teamIdForLog = user.getTeamId();

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            final User userSnapshot = cloneUserForSnapshot(user);

            log.debug("User Listener: Registering synchronization for user {} (Potential New Team: {}), Action: {}",
                    userIdForLog, teamIdForLog, action);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    log.debug("User Listener (after commit): Executing post-commit logic for user {} (Action: {})",
                            userSnapshot.getId(), action);

                    if ("USER_REMOVED".equals(action)) {
                        log.info(
                                "User Listener (after commit): User {} (Action: {}) was removed. Attempting to close their WebSocket session.",
                                userSnapshot.getId(), action);
                        socketHandler.closeSessionForUser(userSnapshot.getId(), "User account deleted");
                    } else if (userSnapshot.getTeamId() == null) {

                        if ("USER_UPDATED".equals(action) || "USER_PERSISTED_NO_TEAM".equals(action)) {
                            log.info(
                                    "User Listener (after commit): User {} (Action: {}) now has no teamId or is new without a team. Attempting to move/confirm WebSocket session to pending.",
                                    userSnapshot.getId(), action);
                            socketHandler.moveSessionToPending(userSnapshot.getId());
                        } else if ("USER_PERSISTED_JOINED_TEAM".equals(action)) {

                            log.warn(
                                    "User Listener (after commit): Action was {} for user {} but teamId is unexpectedly null. Attempting to pend session.",
                                    action, userSnapshot.getId());
                            socketHandler.moveSessionToPending(userSnapshot.getId());
                        } else {
                            log.debug(
                                    "User Listener (after commit): User {} (Action: {}) has null teamId. No specific WebSocket session move-to-pending action triggered besides default handling for new users if applicable.",
                                    userSnapshot.getId(), action);
                        }
                    } else {

                        log.debug(
                                "User Listener (after commit): User {} is in team {}. Processing team notifications for action {}.",
                                userSnapshot.getId(), userSnapshot.getTeamId(), action);
                        performNotificationLogic(userSnapshot, action + "_AFTER_COMMIT_TEAM_NOTIFICATION");
                    }
                }

                @Override
                public void afterCompletion(int status) {
                    String userNameForLog = (userSnapshot != null && userSnapshot.getId() != null)
                            ? userSnapshot.getId().toString()
                            : "UNKNOWN_USER_ID_IN_SNAPSHOT";
                    if (userIdForLog != null && (userSnapshot == null || userSnapshot.getId() == null)) {
                        userNameForLog = userIdForLog.toString();
                    }

                    if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                        log.info(
                                "User Listener (after completion - ROLLED_BACK): Transaction for user {} (Action: {}) was rolled back. WebSocket/Notification changes were not applied.",
                                userNameForLog, action);
                    } else if (status == TransactionSynchronization.STATUS_COMMITTED) {
                        log.info(
                                "User Listener (after completion - COMMITTED): Transaction for user {} (Action: {}) committed. Post-commit logic should have run.",
                                userNameForLog, action);
                    } else {
                        log.info(
                                "User Listener (after completion - status {}): Transaction for user {} (Action: {}) completed.",
                                status, userNameForLog, action);
                    }
                }
            });
        } else {
            log.warn(
                    "User Listener: No active transaction for user action {}. WebSocket/Notification changes for user {} (team {}) WILL NOT BE SENT/APPLIED.",
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
            log.debug("New user {} has no teamId. Registering post-commit action for potential session pending.",
                    user.getId());
            sendNotificationAfterCommit(user, "USER_PERSISTED_NO_TEAM");
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