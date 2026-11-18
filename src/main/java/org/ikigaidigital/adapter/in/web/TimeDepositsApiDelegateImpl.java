package org.ikigaidigital.adapter.in.web;

import org.ikigaidigital.api.TimeDepositsApiDelegate;
import org.ikigaidigital.api.model.TimeDeposit;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Implementation of the delegate generated from openapi.yaml.
 * Scaffold only: returns placeholder responses until the domain is wired in.
 */
@Service
public class TimeDepositsApiDelegateImpl implements TimeDepositsApiDelegate {

    @Override
    public ResponseEntity<List<TimeDeposit>> getTimeDeposits() {
        return ResponseEntity.ok(List.of());
    }

    @Override
    public ResponseEntity<Void> calculateInterest() {
        return ResponseEntity.noContent().build();
    }
}
