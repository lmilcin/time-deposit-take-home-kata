package org.ikigaidigital.application.service;

import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.TimeDepositCalculator;
import org.ikigaidigital.application.port.out.TimeDepositRepository;
import org.ikigaidigital.domain.TimeDepositDetails;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeDepositServiceTest {

    private final InMemoryRepository repository = new InMemoryRepository(List.of(
            new TimeDeposit(1, "basic", 1000.00, 60),
            new TimeDeposit(2, "student", 2000.00, 60),
            new TimeDeposit(3, "premium", 3000.00, 60),
            new TimeDeposit(4, "basic", 4000.00, 10),
            new TimeDeposit(5, "premium", 5000.00, 40)));

    private final TimeDepositService service = new TimeDepositService(repository, new TimeDepositCalculator(), 2);

    @Test
    void calculateInterestUpdatesEveryBatchUsingTheCalculator() {
        List<TimeDeposit> expected = repository.copyOfAll();
        new TimeDepositCalculator().updateBalance(expected);

        service.calculateInterest();

        assertThat(repository.updatedBatchSizes).containsExactly(2, 2, 1);
        assertThat(repository.copyOfAll())
                .extracting(TimeDeposit::getBalance)
                .containsExactlyElementsOf(expected.stream().map(TimeDeposit::getBalance).toList());
    }

    @Test
    void getTimeDepositsFlattensAllBatches() {
        try (Stream<TimeDepositDetails> deposits = service.getTimeDeposits()) {
            assertThat(deposits.map(details -> details.timeDeposit().getId())).containsExactly(1, 2, 3, 4, 5);
        }
    }

    @Test
    void rejectsNonPositiveBatchSize() {
        assertThatThrownBy(() -> new TimeDepositService(repository, new TimeDepositCalculator(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class InMemoryRepository implements TimeDepositRepository {

        private final List<TimeDeposit> store;
        private final List<Integer> updatedBatchSizes = new ArrayList<>();

        InMemoryRepository(List<TimeDeposit> timeDeposits) {
            this.store = new ArrayList<>(timeDeposits);
        }

        List<TimeDeposit> copyOfAll() {
            return store.stream().map(InMemoryRepository::copy).toList();
        }

        @Override
        public Stream<List<TimeDeposit>> findAllInBatches(int batchSize) {
            return IntStream.iterate(0, from -> from < store.size(), from -> from + batchSize)
                    .mapToObj(from -> store.subList(from, Math.min(from + batchSize, store.size())).stream()
                            .map(InMemoryRepository::copy)
                            .toList());
        }

        @Override
        public Stream<List<TimeDepositDetails>> findAllWithWithdrawalsInBatches(int batchSize) {
            return findAllInBatches(batchSize)
                    .map(batch -> batch.stream().map(td -> new TimeDepositDetails(td, List.of())).toList());
        }

        @Override
        public void updateBalances(List<TimeDeposit> timeDeposits) {
            updatedBatchSizes.add(timeDeposits.size());
            timeDeposits.forEach(updated -> store.replaceAll(td -> td.getId() == updated.getId() ? copy(updated) : td));
        }

        private static TimeDeposit copy(TimeDeposit td) {
            return new TimeDeposit(td.getId(), td.getPlanType(), td.getBalance(), td.getDays());
        }
    }
}
