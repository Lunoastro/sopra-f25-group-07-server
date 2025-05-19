package ch.uzh.ifi.hase.soprafs24.listener;

import ch.uzh.ifi.hase.soprafs24.entity.User;
import ch.uzh.ifi.hase.soprafs24.rest.dto.user.UserGetDTO;
import ch.uzh.ifi.hase.soprafs24.service.TeamService;
import ch.uzh.ifi.hase.soprafs24.service.UserService; // Included as it's in the listener's constructor
import ch.uzh.ifi.hase.soprafs24.service.WebSocketNotificationService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class UserEntityListenerTest {

    @Mock
    private WebSocketNotificationService mockNotificationService;

    @Mock
    private TeamService mockTeamService;

    @Mock
    private UserService mockUserService; // Mocking UserService as it's a constructor dependency

    @InjectMocks
    private UserEntityListener userEntityListener;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testUser");
        testUser.setTeamId(10L); // Default to having a team ID

        // Ensure no synchronization is active from previous tests
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @AfterEach
    void tearDown() {
        // Clean up synchronization manager after each test
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // --- Unit Tests for performNotificationLogic ---

    @Test
    void performNotificationLogic_userWithTeam_notifiesCorrectly() {
        // Arrange
        List<UserGetDTO> mockMembers = Collections.singletonList(new UserGetDTO());
        when(mockTeamService.getCurrentMembersForTeam(10L)).thenReturn(mockMembers);

        // Act
        userEntityListener.performNotificationLogic(testUser, "TEST_ACTION_WITH_TEAM");

        // Assert
        ArgumentCaptor<List> payloadCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockNotificationService, times(1)).notifyTeamMembers(eq(10L), eq("MEMBERS"), payloadCaptor.capture());
        assertEquals(mockMembers, payloadCaptor.getValue());
    }

    @Test
    void performNotificationLogic_userWithoutTeam_doesNotNotifyTeam() {
        // Arrange
        testUser.setTeamId(null);

        // Act
        userEntityListener.performNotificationLogic(testUser, "TEST_ACTION_NO_TEAM");

        // Assert
        verify(mockNotificationService, never()).notifyTeamMembers(any(), any(), any());
    }

    @Test
    void performNotificationLogic_teamServiceReturnsNullMembers_sendsEmptyList() {
        // Arrange
        when(mockTeamService.getCurrentMembersForTeam(10L)).thenReturn(null);

        // Act
        userEntityListener.performNotificationLogic(testUser, "TEST_ACTION_NULL_MEMBERS");

        // Assert
        ArgumentCaptor<List> payloadCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockNotificationService, times(1)).notifyTeamMembers(eq(10L), eq("MEMBERS"), payloadCaptor.capture());
        assertTrue(payloadCaptor.getValue().isEmpty());
    }

    @Test
    void performNotificationLogic_teamServiceReturnsEmptyMembers_sendsEmptyList() {
        // Arrange
        when(mockTeamService.getCurrentMembersForTeam(10L)).thenReturn(Collections.emptyList());

        // Act
        userEntityListener.performNotificationLogic(testUser, "TEST_ACTION_EMPTY_MEMBERS");

        // Assert
        ArgumentCaptor<List> payloadCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockNotificationService, times(1)).notifyTeamMembers(eq(10L), eq("MEMBERS"), payloadCaptor.capture());
        assertTrue(payloadCaptor.getValue().isEmpty());
    }
    
    @Test
    void performNotificationLogic_nullUser_logsWarningAndReturns() {
        // Act
        userEntityListener.performNotificationLogic(null, "TEST_NULL_USER");

        // Assert
        verify(mockNotificationService, never()).notifyTeamMembers(any(), any(), any());
        verify(mockTeamService, never()).getCurrentMembersForTeam(any());
        // Add log verification if you have a logging test framework
    }


    // --- Unit Tests for public listener methods and transaction synchronization ---

    private void triggerAfterCommit() {
        assertTrue(TransactionSynchronizationManager.isSynchronizationActive(), "Synchronization should be active to trigger afterCommit.");
        assertFalse(TransactionSynchronizationManager.getSynchronizations().isEmpty(), "Synchronizations list should not be empty.");

        // Make a copy as the list might be modified during iteration by some frameworks
        List<TransactionSynchronization> synchronizations = new ArrayList<>(TransactionSynchronizationManager.getSynchronizations());
        for (TransactionSynchronization synchronization : synchronizations) {
            synchronization.afterCommit();
        }
    }
    
    // Helper method to simulate afterCompletion for all registered synchronizations
    private void triggerAfterCompletion(int status) {
        assertTrue(TransactionSynchronizationManager.isSynchronizationActive(), "Synchronization should be active to trigger afterCompletion.");
        List<TransactionSynchronization> synchronizations = new ArrayList<>(TransactionSynchronizationManager.getSynchronizations());
        for (TransactionSynchronization synchronization : synchronizations) {
            synchronization.afterCompletion(status);
        }
    }

    @Test
    void afterUserPersist_userWithTeamAndNoActiveTransaction_logsWarningDoesNotNotify() {
        // Arrange
        // No TransactionSynchronizationManager.initSynchronization();

        // Act
        userEntityListener.afterUserPersist(testUser);

        // Assert
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive()); // Should remain inactive
        verify(mockNotificationService, never()).notifyTeamMembers(any(), any(), any());
        // Verify log for warning (requires log testing setup or check console output if simple)
    }

    @Test
    void afterUserPersist_userWithoutTeam_doesNotRegisterOrNotify() {
        // Arrange
        testUser.setTeamId(null);
        TransactionSynchronizationManager.initSynchronization(); // Simulate active transaction

        // Act
        userEntityListener.afterUserPersist(testUser);

        // Assert
        // No synchronization should be registered if teamId is null and it's the only trigger for sendNotificationAfterCommit
        // Based on current UserEntityListener, if teamId is null, sendNotificationAfterCommit isn't called from afterUserPersist.
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
        verify(mockNotificationService, never()).notifyTeamMembers(any(), any(), any());
        verify(mockTeamService, never()).getCurrentMembersForTeam(any());
    }


    // --- Tests for null user object passed to public listener methods ---
    // These now test the guard clauses added to the public methods.

    @Test
    void afterUserPersist_nullUser_logsWarningAndSkips() {
        // Act
        userEntityListener.afterUserPersist(null);
        // Assert
        verify(mockNotificationService, never()).notifyTeamMembers(any(), any(), any());
        verify(mockTeamService, never()).getCurrentMembersForTeam(any());
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive() && !TransactionSynchronizationManager.getSynchronizations().isEmpty());
    }

    @Test
    void afterUserUpdate_nullUser_logsWarningAndSkips() {
        // Act
        userEntityListener.afterUserUpdate(null);
        // Assert
        verify(mockNotificationService, never()).notifyTeamMembers(any(), any(), any());
        verify(mockTeamService, never()).getCurrentMembersForTeam(any());
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive() && !TransactionSynchronizationManager.getSynchronizations().isEmpty());
    }

    @Test
    void afterUserRemove_nullUser_logsWarningAndSkips() {
        // Act
        userEntityListener.afterUserRemove(null);
        // Assert
        verify(mockNotificationService, never()).notifyTeamMembers(any(), any(), any());
        verify(mockTeamService, never()).getCurrentMembersForTeam(any());
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive() && !TransactionSynchronizationManager.getSynchronizations().isEmpty());
    }

        @Test
    void afterUserPersist_userWithTeam_InUnitTestEnvironment_DoesNotNotifyDirectlyAndDoesNotRegisterSync() {
        // Arrange
        // testUser already has teamId = 10L from setUp

        // This mock setup is for performNotificationLogic, which won't be called via afterCommit here
        List<UserGetDTO> mockMembers = Collections.singletonList(new UserGetDTO());
        // when(mockTeamService.getCurrentMembersForTeam(testUser.getTeamId())).thenReturn(mockMembers); // Not strictly needed if not called

        // Attempt to simulate active transaction - this likely won't make
        // isActualTransactionActive() true inside the Spring component from a pure unit test.
        TransactionSynchronizationManager.initSynchronization();

        // Act
        userEntityListener.afterUserPersist(testUser); // This calls sendNotificationAfterCommit

        // Assert:
        // In a unit test without full Spring transaction context, isActualTransactionActive()
        // inside the listener will likely be false.
        // So, the 'else' branch of sendNotificationAfterCommit is taken.
        verify(mockNotificationService, never()).notifyTeamMembers(any(), any(), any());
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty(), "No synchronization should have been registered if transaction was not seen as active by listener.");

        // Cleanup
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}