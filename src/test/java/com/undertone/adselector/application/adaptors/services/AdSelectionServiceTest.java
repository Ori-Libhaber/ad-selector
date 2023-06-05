package com.undertone.adselector.application.adaptors.services;

import com.undertone.adselector.application.ports.in.AdSelectionStrategy;
import com.undertone.adselector.application.ports.out.AdBudgetPlanStore;
import com.undertone.adselector.model.AdBudget;
import com.undertone.adselector.model.AdBudgetPlan;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.Set;

import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class AdSelectionServiceTest {

    private AutoCloseable closeable;

    @Mock
    private AdBudgetPlanStore adBudgetPlanStoreMock;

    @Mock
    private AdBudgetPlan adBudgetPlanMock;

    @Mock
    private AdSelectionStrategy adSelectionStrategyMock;

    @BeforeEach
    public void before() {
        closeable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    public void after() throws Exception {
        closeable.close();
    }

    @Test
    @DisplayName("Given set of aids, when ad budget plan contains matching entries, then selection using selectionStrategy")
    void selectAd_givenSetOfAids_whenFetchedAdBudgetsFromPlanStore_thenPassToSelectionStrategy() {

        // GIVEN
        AdBudgetMock adBudgetMock1 = new AdBudgetMock("test1", 0.2, 5);
        AdBudgetMock adBudgetMock2 = new AdBudgetMock("test2", 0.66, 15);

        Set<String> testAids = Set.of("test1", "test2");

        var sut = new AdSelectionService(adSelectionStrategyMock, adBudgetPlanStoreMock);

        // WHEN
        when(adBudgetPlanStoreMock.fetchPlan()).thenReturn(Mono.just(adBudgetPlanMock));
        when(adBudgetPlanMock.fetch(eq("test1"))).thenReturn(Optional.of(adBudgetMock1));
        when(adBudgetPlanMock.fetch(eq("test2"))).thenReturn(Optional.of(adBudgetMock2));
        when(adSelectionStrategyMock.select(anyList())).thenReturn(Mono.just(Optional.of(adBudgetMock2)));

        Mono<Optional<String>> actualMonoResponse = sut.selectAd(testAids);

        // THEN
        Optional<String> actualAidOp = actualMonoResponse.block();
        assertNotNull(actualAidOp, "Expected non empty Mono");

        assertTrue(actualAidOp.isPresent(), "Expected non empty Optional");
        String actualAid = actualAidOp.get();

        assertEquals("test2", actualAid);

    }


    record AdBudgetMock(String aid, double priority, long quota) implements AdBudget {
        @Override
        public String toString() {
            return format(
                    """
                    {
                      "aid": "%s",
                      "priority": %.2f,
                      "quota": %d
                    }
                    """, aid, priority, quota);
        }
    }

}
