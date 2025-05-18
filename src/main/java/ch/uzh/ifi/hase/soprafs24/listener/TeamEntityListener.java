
package ch.uzh.ifi.hase.soprafs24.listener;

import ch.uzh.ifi.hase.soprafs24.entity.Team;
import ch.uzh.ifi.hase.soprafs24.rest.dto.team.TeamGetDTO;
import ch.uzh.ifi.hase.soprafs24.rest.dto.user.UserGetDTO;
import ch.uzh.ifi.hase.soprafs24.rest.mapper.DTOMapper;
import ch.uzh.ifi.hase.soprafs24.service.TeamService;
import ch.uzh.ifi.hase.soprafs24.service.WebSocketNotificationService;
import ch.uzh.ifi.hase.soprafs24.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import javax.persistence.PostPersist;
import javax.persistence.PostRemove;
import javax.persistence.PostUpdate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class TeamEntityListener {

    private final Logger log = LoggerFactory.getLogger(TeamEntityListener.class);

    private final WebSocketNotificationService notificationService;
    private final TeamService teamService;
    private final UserService userService;

    @Autowired
    public TeamEntityListener(@Lazy WebSocketNotificationService notificationService,
            @Lazy TeamService teamService,
            @Lazy UserService userService) {
        this.notificationService = notificationService;
        this.teamService = teamService;
        this.userService = userService;
    }

    /*
     * 
     * List<UserGetDTO> currentMembers = getCurrentMembersForTeamDTO(team.getId());
     * notificationService.notifyTeamMembers(team.getId(), "Team", currentMembers);
     */
    public List<UserGetDTO> getCurrentMembersForTeam(Long teamId) {
        if (teamId == null) {
            log.warn("getCurrentMembersForTeam called with null teamId.");
            return Collections.emptyList();
        }
        Team team;
        try {
            team = teamService.getTeamById(teamId);
        } catch (ResponseStatusException e) {
            log.warn("Team not found with id {} while trying to get current members DTO.", teamId);
            return Collections.emptyList();
        }

        List<Long> memberIds = team.getMembers();

        if (memberIds == null || memberIds.isEmpty()) {
            return Collections.emptyList();
        }

        return memberIds.stream()
                .map(memberId -> {
                    try {
                        return userService.getUserById(memberId);
                    } catch (ResponseStatusException e) {
                        log.warn("User with ID {} not found while fetching members for team {}. Skipping.", memberId,
                                teamId);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .map(user -> DTOMapper.INSTANCE.convertEntityToUserGetDTO(user))
                .collect(Collectors.toList());
    }

    private void sendCreateUpdateNotification(Team team, String action) {
        if (team == null || team.getId() == null) {
            log.warn("Team or Team.id is null. Cannot send {} notification.", action);
            return;
        }

        final Long teamId = team.getId();

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {

                        Team freshTeam = teamService.getTeamById(teamId);
                        TeamGetDTO teamGetDTO = DTOMapper.INSTANCE.convertEntityToTeamGetDTO(freshTeam);
                        log.debug(
                                "TeamEntityListener (after commit): Notifying members of team {} about {} (Action: {})",
                                teamId, teamId, action);
                        notificationService.notifyTeamMembers(teamId, "team", teamGetDTO);
                    } catch (Exception e) {
                        log.error("Error sending {} notification from TeamEntityListener after commit for team {}: {}",
                                action, teamId, e.getMessage(), e);
                    }
                }
            });
        } else {
            log.warn(
                    "TeamEntityListener: No active transaction for action {}. Notification for team {} might not reflect committed state.",
                    action, teamId);

        }
    }

    private void sendRemoveNotification(Team team, String action) {
        if (team == null || team.getId() == null) {
            log.warn("Team or Team.id is null. Cannot send {} notification.", action);
            return;
        }
        final Long teamId = team.getId();

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        log.debug("TeamEntityListener (after commit): Notifying about deleted team {} (Action: {})",
                                teamId, action);
                        Map<String, Object> deletePayload = new HashMap<>();
                        deletePayload.put("id", teamId);
                        deletePayload.put("status", "DELETED");
                        notificationService.notifyTeamMembers(teamId, "Team", deletePayload);
                    } catch (Exception e) {
                        log.error("Error sending {} notification from TeamEntityListener after commit for team {}: {}",
                                action, teamId, e.getMessage(), e);
                    }
                }
            });
        } else {
            log.warn(
                    "TeamEntityListener: No active transaction for action {}. Notification for team {} might not reflect committed state.",
                    action, teamId);
        }
    }

    @PostPersist
    public void afterTeamPersist(Team team) {
        log.debug("TeamEntityListener: @PostPersist triggered for team ID: {}", team.getId());
        sendCreateUpdateNotification(team, "PERSIST");
    }

    @PostUpdate
    public void afterTeamUpdate(Team team) {

        log.debug("TeamEntityListener: @PostUpdate triggered for team ID: {}", team.getId());
        sendCreateUpdateNotification(team, "UPDATE");
    }

    @PostRemove
    public void afterTeamRemove(Team team) {
        log.debug("TeamEntityListener: @PostRemove triggered for team ID: {}", team.getId());
        sendRemoveNotification(team, "REMOVE");
    }
}