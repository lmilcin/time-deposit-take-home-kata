package org.ikigaidigital.config;

import org.ikigaidigital.application.port.out.InterestCalculationProcessRepository;
import org.ikigaidigital.application.port.out.TimeDepositRepository;
import org.ikigaidigital.application.service.TimeDepositService;
import org.ikigaidigital.domain.interest.BasicPlanAccrualStrategy;
import org.ikigaidigital.domain.interest.InterestAccrualStrategies;
import org.ikigaidigital.domain.interest.InterestAccrualStrategy;
import org.ikigaidigital.domain.interest.PremiumPlanAccrualStrategy;
import org.ikigaidigital.domain.interest.StudentPlanAccrualStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** Wires the framework-free domain and application layers into the Spring context. */
@Configuration
public class ApplicationConfiguration {

    @Bean
    BasicPlanAccrualStrategy basicPlanAccrualStrategy() {
        return new BasicPlanAccrualStrategy();
    }

    @Bean
    StudentPlanAccrualStrategy studentPlanAccrualStrategy() {
        return new StudentPlanAccrualStrategy();
    }

    @Bean
    PremiumPlanAccrualStrategy premiumPlanAccrualStrategy() {
        return new PremiumPlanAccrualStrategy();
    }

    /** Collects every {@link InterestAccrualStrategy} bean; a new plan only needs a new strategy bean. */
    @Bean
    InterestAccrualStrategies interestAccrualStrategies(List<InterestAccrualStrategy> strategies) {
        return new InterestAccrualStrategies(strategies);
    }

    @Bean
    TimeDepositService timeDepositService(TimeDepositRepository repository,
                                          InterestCalculationProcessRepository processRepository,
                                          InterestAccrualStrategies accrualStrategies,
                                          @Value("${time-deposits.batch-size}") int batchSize) {
        return new TimeDepositService(repository, processRepository, accrualStrategies, batchSize);
    }
}
