package com.concept.roster.app;

import com.concept.common.AuditLogService;
import com.concept.shared.data.PickupContact;
import com.concept.shared.data.PickupContactRepository;
import com.concept.shared.data.Student;
import com.concept.shared.data.StudentRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Who may collect a child.
 *
 * <p>Every change is audited. This is the one list in the system where the
 * question "who added this name, and when" can matter more than the name
 * itself, and a school asked that question after an incident should not have
 * to take anyone's word for the answer.
 */
@Service
public class PickupContactService {

    private final PickupContactRepository pickupContactRepository;
    private final StudentRepository studentRepository;
    private final AuditLogService auditLogService;

    public PickupContactService(PickupContactRepository pickupContactRepository,
                                StudentRepository studentRepository,
                                AuditLogService auditLogService) {
        this.pickupContactRepository = pickupContactRepository;
        this.studentRepository = studentRepository;
        this.auditLogService = auditLogService;
    }

    /** One authorised person, flattened for the template. */
    public record Row(UUID id, String name, String relationship, String phone) {}

    public List<Row> forStudent(UUID studentId, UUID tenantId) {
        return pickupContactRepository.findByStudentIdAndTenantIdOrderByNameAsc(studentId, tenantId).stream()
                .map(c -> new Row(c.getId(), c.getName(), c.getRelationship(), c.getPhone()))
                .toList();
    }

    @Transactional
    public void add(UUID studentId, String name, String relationship, String phone,
                    UUID tenantId, Authentication authentication) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A name is required -- a list entry nobody can identify is worse than no entry.");
        }
        // Resolved through the caller's tenant: an id in a request is not proof
        // of ownership, least of all on this list.
        Student student = studentRepository.findByIdAndTenantId(studentId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found."));

        PickupContact contact = new PickupContact();
        contact.setId(UUID.randomUUID());
        contact.setTenantId(tenantId);
        contact.setAcademicYearId(student.getAcademicYearId());
        contact.setStudentId(studentId);
        contact.setName(name.trim());
        contact.setRelationship(blankToNull(relationship));
        contact.setPhone(blankToNull(phone));
        pickupContactRepository.saveAndFlush(contact);

        auditLogService.log(authentication, "PICKUP_CONTACT_ADDED", "Student", studentId,
                "Authorised " + contact.getName()
                        + (contact.getRelationship() == null ? "" : " (" + contact.getRelationship() + ")")
                        + " to collect " + student.getFirstName() + " " + student.getLastName());
    }

    @Transactional
    public void remove(UUID contactId, UUID tenantId, Authentication authentication) {
        PickupContact contact = pickupContactRepository.findByIdAndTenantId(contactId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Contact not found."));
        // Audited before deletion, while the name is still readable -- a
        // revocation that leaves no trace of who was revoked is not a record.
        auditLogService.log(authentication, "PICKUP_CONTACT_REMOVED", "Student", contact.getStudentId(),
                "Revoked " + contact.getName() + "'s authorisation to collect this child");
        pickupContactRepository.delete(contact);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
