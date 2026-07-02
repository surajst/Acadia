package com.schoolos.management;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CurriculumService {

    private final CurriculumRepository curriculumRepository;

    @Autowired
    public CurriculumService(CurriculumRepository curriculumRepository) {
        this.curriculumRepository = curriculumRepository;
    }

    public List<Curriculum> getTopics(UUID tenantId, SyllabusType syllabus, int standard, String subjectCode) {
        if (subjectCode == null) {
            return curriculumRepository.findByTenantIdAndSyllabusTypeAndStandardOrderByTopicOrderAsc(
                    tenantId, syllabus, standard);
        }
        return curriculumRepository.findByTenantIdAndSyllabusTypeAndStandardAndSubjectCodeOrderByTopicOrderAsc(
                tenantId, syllabus, standard, subjectCode);
    }

    public List<String> getSubjects(UUID tenantId, SyllabusType syllabus, int standard) {
        List<Curriculum> records = curriculumRepository.findByTenantIdAndSyllabusTypeAndStandard(
                tenantId, syllabus, standard);

        return records.stream()
                .map(Curriculum::getSubjectCode)
                .distinct()
                .collect(Collectors.toList());
    }
}
