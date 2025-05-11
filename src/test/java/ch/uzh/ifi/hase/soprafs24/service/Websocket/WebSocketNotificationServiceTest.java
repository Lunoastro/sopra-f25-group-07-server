package ch.uzh.ifi.hase.soprafs24.service.Websocket;

import ch.uzh.ifi.hase.soprafs24.rest.dto.websocket.DatabaseChangeEventDTO;
import ch.uzh.ifi.hase.soprafs24.service.WebSocketNotificationService;
import ch.uzh.ifi.hase.soprafs24.websocket.SocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebSocketNotificationServiceTest {

    @Mock
    private SocketHandler mockSocketHandler;

    @InjectMocks
    private WebSocketNotificationService webSocketNotificationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void broadcastEntityChange_validData_callsSocketHandlerBroadcastMessageToAll() {
        // Given
        String entityType = "task";
        Map<String, Object> entityData = new HashMap<>();
        entityData.put("id", 1L);
        entityData.put("name", "Test Task");

        // When
        webSocketNotificationService.broadcastEntityChange(entityType, entityData);

        // Then
        ArgumentCaptor<DatabaseChangeEventDTO> eventCaptor = ArgumentCaptor.forClass(DatabaseChangeEventDTO.class);
        verify(mockSocketHandler, times(1)).broadcastMessageToAll(eventCaptor.capture());

        DatabaseChangeEventDTO<?> capturedEvent = eventCaptor.getValue();
        assertEquals(entityType, capturedEvent.getEntityType());
        assertEquals(entityData, capturedEvent.getPayload());
    }

    @Test
    void broadcastEntityChange_nullEntityData_doesNotCallSocketHandlerAndLogsWarning() {
        // Given
        String entityType = "user";

        // When
        webSocketNotificationService.broadcastEntityChange(entityType, null);

        // Then
        verify(mockSocketHandler, never()).broadcastMessageToAll(any());
    }

    @Test
    void notifyTeamMembers_validDataAndTeamId_callsSocketHandlerBroadcastMessageToTeam() {
        // Given
        Long teamId = 123L;
        String entityType = "taskUpdate";
        Map<String, Object> entityData = new HashMap<>();
        entityData.put("id", 2L);
        entityData.put("status", "completed");

        // When
        webSocketNotificationService.notifyTeamMembers(teamId, entityType, entityData);

        // Then
        ArgumentCaptor<DatabaseChangeEventDTO> eventCaptor = ArgumentCaptor.forClass(DatabaseChangeEventDTO.class);
        verify(mockSocketHandler, times(1)).broadcastMessageToTeam(eq(teamId), eventCaptor.capture());

        DatabaseChangeEventDTO<?> capturedEvent = eventCaptor.getValue();
        assertEquals(entityType, capturedEvent.getEntityType());
        assertEquals(entityData, capturedEvent.getPayload());
    }

    @Test
    void notifyTeamMembers_nullTeamId_doesNotCallSocketHandlerAndLogsWarning() {
        // Given
        String entityType = "teamEvent";
        Map<String, Object> entityData = new HashMap<>();
        entityData.put("id", "eventX");

        // When
        webSocketNotificationService.notifyTeamMembers(null, entityType, entityData);

        // Then
        verify(mockSocketHandler, never()).broadcastMessageToTeam(anyLong(), any());
        
    }

    @Test
    void notifyTeamMembers_nullEntityData_doesNotCallSocketHandlerAndLogsWarning() {
        // Given
        Long teamId = 456L;
        String entityType = "userJoin";

        // When
        webSocketNotificationService.notifyTeamMembers(teamId, entityType, null);

        // Then
        verify(mockSocketHandler, never()).broadcastMessageToTeam(anyLong(), any());
    }

    @Test
    void handleCommonChecks_entityDataIsMapWithId_returnsTrue() {
        // This tests the private method indirectly through the public methods.
        // If broadcastEntityChange proceeds, handleCommonChecks returned true.
        String entityType = "generic";
        Map<String, Object> entityData = Map.of("id", "someId");

        webSocketNotificationService.broadcastEntityChange(entityType, entityData);

        verify(mockSocketHandler, times(1)).broadcastMessageToAll(any(DatabaseChangeEventDTO.class));
    }

     @Test
    void handleCommonChecks_entityDataIsSimpleObject_returnsTrue() {
        // Given
        String entityType = "simpleObject";
        Object entityData = new Object(); // Any non-null object that isn't a Map with "id"

        // When
        webSocketNotificationService.broadcastEntityChange(entityType, entityData);

        // Then
        ArgumentCaptor<DatabaseChangeEventDTO> eventCaptor = ArgumentCaptor.forClass(DatabaseChangeEventDTO.class);
        verify(mockSocketHandler, times(1)).broadcastMessageToAll(eventCaptor.capture());
        DatabaseChangeEventDTO<?> capturedEvent = eventCaptor.getValue();
        assertEquals(entityType, capturedEvent.getEntityType());
        assertEquals(entityData, capturedEvent.getPayload());
    }
}