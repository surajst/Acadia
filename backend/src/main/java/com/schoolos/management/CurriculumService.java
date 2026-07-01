package com.schoolos.management;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CurriculumService {

    private final CurriculumRepository curriculumRepository;

    // Hardcoded Greenwood static tenant ID for now, as per instruction
    private final UUID defaultTenantId = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Autowired
    public CurriculumService(CurriculumRepository curriculumRepository) {
        this.curriculumRepository = curriculumRepository;
    }

    public List<Curriculum> getTopics(SyllabusType syllabus, int standard, SubjectType subject) {
        if (subject == null) {
            return curriculumRepository.findByTenantIdAndSyllabusTypeAndStandardOrderByTopicOrderAsc(
                    defaultTenantId, syllabus, standard);
        }
        return curriculumRepository.findByTenantIdAndSyllabusTypeAndStandardAndSubjectTypeOrderByTopicOrderAsc(
                defaultTenantId, syllabus, standard, subject);
    }

    public List<SubjectType> getSubjects(SyllabusType syllabus, int standard) {
        List<Curriculum> records = curriculumRepository.findByTenantIdAndSyllabusTypeAndStandard(
                defaultTenantId, syllabus, standard);
        
        return records.stream()
                .map(Curriculum::getSubjectType)
                .distinct()
                .collect(Collectors.toList());
    }
}
