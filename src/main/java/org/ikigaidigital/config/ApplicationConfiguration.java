package org.ikigaidigital.config;

import org.ikigaidigital.TimeDepositCalculator;
import org.ikigaidigital.application.port.out.TimeDepositRepository;
import org.ikigaidigital.application.service.TimeDepositService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the framework-free domain and application layers into the Spring context. */
@Configuration
public class ApplicationConfiguration {

    @Bean
    TimeDepositCalculator timeDepositCalculator() {
        return new TimeDepositCalculator();
    }

    @Bean
    TimeDepositService timeDepositService(TimeDepositRepository repository,
                                          TimeDepositCalculator calculator,
                                          @Value("${time-deposits.batch-size}") int batchSize) {
        return new TimeDepositService(repository, calculator, batchSize);
    }
}
