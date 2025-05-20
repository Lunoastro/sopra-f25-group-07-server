package ch.uzh.ifi.hase.soprafs24.listener;

import ch.uzh.ifi.hase.soprafs24.entity.User;
import ch.uzh.ifi.hase.soprafs24.rest.dto.user.UserGetDTO;
import ch.uzh.ifi.hase.soprafs24.service.TeamService;
import ch.uzh.ifi.hase.soprafs24.service.UserService;
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
    private UserService mockUserService;

    @InjectMocks
    private UserEntityListener userEntityListener;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setTeamId(10L);
        testUser.setUsername("testUser");

        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void afterUserUpdate_userWithTeam_InUnitTestEnvironment_DoesNotNotifyDirectlyAndDoesNotRegisterSync() {

        userEntityListener.afterUserUpdate(testUser);

        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty(),
                "Synchronization should NOT have been registered in this unit test setup.");
        verify(mockNotificationService, never()).notifyTeamMembers(anyLong(), anyString(), anyList());
    }

    @Test
    void afterUserRemove_userWithTeam_InUnitTestEnvironment_DoesNotNotifyDirectlyAndDoesNotRegisterSync() {

        userEntityListener.afterUserRemove(testUser);

        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty(),
                "Synchronization should NOT have been registered in this unit test setup.");
        verify(mockNotificationService, never()).notifyTeamMembers(anyLong(), anyString(), anyList());
    }

    @Test
    void afterCommit_transactionCommitted_logsCommitAndNotifies() {

        final boolean[] afterCommitCalled = { false };
        List<UserGetDTO> mockMembers = Collections.singletonList(new UserGetDTO());
        when(mockTeamService.getCurrentMembersForTeam(testUser.getTeamId())).thenReturn(mockMembers);

        TransactionSynchronization sync = new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                afterCommitCalled[0] = true;

                List<UserGetDTO> members = mockTeamService.getCurrentMembersForTeam(testUser.getTeamId());
                mockNotificationService.notifyTeamMembers(testUser.getTeamId(), "MEMBERS", members);
            }
        };
        TransactionSynchronizationManager.registerSynchronization(sync);
        assertFalse(TransactionSynchronizationManager.getSynchronizations().isEmpty(),
                "Manual Synchronization should be registered.");

        triggerAfterCommit();

        assertTrue(afterCommitCalled[0], "afterCommit should have been called on the manual sync.");
        ArgumentCaptor<List> payloadCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockNotificationService, times(1)).notifyTeamMembers(eq(testUser.getTeamId()), eq("MEMBERS"),
                payloadCaptor.capture());
        assertEquals(mockMembers, payloadCaptor.getValue());
    }

    @Test
    void afterCommit_transactionRolledBack_logsRollback() {

        final boolean[] afterCompletionCalledWithRollback = { false };

        TransactionSynchronization sync = new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    afterCompletionCalledWithRollback[0] = true;
                    log.info(
                            "User Listener: Transaction for user action (simulated) ROLLED_BACK. Notification for user {} (team {}) will not be sent.",
                            testUser.getId(), testUser.getTeamId());
                }
            }
        };
        TransactionSynchronizationManager.registerSynchronization(sync);
        assertFalse(TransactionSynchronizationManager.getSynchronizations().isEmpty(),
                "Manual Synchronization should be registered.");

        triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertTrue(afterCompletionCalledWithRollback[0],
                "afterCompletion with STATUS_ROLLED_BACK should have been called.");
        verify(mockNotificationService, never()).notifyTeamMembers(anyLong(), anyString(), anyList());

    }

    @Test
    void afterCommit_transactionStatusUnknown_logsCompletionWithStatus() {

        final boolean[] afterCompletionCalledWithUnknown = { false };
        TransactionSynchronization sync = new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_UNKNOWN) {
                    afterCompletionCalledWithUnknown[0] = true;
                    log.info(
                            "User Listener: Transaction for user action (simulated) completed with UNKNOWN status {}. Notification for user {} (team {}) may not have been sent.",
                            status, testUser.getId(), testUser.getTeamId());
                }
            }
        };
        TransactionSynchronizationManager.registerSynchronization(sync);
        assertFalse(TransactionSynchronizationManager.getSynchronizations().isEmpty(),
                "Manual Synchronization should be registered.");

        triggerAfterCompletion(TransactionSynchronization.STATUS_UNKNOWN);

        assertTrue(afterCompletionCalledWithUnknown[0], "afterCompletion with STATUS_UNKNOWN should have been called.");
        verify(mockNotificationService, never()).notifyTeamMembers(anyLong(), anyString(), anyList());

    }

    @Test
    void afterUserUpdate_withSimulatedActiveTransaction_ButListenerSeesNoTransaction_NoSyncRegistered() {

        userEntityListener.afterUserUpdate(testUser);

        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty(),
                "Synchronization should NOT be registered if listener doesn't perceive an active Spring transaction.");

        verify(mockNotificationService, never()).notifyTeamMembers(anyLong(), anyString(), anyList());
    }

    @Test
    void afterUserRemove_withSimulatedActiveTransaction_ButListenerSeesNoTransaction_NoSyncRegistered() {

        userEntityListener.afterUserRemove(testUser);

        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty(),
                "Synchronization should NOT be registered if listener doesn't perceive an active Spring transaction.");
        verify(mockNotificationService, never()).notifyTeamMembers(anyLong(), anyString(), anyList());
    }

    @Test
    void afterUserPersist_withSimulatedActiveTransaction_ButListenerSeesNoTransaction_NoSyncRegistered() {

        userEntityListener.afterUserPersist(testUser);

        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty(),
                "Synchronization should NOT be registered if listener doesn't perceive an active Spring transaction.");
        verify(mockNotificationService, never()).notifyTeamMembers(anyLong(), anyString(), anyList());
    }

    @Test
    void performNotificationLogic_userWithTeam_notifiesCorrectly() {

        List<UserGetDTO> mockMembers = Collections.singletonList(new UserGetDTO());
        when(mockTeamService.getCurrentMembersForTeam(10L)).thenReturn(mockMembers);

        userEntityListener.performNotificationLogic(testUser, "TEST_ACTION_WITH_TEAM");

        ArgumentCaptor<List> payloadCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockNotificationService, times(1)).notifyTeamMembers(eq(10L), eq("MEMBERS"), payloadCaptor.capture());
        assertEquals(mockMembers, payloadCaptor.getValue());
    }

    @Test
    void performNotificationLogic_userWithoutTeam_doesNotNotifyTeam() {

        testUser.setTeamId(null);

        userEntityListener.performNotificationLogic(testUser, "TEST_ACTION_NO_TEAM");

        verify(mockNotificationService, never()).notifyTeamMembers(any(), any(), any());
    }

    @Test
    void performNotificationLogic_teamServiceReturnsNullMembers_sendsEmptyList() {

        when(mockTeamService.getCurrentMembersForTeam(10L)).thenReturn(null);

        userEntityListener.performNotificationLogic(testUser, "TEST_ACTION_NULL_MEMBERS");

        ArgumentCaptor<List> payloadCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockNotificationService, times(1)).notifyTeamMembers(eq(10L), eq("MEMBERS"), payloadCaptor.capture());
        assertTrue(payloadCaptor.getValue().isEmpty());
    }

    @Test
    void performNotificationLogic_teamServiceReturnsEmptyMembers_sendsEmptyList() {

        when(mockTeamService.getCurrentMembersForTeam(10L)).thenReturn(Collections.emptyList());

        userEntityListener.performNotificationLogic(testUser, "TEST_ACTION_EMPTY_MEMBERS");

        ArgumentCaptor<List> payloadCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockNotificationService, times(1)).notifyTeamMembers(eq(10L), eq("MEMBERS"), payloadCaptor.capture());
        assertTrue(payloadCaptor.getValue().isEmpty());
    }

    @Test
    void performNotificationLogic_nullUser_logsWarningAndReturns() {

        userEntityListener.performNotificationLogic(null, "TEST_NULL_USER");

        verify(mockNotificationService, never()).notifyTeamMembers(any(), any(), any());
        verify(mockTeamService, never()).getCurrentMembersForTeam(any());

    }

    private void triggerAfterCommit() {
        assertTrue(TransactionSynchronizationManager.isSynchronizationActive(),
                "Synchronization should be active to trigger afterCommit.");
        assertFalse(TransactionSynchronizationManager.getSynchronizations().isEmpty(),
                "Synchronizations list should not be empty.");

        List<TransactionSynchronization> synchronizations = new ArrayList<>(
                TransactionSynchronizationManager.getSynchronizations());
        for (TransactionSynchronization synchronization : synchronizations) {
            synchronization.afterCommit();
        }
    }

    private void triggerAfterCompletion(int status) {
        assertTrue(TransactionSynchronizationManager.isSynchronizationActive(),
                "Synchronization should be active to trigger afterCompletion.");
        List<TransactionSynchronization> synchronizations = new ArrayList<>(
                TransactionSynchronizationManager.getSynchronizations());
        for (TransactionSynchronization synchronization : synchronizations) {
            synchronization.afterCompletion(status);
        }
    }

    @Test
    void afterUserPersist_userWithTeamAndNoActiveTransaction_logsWarningDoesNotNotify() {
        TransactionSynchronizationManager.clearSynchronization();

        userEntityListener.afterUserPersist(testUser);

        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
        verify(mockNotificationService, never()).notifyTeamMembers(any(), any(), any());

    }

    @Test
    void afterUserPersist_userWithoutTeam_doesNotRegisterOrNotify() {

        TransactionSynchronizationManager.clearSynchronization();
        testUser.setTeamId(null);
        TransactionSynchronizationManager.initSynchronization();

        userEntityListener.afterUserPersist(testUser);

        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
        verify(mockNotificationService, never()).notifyTeamMembers(any(), any(), any());
        verify(mockTeamService, never()).getCurrentMembersForTeam(any());
    }

    @Test
    void afterUserPersist_nullUser_logsWarningAndSkips() {

        userEntityListener.afterUserPersist(null);

        verify(mockNotificationService, never()).notifyTeamMembers(any(), any(), any());
        verify(mockTeamService, never()).getCurrentMembersForTeam(any());
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive()
                && !TransactionSynchronizationManager.getSynchronizations().isEmpty());
    }

    @Test
    void afterUserUpdate_nullUser_logsWarningAndSkips() {

        userEntityListener.afterUserUpdate(null);

        verify(mockNotificationService, never()).notifyTeamMembers(any(), any(), any());
        verify(mockTeamService, never()).getCurrentMembersForTeam(any());
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive()
                && !TransactionSynchronizationManager.getSynchronizations().isEmpty());
    }

    @Test
    void afterUserRemove_nullUser_logsWarningAndSkips() {

        userEntityListener.afterUserRemove(null);

        verify(mockNotificationService, never()).notifyTeamMembers(any(), any(), any());
        verify(mockTeamService, never()).getCurrentMembersForTeam(any());
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive()
                && !TransactionSynchronizationManager.getSynchronizations().isEmpty());
    }

    @Test
    void afterUserPersist_userWithTeam_InUnitTestEnvironment_DoesNotNotifyDirectlyAndDoesNotRegisterSync() {

        TransactionSynchronizationManager.clearSynchronization();

        TransactionSynchronizationManager.initSynchronization();

        userEntityListener.afterUserPersist(testUser);

        verify(mockNotificationService, never()).notifyTeamMembers(any(), any(), any());
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty(),
                "No synchronization should have been registered if transaction was not seen as active by listener.");

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}