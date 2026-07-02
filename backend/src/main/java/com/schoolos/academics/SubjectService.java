package com.schoolos.academics;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SubjectService {

    /**
     * The 5 subjects this codebase hardcoded before Subject became data-driven.
     * Seeded once per tenant so nothing changes in behavior for existing tenants.
     */
    private static final String[][] DEFAULT_SUBJECTS = {
            {"MATHEMATICS", "Mathematics"},
            {"SCIENCE", "Science"},
            {"SOCIAL_SCIENCE", "Social Science"},
            {"ENGLISH", "English"},
            {"LANGUAGE", "Language"},
    };

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private GradeSubjectRepository gradeSubjectRepository;

    /** Idempotent: does nothing if the tenant already has any subjects. */
    @Transactional
    public void seedDefaultSubjectsIfNone(UUID tenantId, UUID academicYearId) {
        if (tenantId == null || subjectRepository.countByTenantId(tenantId) > 0) return;

        for (int i = 0; i < DEFAULT_SUBJECTS.length; i++) {
            Subject subject = new Subject();
            subject.setId(UUID.randomUUID());
            subject.setTenantId(tenantId);
            subject.setAcademicYearId(academicYearId);
            subject.setCode(DEFAULT_SUBJECTS[i][0]);
            subject.setDisplayName(DEFAULT_SUBJECTS[i][1]);
            subject.setActive(true);
            subject.setSortOrder(i);
            subjectRepository.save(subject);
        }
    }

    public List<Subject> listActiveSubjects(UUID tenantId) {
        return subjectRepository.findByTenantIdAndActiveTrueOrderBySortOrderAsc(tenantId);
    }

    public List<Subject> listAllSubjects(UUID tenantId) {
        return subjectRepository.findByTenantIdOrderBySortOrderAsc(tenantId);
    }

    @Transactional
    public Subject createSubject(UUID tenantId, UUID academicYearId, String code, String displayName, String colorHex) {
        Subject subject = new Subject();
        subject.setId(UUID.randomUUID());
        subject.setTenantId(tenantId);
        subject.setAcademicYearId(academicYearId);
        subject.setCode(code);
        subject.setDisplayName(displayName);
        subject.setColorHex(colorHex);
        subject.setActive(true);
        subject.setSortOrder((int) subjectRepository.countByTenantId(tenantId));
        return subjectRepository.save(subject);
    }

    @Transactional
    public Subject renameSubject(UUID subjectId, String newDisplayName) {
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new IllegalArgumentException("Subject not found: " + subjectId));
        subject.setDisplayName(newDisplayName);
        return subjectRepository.save(subject);
    }

    @Transactional
    public void setActive(UUID subjectId, boolean active) {
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new IllegalArgumentException("Subject not found: " + subjectId));
        subject.setActive(active);
        subjectRepository.save(subject);
    }

    public List<Subject> listSubjectsForGrade(UUID tenantId, String gradeName) {
        List<UUID> subjectIds = gradeSubjectRepository.findSubjectIdsByTenantIdAndGradeName(tenantId, gradeName);
        return subjectRepository.findAllById(subjectIds);
    }

    /** Replaces the full subject list for a grade in one call — admin sets it once per grade. */
    @Transactional
    public void assignSubjectsToGrade(UUID tenantId, UUID academicYearId, String gradeName, List<UUID> subjectIds) {
        gradeSubjectRepository.deleteByTenantIdAndGradeName(tenantId, gradeName);
        for (UUID subjectId : subjectIds) {
            Subject subject = subjectRepository.findById(subjectId)
                    .orElseThrow(() -> new IllegalArgumentException("Subject not found: " + subjectId));
            GradeSubject mapping = new GradeSubject();
            mapping.setId(UUID.randomUUID());
            mapping.setTenantId(tenantId);
            mapping.setAcademicYearId(academicYearId);
            mapping.setGradeName(gradeName);
            mapping.setSubject(subject);
            gradeSubjectRepository.save(mapping);
        }
    }
}
