package com.schwab.urlshortener.analytics.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.schwab.urlshortener.analytics.domain.ClickEvent;
import com.schwab.urlshortener.analytics.repository.ClickEventRepository;
import com.schwab.urlshortener.url.service.ResolvedUrl;
import com.schwab.urlshortener.url.service.UrlService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.UnexpectedRollbackException;

class AnalyticsServiceTest {

    private static final ResolvedUrl RESOLVED = new ResolvedUrl(1L, "abcdef", "https://example.com/");

    private final ClickEventRepository repository = mock(ClickEventRepository.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final AnalyticsService service =
            new AnalyticsService(repository, mock(UrlService.class), transactionManager);

    @Test
    void recordsClickInItsOwnRequiresNewTransaction() {
        service.recordClick(RESOLVED);

        verify(transactionManager).getTransaction(
                argThat((TransactionDefinition definition) ->
                        definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        verify(repository).save(any(ClickEvent.class));
    }

    @Test
    void recordClickNeverThrowsWhenPersistenceFails() {
        when(repository.save(any())).thenThrow(new DataAccessResourceFailureException("database down"));

        assertThatCode(() -> service.recordClick(RESOLVED)).doesNotThrowAnyException();
    }

    @Test
    void recordClickNeverThrowsWhenCommitFails() {
        doThrow(new UnexpectedRollbackException("rolled back")).when(transactionManager).commit(any());

        assertThatCode(() -> service.recordClick(RESOLVED)).doesNotThrowAnyException();
    }
}
