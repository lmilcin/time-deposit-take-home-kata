package org.ikigaidigital.adapter.in.web;

import org.ikigaidigital.api.TimeDepositsApiDelegate;
import org.ikigaidigital.application.port.in.CalculateInterestUseCase;
import org.ikigaidigital.application.port.in.GetTimeDepositsUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.OptionalInt;

/** Implementation of the delegate generated from openapi.yaml. */
@Component
public class TimeDepositsApiDelegateImpl implements TimeDepositsApiDelegate {

    private final GetTimeDepositsUseCase getTimeDeposits;
    private final CalculateInterestUseCase calculateInterest;
    private final TimeDepositApiMapper mapper;

    public TimeDepositsApiDelegateImpl(GetTimeDepositsUseCase getTimeDeposits,
                                       CalculateInterestUseCase calculateInterest,
                                       TimeDepositApiMapper mapper) {
        this.getTimeDeposits = getTimeDeposits;
        this.calculateInterest = calculateInterest;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<TimeDepositStream> getTimeDeposits(Integer afterId) {
        OptionalInt resumeAfter = afterId == null ? OptionalInt.empty() : OptionalInt.of(afterId);
        return ResponseEntity.ok(new TimeDepositStream(getTimeDeposits.getTimeDeposits(resumeAfter).map(mapper::toApi)));
    }

    @Override
    public ResponseEntity<Void> calculateInterest(LocalDate accountingDate) {
        calculateInterest.calculateInterest(accountingDate);
        return ResponseEntity.noContent().build();
    }
}
