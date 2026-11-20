package org.ikigaidigital.adapter.in.web;

import org.ikigaidigital.api.model.Problem;
import org.ikigaidigital.domain.process.InterestCalculationConflictException;
import org.ikigaidigital.domain.process.InterestCalculationNotAllowedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps rejected interest calculation runs to the 409 response defined in openapi.yaml. */
@RestControllerAdvice
public class InterestCalculationExceptionHandler {

    @ExceptionHandler({InterestCalculationNotAllowedException.class, InterestCalculationConflictException.class})
    ResponseEntity<Problem> handleRejectedRun(RuntimeException exception) {
        HttpStatus status = HttpStatus.CONFLICT;
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(new Problem(status.getReasonPhrase(), status.value(), exception.getMessage()));
    }
}
