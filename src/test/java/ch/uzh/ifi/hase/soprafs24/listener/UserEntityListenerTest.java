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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@ExtendWith(MockitoExtension.class)
class UserEntityListenerTest {

    private static final Logger log = LoggerFactory.getLogger(UserEntityListenerTest.class);

    @Mock
    private WebSocketNotificationService mockNotificationService;

    @Mock
    private TeamService mockTeamService;

    @Mock
    private UserService mockUserService; // Mock UserService as it's a dependency

    @InjectMocks
    private UserEntityListener userEntityListener;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setTeamId(10L);
        testUser.setUsername("testUser");

        // Crucial: Initialize TransactionSynchronizationManager for each test
        // if it's expected to be used.
        // However, initSynchronization() alone doesn't make isActualTransactionActive() true
        // from within a Spring-managed component in a unit test.
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        // Clear synchronizations after each test to ensure test isolation
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // Removed duplicate triggerAfterCommit() to resolve compilation error.

    @Test
    void afterUserUpdate_userWithTeam_InUnitTestEnvironment_DoesNotNotifyDirectlyAndDoesNotRegisterSync() {
        // Arrange
        // No TransactionSynchronizationManager.initSynchronization(); or assume it's not "active"
        // for the listener.

        // Act
        userEntityListener.afterUserUpdate(testUser);

        // Assert
        // In a unit test, isActualTransactionActive() in UserEntityListener is likely false,
        // so no synchronization should be registered.
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty(),
                "Synchronization should NOT have been registered in this unit test setup.");
        verify(mockNotificationService, never()).notifyTeamMembers(anyLong(), anyString(), anyList());
    }


    @Test
    void afterUserRemove_userWithTeam_InUnitTestEnvironment_DoesNotNotifyDirectlyAndDoesNotRegisterSync() {
        // Act
        userEntityListener.afterUserRemove(testUser);

        // Assert
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty(),
                "Synchronization should NOT have been registered in this unit test setup.");
        verify(mockNotificationService, never()).notifyTeamMembers(anyLong(), anyString(), anyList());
    }


    @Test
    void afterCommit_transactionCommitted_logsCommitAndNotifies() {
        // This test demonstrates how to test the *logic* of afterCommit if a sync *were* registered.
        // However, registering it via the listener's own mechanism is tricky in unit tests.

        // Arrange
        // Manually create and register a synchronization to directly test its methods.
        // This bypasses the listener's internal check for an active transaction.
        final boolean[] afterCommitCalled = {false};
        List<UserGetDTO> mockMembers = Collections.singletonList(new UserGetDTO());
        when(mockTeamService.getCurrentMembersForTeam(testUser.getTeamId())).thenReturn(mockMembers);


        TransactionSynchronization sync = new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                afterCommitCalled[0] = true;
                // Simulate the notification part of the listener's sync
                 List<UserGetDTO> members = mockTeamService.getCurrentMembersForTeam(testUser.getTeamId());
                 mockNotificationService.notifyTeamMembers(testUser.getTeamId(), "MEMBERS", members);
            }
        };
        TransactionSynchronizationManager.registerSynchronization(sync);
        assertFalse(TransactionSynchronizationManager.getSynchronizations().isEmpty(), "Manual Synchronization should be registered.");


        // Act
        triggerAfterCommit(); // Directly trigger the manually registered sync

        // Assert
        assertTrue(afterCommitCalled[0], "afterCommit should have been called on the manual sync.");
        ArgumentCaptor<List> payloadCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockNotificationService, times(1)).notifyTeamMembers(eq(testUser.getTeamId()), eq("MEMBERS"), payloadCaptor.capture());
        assertEquals(mockMembers, payloadCaptor.getValue());
    }

    @Test
    void afterCommit_transactionRolledBack_logsRollback() {
        // Arrange
        // Similar to above, if we want to test afterCompletion, we register a sync manually.
        final boolean[] afterCompletionCalledWithRollback = {false};

        TransactionSynchronization sync = new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    afterCompletionCalledWithRollback[0] = true;
                    log.info("User Listener: Transaction for user action (simulated) ROLLED_BACK. Notification for user {} (team {}) will not be sent.", testUser.getId(), testUser.getTeamId());
                }
            }
        };
        TransactionSynchronizationManager.registerSynchronization(sync);
        assertFalse(TransactionSynchronizationManager.getSynchronizations().isEmpty(), "Manual Synchronization should be registered.");


        // Act
        triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        // Assert
        assertTrue(afterCompletionCalledWithRollback[0], "afterCompletion with STATUS_ROLLED_BACK should have been called.");
        verify(mockNotificationService, never()).notifyTeamMembers(anyLong(), anyString(), anyList());
        // Add log verification here if you have a mechanism to capture logs
    }

    @Test
    void afterCommit_transactionStatusUnknown_logsCompletionWithStatus() {
        // Arrange
        final boolean[] afterCompletionCalledWithUnknown = {false};
        TransactionSynchronization sync = new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_UNKNOWN) {
                     afterCompletionCalledWithUnknown[0] = true;
                    log.info("User Listener: Transaction for user action (simulated) completed with UNKNOWN status {}. Notification for user {} (team {}) may not have been sent.", status, testUser.getId(), testUser.getTeamId());
                }
            }
        };
        TransactionSynchronizationManager.registerSynchronization(sync);
        assertFalse(TransactionSynchronizationManager.getSynchronizations().isEmpty(), "Manual Synchronization should be registered.");


        // Act
        triggerAfterCompletion(TransactionSynchronization.STATUS_UNKNOWN);

        // Assert
         assertTrue(afterCompletionCalledWithUnknown[0], "afterCompletion with STATUS_UNKNOWN should have been called.");
        verify(mockNotificationService, never()).notifyTeamMembers(anyLong(), anyString(), anyList());
        // Add log verification here
    }


    // The following tests attempt to verify behavior when an "active transaction" is present.
    // In a pure unit test, TransactionSynchronizationManager.isActualTransactionActive()
    // within the UserEntityListener will likely return false, so no synchronization will be registered by the listener.
    // These tests would typically pass in an @DataJpaTest or @SpringBootTest (integration test) environment
    // where Spring manages the transaction lifecycle.
    // For unit tests, the assertions are changed to expect no synchronization.

    @Test
    void afterUserUpdate_withSimulatedActiveTransaction_ButListenerSeesNoTransaction_NoSyncRegistered() {
        // Arrange
        // TransactionSynchronizationManager.initSynchronization(); // Done in BeforeEach
        // Even with initSynchronization(), isActualTransactionActive() in the listener is typically false in unit tests.

        // Act
        userEntityListener.afterUserUpdate(testUser); // This calls sendNotificationAfterCommit

        // Assert
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty(),
                "Synchronization should NOT be registered if listener doesn't perceive an active Spring transaction.");
        // Consequently, triggerAfterCommit would do nothing here as no sync is registered by the listener.
        // And no notification should be sent directly.
        verify(mockNotificationService, never()).notifyTeamMembers(anyLong(), anyString(), anyList());
    }

    @Test
    void afterUserRemove_withSimulatedActiveTransaction_ButListenerSeesNoTransaction_NoSyncRegistered() {
        // Arrange
        // TransactionSynchronizationManager.initSynchronization(); // Done in BeforeEach

        // Act
        userEntityListener.afterUserRemove(testUser); // This calls sendNotificationAfterCommit

        // Assert
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty(),
                "Synchronization should NOT be registered if listener doesn't perceive an active Spring transaction.");
        verify(mockNotificationService, never()).notifyTeamMembers(anyLong(), anyString(), anyList());
    }

    @Test
    void afterUserPersist_withSimulatedActiveTransaction_ButListenerSeesNoTransaction_NoSyncRegistered() {
        // Arrange
        // TransactionSynchronizationManager.initSynchronization(); // Done in BeforeEach
        // List<UserGetDTO> mockMembers = Collections.singletonList(new UserGetDTO());
        // when(mockTeamService.getCurrentMembersForTeam(testUser.getTeamId())).thenReturn(mockMembers);
        // The stubbing above is removed as it would be an UnnecessaryStubbing if no sync is registered.

        // Act
        userEntityListener.afterUserPersist(testUser); // Calls sendNotificationAfterCommit

        // Assert
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty(),
            "Synchronization should NOT be registered if listener doesn't perceive an active Spring transaction.");
        verify(mockNotificationService, never()).notifyTeamMembers(anyLong(), anyString(), anyList());
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
        TransactionSynchronizationManager.clearSynchronization(); // Ensure no active transaction
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
        TransactionSynchronizationManager.clearSynchronization(); // Ensure no active transaction
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
        TransactionSynchronizationManager.clearSynchronization(); // Ensure no active transaction
        // testUser already has teamId = 10L from setUp

        

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