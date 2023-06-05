package com.undertone.adselector.application.adaptors.services;

import com.undertone.adselector.application.ports.in.UseCaseException;
import com.undertone.adselector.application.ports.in.UseCaseException.AbortedException;
import com.undertone.adselector.application.ports.out.AdDistributionStore;
import com.undertone.adselector.model.AdBudget;
import com.undertone.adselector.model.AdDistribution;
import com.undertone.adselector.model.Status;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WeightedRandomSelectionTest {

    private AutoCloseable closeable;

    @Mock
    private AdDistributionStore adDistributionStoreMock;

    @BeforeEach
    public void before() {
        closeable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    public void after() throws Exception {
        closeable.close();
    }

    @Test
    @DisplayName("Given entire population have remaining quota, when selecting, then select from entire population")
    void select_givenPopulationHaveRemainingQuota_whenSelecting_thenUseEntirePopulation_positive() {

        // GIVEN
        var sutSpy = spy(new WeightedRandomSelection(adDistributionStoreMock));

        var testAdDistribution = new AdDistributionMock("testRemainingQuota", 0.5d, 3l, 2l);
        var testPopulation = List.of(testAdDistribution.asAdBudget());

        var selectionPopulationCaptor = ArgumentCaptor.forClass(List.class);

        // WHEN
        when(adDistributionStoreMock
                .fetchDistributions(Mockito.anyList()))
                        .thenReturn(Mono.just(List.of(testAdDistribution)));
        when(adDistributionStoreMock.incrementDistribution(eq(testAdDistribution)))
                .thenReturn(Mono.just(Status.SUCCESS));

        Mono<Optional<AdBudget>> actualSelection = sutSpy.select(testPopulation);

        // THEN
        Optional<AdBudget> adBudgetOp = actualSelection.block();
        assertNotNull(adBudgetOp, "Expected non empty Mono");
        assertTrue(adBudgetOp.isPresent(), "Expected selection of existing AdBudget");

        AdBudget actualAdBudget = adBudgetOp.get();
        assertEquals("testRemainingQuota", actualAdBudget.aid());
        assertEquals(0.5d, actualAdBudget.priority());
        assertEquals(3l, actualAdBudget.quota());

        verify(sutSpy, times(1)).doSelect(selectionPopulationCaptor.capture());
        List<AdDistribution> actualSelectedPopulation = selectionPopulationCaptor.getValue();
        assertFalse(actualSelectedPopulation.isEmpty(), "Expected to selection against non empty population");

    }

    @Test
    @DisplayName("Given population is partly exhausted, when selecting, then select only from non exhausted")
    void select_givenPopulationPartlyExhausted_whenSelecting_thenUseOnlyNonExhausted_positive() {

        // GIVEN
        var sutSpy = spy(new WeightedRandomSelection(adDistributionStoreMock));

        var testNonExhaustedDist = new AdDistributionMock("testRemainingQuota", 0.5d, 3l, 2l);
        var testExhaustedDist = new AdDistributionMock("testExhaustedQuota", 0.5d, 3l, 0l);

        var testPopulation = List.of(testNonExhaustedDist.asAdBudget(), testExhaustedDist.asAdBudget());
        var selectionPopulationCaptor = ArgumentCaptor.forClass(List.class);

        // WHEN
        when(adDistributionStoreMock
                .fetchDistributions(Mockito.anyList()))
                    .thenReturn(Mono.just(List.of(testNonExhaustedDist, testExhaustedDist)));
        when(adDistributionStoreMock.incrementDistribution(eq(testNonExhaustedDist)))
                .thenReturn(Mono.just(Status.SUCCESS));

        Mono<Optional<AdBudget>> actualSelection = sutSpy.select(testPopulation);

        // THEN
        Optional<AdBudget> adBudgetOp = actualSelection.block();
        assertNotNull(adBudgetOp, "Expected non empty Mono");
        assertTrue(adBudgetOp.isPresent(), "Expected selection of existing AdBudget");

        AdBudget actualAdBudget = adBudgetOp.get();
        assertEquals("testRemainingQuota", actualAdBudget.aid());
        assertEquals(0.5d, actualAdBudget.priority());
        assertEquals(3l, actualAdBudget.quota());

        verify(sutSpy, times(1)).doSelect(selectionPopulationCaptor.capture());
        List<AdDistribution> actualSelectedPopulation = selectionPopulationCaptor.getValue();
        assertFalse(actualSelectedPopulation.isEmpty(), "Expected to selection against non empty population");

    }

    @Test
    @DisplayName("Given population exhausted, when selecting, then abort selection and return empty")
    void select_givenPopulationExhausted_whenSelecting_thenAbortSelectionAndReturnEmpty_positive() {

        // GIVEN
        var sutSpy = spy(new WeightedRandomSelection(adDistributionStoreMock));

        var testExhaustedDist = new AdDistributionMock("testExhaustedQuota", 0.5d, 3l, 0l);

        var testPopulation = List.of(testExhaustedDist.asAdBudget());
        var fetchDistributionCaptor = ArgumentCaptor.forClass(List.class);

        // WHEN
        when(adDistributionStoreMock
                .fetchDistributions(Mockito.anyList()))
                    .thenReturn(Mono.just(List.of(testExhaustedDist)));
        when(adDistributionStoreMock.incrementDistribution(eq(testExhaustedDist)))
                .thenReturn(Mono.just(Status.FAILURE));

        Mono<Optional<AdBudget>> actualSelection = sutSpy.select(testPopulation);

        // THEN
        Optional<AdBudget> adBudgetOp = actualSelection.block();
        assertNull(adBudgetOp, "Expected empty Mono");

        verify(sutSpy, never()).doSelect(anyList());
        verify(adDistributionStoreMock, times(1)).fetchDistributions(fetchDistributionCaptor.capture());
        List<AdBudget> actualAdBudgetPopulation = fetchDistributionCaptor.getValue();
        assertEquals(1, actualAdBudgetPopulation.size(), "Expected to fetch single distribution");

        AdBudget actualAdBudget = actualAdBudgetPopulation.get(0);
        assertEquals("testExhaustedQuota", actualAdBudget.aid());
        assertEquals(0.5d, actualAdBudget.priority());
        assertEquals(3l, actualAdBudget.quota());

    }

    @Test
    @DisplayName("Given incrementing distribution, when conflict, then throw aborted exception")
    void select_givenIncrementing_whenConflict_thenThrowAbortedException_negative() {

        // GIVEN
        var sutSpy = spy(new WeightedRandomSelection(adDistributionStoreMock));

        var testAdDistribution = new AdDistributionMock("testRemainingQuota", 0.5d, 3l, 1l);

        var testPopulation = List.of(testAdDistribution.asAdBudget());

        // WHEN
        when(adDistributionStoreMock
                .fetchDistributions(Mockito.anyList()))
                .thenReturn(Mono.just(List.of(testAdDistribution)));
        when(adDistributionStoreMock.incrementDistribution(eq(testAdDistribution)))
                .thenReturn(Mono.just(Status.CONFLICT));

        Mono<Optional<AdBudget>> actualSelection = sutSpy.select(testPopulation);

        // THEN
        Assertions.assertThrows(AbortedException.class,
                () -> actualSelection.block(), "Expected exceptional Mono");

    }


    record AdDistributionMock(String aid, double priority, long quota, long remainingQuota) implements AdDistribution {
        @Override
        public String toString() {
            return format(
                    """
                    {
                      "aid": "%s",
                      "priority": %.2f,
                      "quota": %d,
                      "remaining": %d
                    }
                    """, aid, priority, quota, remainingQuota);
        }

        public AdBudget asAdBudget(){
            return this;
        }

    }

}
