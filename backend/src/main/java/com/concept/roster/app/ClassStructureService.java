package com.concept.roster.app;

import com.concept.common.AuditLogService;
import com.concept.shared.data.ClassSection;
import com.concept.roster.data.RosterClassSectionRepository;
import com.concept.roster.data.RosterStudentRepository;
import com.concept.timetable.data.TimetableRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application layer for admin class-structure management (class sections and
 * classrooms). Owns the tenant-scoped edits/deletes and the audit trail; the
 * web layer only binds and delegates.
 */
@Service
public class ClassStructureService {

    private final RosterClassSectionRepository classSectionRepository;
    private final RosterStudentRepository studentRepository;
    private final AuditLogService auditLogService;
    private final TimetableRepository timetableRepository;

    public ClassStructureService(RosterClassSectionRepository classSectionRepository,
                                 RosterStudentRepository studentRepository,
                                 AuditLogService auditLogService,
                                 TimetableRepository timetableRepository) {
        this.classSectionRepository = classSectionRepository;
        this.studentRepository = studentRepository;
        this.auditLogService = auditLogService;
        this.timetableRepository = timetableRepository;
    }

    @Transactional(readOnly = true)
    public List<ClassSectionDto> listSections(UUID tenantId) {
        if (tenantId == null) {
            return Collections.emptyList();
        }
        return classSectionRepository.findByTenantId(tenantId).stream()
                .map(s -> new ClassSectionDto(s.getId(), s.getGradeName(), s.getSectionName(), s.getRoomNumber()))
                .collect(Collectors.toList());
    }

    /**
     * Two sections are the same section if their grade and name match once
     * trimmed and lowercased. "Grade 6"/"A" and "grade 6"/"a " are one class
     * with two rows, and every roster, timetable and fee figure then splits
     * between them in ways nobody can reconcile afterwards.
     */
    private void refuseDuplicate(UUID tenantId, String gradeName, String sectionName, UUID ignoreId) {
        String grade = normalise(gradeName);
        String name = normalise(sectionName);
        boolean taken = classSectionRepository.findByTenantId(tenantId).stream()
                .filter(existing -> ignoreId == null || !existing.getId().equals(ignoreId))
                .anyMatch(existing -> normalise(existing.getGradeName()).equals(grade)
                        && normalise(existing.getSectionName()).equals(name));
        if (taken) {
            throw new IllegalArgumentException(
                    gradeName.trim() + " " + sectionName.trim() + " already exists.");
        }
    }

    private static String normalise(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("\s+", " ");
    }

    @Transactional
    public void addSection(UUID tenantId, UUID academicYearId, String gradeName, String sectionName,
                           String roomNumber, Integer totalCapacity, Authentication authentication) {
        if (gradeName == null || gradeName.isBlank() || sectionName == null || sectionName.isBlank()) {
            throw new IllegalArgumentException("Grade and section are required");
        }
        refuseDuplicate(tenantId, gradeName, sectionName, null);

        ClassSection classSection = new ClassSection();
        classSection.setId(UUID.randomUUID());
        classSection.setTenantId(tenantId);
        classSection.setAcademicYearId(academicYearId);
        classSection.setGradeName(gradeName.trim());
        classSection.setSectionName(sectionName.trim());
        classSection.setRoomNumber(roomNumber != null ? roomNumber.trim() : null);
        classSection.setTotalCapacity(totalCapacity);
        classSectionRepository.save(classSection);
        auditLogService.log(authentication, "CLASS_SECTION_ADDED", "ClassSection", classSection.getId(),
                "Added class section " + gradeName + " - " + sectionName);
    }

    @Transactional
    public void updateSection(UUID id, UUID tenantId, String gradeName, String sectionName,
                              String roomNumber, Authentication authentication) {
        // Tenant-scoped: never let one school edit another's section.
        ClassSection section = classSectionRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Class section not found"));
        if (gradeName == null || gradeName.isBlank() || sectionName == null || sectionName.isBlank()) {
            throw new IllegalArgumentException("Grade and section are required");
        }
        refuseDuplicate(tenantId, gradeName, sectionName, id);
        section.setGradeName(gradeName.trim());
        section.setSectionName(sectionName.trim());
        section.setRoomNumber(roomNumber != null ? roomNumber.trim() : null);
        classSectionRepository.save(section);
        auditLogService.log(authentication, "CLASS_SECTION_UPDATED", "ClassSection", id,
                "Updated class section to " + gradeName + " - " + sectionName);
    }

    @Transactional
    public void removeSection(UUID id, UUID tenantId, Authentication authentication) {
        // Tenant-scoped: never let one school delete another's section.
        ClassSection section = classSectionRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Class section not found"));
        // Don't orphan students — require the section to be empty first.
        if (studentRepository.countByClassSection(section) > 0) {
            throw new IllegalArgumentException("Cannot remove a section that still has students");
        }
        // A timetable entry points at the section, so deleting it out from
        // under one leaves a period belonging to a class that no longer exists.
        long periods = timetableRepository.findByClassSectionId(section.getId()).size();
        if (periods > 0) {
            throw new IllegalArgumentException("Remove this section's " + periods
                    + " timetable period" + (periods == 1 ? "" : "s") + " first.");
        }
        classSectionRepository.delete(section);
        auditLogService.log(authentication, "CLASS_SECTION_REMOVED", "ClassSection", id,
                "Removed class section " + section.getGradeName() + " - " + section.getSectionName());
    }

}
