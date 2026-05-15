package org.learnings.payments.messaging.outbox.jpa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class OutboxSchedulerTest {

    @Mock
    private OutboxEventProcessorRunner runner;
    @InjectMocks
    private OutboxScheduler scheduler;

    @Test
    void process_delegatesToRunner() {
        scheduler.process();

        verify(runner).run();
        verifyNoMoreInteractions(runner);
    }
}
