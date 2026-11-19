package org.ikigaidigital.adapter.in.web;

import org.ikigaidigital.api.model.TimeDeposit;
import org.ikigaidigital.api.model.Withdrawal;
import org.ikigaidigital.domain.TimeDepositDetails;
import org.springframework.stereotype.Component;

@Component
public class TimeDepositApiMapper {

    public TimeDeposit toApi(TimeDepositDetails details) {
        var timeDeposit = details.timeDeposit();
        return new TimeDeposit(
                timeDeposit.getId(),
                timeDeposit.getPlanType(),
                timeDeposit.getBalance(),
                timeDeposit.getDays(),
                details.withdrawals().stream().map(this::toApi).toList());
    }

    private Withdrawal toApi(org.ikigaidigital.domain.Withdrawal withdrawal) {
        return new Withdrawal(withdrawal.id(), withdrawal.amount().doubleValue(), withdrawal.date());
    }
}
