package ch.uzh.ifi.hase.soprafs24.service;

import ch.uzh.ifi.hase.soprafs24.websocket.SocketHandler;
import ch.uzh.ifi.hase.soprafs24.rest.dto.websocket.DatabaseChangeEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class WebSocketNotificationService {

    private static final Logger log = LoggerFactory.getLogger(WebSocketNotificationService.class);
    private final SocketHandler plainWebSocketHandler;

    @Autowired
    public WebSocketNotificationService(SocketHandler plainWebSocketHandler) {
        this.plainWebSocketHandler = plainWebSocketHandler;
    }

    /**
     * Broadcasts a generic entity change to ALL connected clients.
     * Used for entities where targeting by teamId is not applicable (e.g., User updates).
     */
    public void broadcastEntityChange(String entityType, Object entityData) {
        if (handleCommonChecks(entityType, entityData)) {
            DatabaseChangeEventDTO<?> event = new DatabaseChangeEventDTO<>(entityType, entityData);
            plainWebSocketHandler.broadcastMessageToAll(event); // New method in handler
        }
    }

    /**
     * Notifies only users belonging to a specific team about an entity change.
     * Primarily used for Task updates.
     */
    public void notifyTeamMembers(Long teamId, String entityType, Object entityData) {
        if (teamId == null) {
            log.warn("Cannot notify team members: teamId is null. EntityType: {}", entityType);
            // Fallback to broadcast or log an error depending on desired behavior
            // For now, just return to avoid NullPointerException
            return;
        }
        if (handleCommonChecks(entityType, entityData)) {
            DatabaseChangeEventDTO<?> event = new DatabaseChangeEventDTO<>(entityType, entityData);
            plainWebSocketHandler.broadcastMessageToTeam(teamId, event); // New method in handler
        }
    }

    private boolean handleCommonChecks(String entityType, Object entityData) {
        if (entityData instanceof Map && ((Map<?, ?>) entityData).containsKey("id")) {
            // This is fine, entityData is Map.of("id", entityId)
        } else if (entityData == null) {
            log.warn("Attempted to send WebSocket notification for {} with null entityData. Skipping.", entityType);
            return false;
        }
        // You might add more checks here if needed
        return true;
    }
}