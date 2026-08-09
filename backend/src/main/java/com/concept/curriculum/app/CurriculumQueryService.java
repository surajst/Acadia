package com.concept.curriculum.app;

import com.concept.management.CurriculumService;
import com.concept.management.SyllabusType;
import com.concept.user.CurrentUserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Application layer for the curriculum-topic catalog (ADR 0001). Resolves the
 * caller's tenant, binds the syllabus enum from the raw request string, and
 * delegates reads to the shared {@link CurriculumService}. Topic rows are
 * returned as {@code Object} so the interface layer never statically depends on
 * the JPA entity — the serialized JSON is identical.
 */
@Service
public class CurriculumQueryService {

    private final CurriculumService curriculumService;
    private final CurrentUserService currentUserService;

    public CurriculumQueryService(CurriculumService curriculumService,
                                  CurrentUserService currentUserService) {
        this.curriculumService = curriculumService;
        this.currentUserService = currentUserService;
    }

    public List<Object> topics(String syllabus, int standard, String subjectCode, Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        SyllabusType syllabusType = parseSyllabus(syllabus);
        return List.copyOf(curriculumService.getTopics(tenantId, syllabusType, standard, subjectCode));
    }

    public List<String> subjects(String syllabus, int standard, Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        SyllabusType syllabusType = parseSyllabus(syllabus);
        return curriculumService.getSubjects(tenantId, syllabusType, standard);
    }

    private SyllabusType parseSyllabus(String syllabus) {
        try {
            return SyllabusType.valueOf(syllabus);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw CurriculumException.badRequest("Invalid syllabus");
        }
    }
}
