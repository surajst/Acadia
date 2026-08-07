package com.concept.roster.app;

import java.util.UUID;

/**
 * Raised when a student cannot be read in the caller's tenant — either it does
 * not exist or it belongs to another school. The two cases are deliberately
 * indistinguishable to the caller so the endpoint never confirms the existence
 * of another tenant's student.
 */
public class StudentProfileNotFoundException extends RuntimeException {
    public StudentProfileNotFoundException(UUID studentId) {
        super("Student not found in tenant: " + studentId);
    }
}
