package com.eneik.production.exceptions;

/**
 * @file DataComplianceException.java
 * @agent TAG-10 (Deontic Prohibition)
 * @description Exception thrown when a data compliance violation is detected.
 */
public class DataComplianceException extends RuntimeException {
    private final String violationType;

    public DataComplianceException(String violationType) {
        super("Data compliance violation: " + violationType);
        this.violationType = violationType;
    }

    public String getViolationType() {
        return violationType;
    }
}
