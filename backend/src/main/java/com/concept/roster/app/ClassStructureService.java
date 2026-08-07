package com.concept.roster.app;

import com.concept.common.AuditLogService;
import com.concept.management.ClassSection;
import com.concept.management.SchoolClass;
import com.concept.roster.data.RosterClassSectionRepository;
import com.concept.roster.data.RosterSchoolClassRepository;
import com.concept.roster.data.RosterStudentRepository;
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
    private final RosterSchoolClassRepository schoolClassRepository;
    private final RosterStudentRepository studentRepository;
    private final AuditLogService auditLogService;

    public ClassStructureService(RosterClassSectionRepository classSectionRepository,
                                 RosterSchoolClassRepository schoolClassRepository,
                                 RosterStudentRepository studentRepository,
                                 AuditLogService auditLogService) {
        this.classSectionRepository = classSectionRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.studentRepository = studentRepository;
        this.auditLogService = auditLogService;
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

    @Transactional
    public void addSection(UUID tenantId, UUID academicYearId, String gradeName, String sectionName,
                           String roomNumber, Authentication authentication) {
        ClassSection classSection = new ClassSection();
        classSection.setId(UUID.randomUUID());
        classSection.setTenantId(tenantId);
        classSection.setAcademicYearId(academicYearId);
        classSection.setGradeName(gradeName);
        classSection.setSectionName(sectionName);
        classSection.setRoomNumber(roomNumber);
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
        classSectionRepository.delete(section);
        auditLogService.log(authentication, "CLASS_SECTION_REMOVED", "ClassSection", id,
                "Removed class section " + section.getGradeName() + " - " + section.getSectionName());
    }

    @Transactional
    public void addClassroom(UUID tenantId, UUID academicYearId, String gradeLevel, String sectionName,
                             String roomNumber, Integer totalCapacity, Authentication authentication) {
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setId(UUID.randomUUID());
        schoolClass.setTenantId(tenantId);
        schoolClass.setAcademicYearId(academicYearId);
        schoolClass.setGradeLevel(gradeLevel);
        schoolClass.setSectionName(sectionName);
        schoolClass.setRoomNumber(roomNumber);
        schoolClass.setTotalCapacity(totalCapacity);
        schoolClassRepository.save(schoolClass);
        auditLogService.log(authentication, "SCHOOL_CLASS_ADDED", "SchoolClass", schoolClass.getId(),
                "Added classroom " + gradeLevel + " - " + sectionName);
    }
}
