package com.concept.curriculum.app;

import com.concept.curriculum.data.Curriculum;
import com.concept.curriculum.data.CurriculumRepository;
import com.concept.curriculum.data.SyllabusType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Creating, editing and removing a school's curriculum topics.
 *
 * <p>Nothing in the application could write this table. The only code that ever
 * created a topic was {@code AcademicDataSeeder}, which is gated on
 * {@code app.dev-mode=true} and so never runs in production; the admin page was
 * read-only and the API exposed GET alone. Every real school's Syllabus tab was
 * therefore permanently empty, with no way for anyone to change that.
 *
 * <p>Topics are written as {@link SyllabusType#CBSE} and the syllabus is
 * deliberately not a parameter. {@code StudentService.mobileSyllabus} reads
 * CBSE and nothing else, and a tenant carries no syllabus of its own, so
 * offering a choice here would let an admin carefully enter a term of ICSE
 * topics that no pupil could ever see. Making the syllabus real means changing
 * the read path and giving a tenant a syllabus first; until then one value,
 * stated once, beats a dropdown that silently discards work.
 */
@Service
public class CurriculumAdminService {

    private final CurriculumRepository curriculumRepository;

    public CurriculumAdminService(CurriculumRepository curriculumRepository) {
        this.curriculumRepository = curriculumRepository;
    }

    /** The one syllabus the student-facing syllabus screen can read. */
    static final SyllabusType SUPPORTED_SYLLABUS = SyllabusType.CBSE;

    @Transactional
    public Map<String, Object> create(UUID tenantId, UUID academicYearId, String subjectCode,
                                      Integer standard, String topicName, Integer topicOrder,
                                      Integer xpReward) {
        requireTenant(tenantId);
        String subject = requireText(subjectCode, "A subject is required");
        String topic = requireText(topicName, "A topic name is required");
        requireStandard(standard);

        Curriculum row = new Curriculum();
        row.setId(UUID.randomUUID());
        row.setTenantId(tenantId);
        row.setAcademicYearId(academicYearId);
        row.setSyllabusType(SUPPORTED_SYLLABUS);
        row.setStandard(standard);
        row.setSubjectCode(subject);
        row.setTopicName(topic);
        // Appended to the end of its subject by default, so adding topics one at
        // a time produces the order they were entered in rather than a pile of
        // zeroes that sort arbitrarily.
        row.setTopicOrder(topicOrder != null ? topicOrder : nextOrder(tenantId, standard, subject));
        row.setXpReward(xpReward != null && xpReward >= 0 ? xpReward : 50);

        return view(curriculumRepository.save(row));
    }

    @Transactional
    public Map<String, Object> update(UUID id, UUID tenantId, String subjectCode, Integer standard,
                                      String topicName, Integer topicOrder, Integer xpReward) {
        requireTenant(tenantId);
        Curriculum row = curriculumRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> CurriculumException.notFound("Topic not found"));

        if (subjectCode != null && !subjectCode.isBlank()) row.setSubjectCode(subjectCode.trim());
        if (topicName != null && !topicName.isBlank()) row.setTopicName(topicName.trim());
        if (standard != null) {
            requireStandard(standard);
            row.setStandard(standard);
        }
        if (topicOrder != null) row.setTopicOrder(topicOrder);
        if (xpReward != null && xpReward >= 0) row.setXpReward(xpReward);

        return view(curriculumRepository.save(row));
    }

    @Transactional
    public void delete(UUID id, UUID tenantId) {
        requireTenant(tenantId);
        Curriculum row = curriculumRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> CurriculumException.notFound("Topic not found"));
        curriculumRepository.delete(row);
    }

    /** Every topic this school has, newest grade last, ordered within each subject. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(UUID tenantId, Integer standard) {
        requireTenant(tenantId);
        return curriculumRepository.findByTenantId(tenantId).stream()
                .filter(c -> standard == null || standard.equals(c.getStandard()))
                .sorted(Comparator.comparing(Curriculum::getStandard)
                        .thenComparing(Curriculum::getSubjectCode)
                        .thenComparing(Curriculum::getTopicOrder))
                .map(this::view)
                .collect(Collectors.toList());
    }

    private int nextOrder(UUID tenantId, Integer standard, String subjectCode) {
        return curriculumRepository
                .findByTenantIdAndSyllabusTypeAndStandardAndSubjectCodeOrderByTopicOrderAsc(
                        tenantId, SUPPORTED_SYLLABUS, standard, subjectCode)
                .stream()
                .map(Curriculum::getTopicOrder)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .map(max -> max + 1)
                .orElse(1);
    }

    private Map<String, Object> view(Curriculum c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("standard", c.getStandard());
        m.put("subjectCode", c.getSubjectCode());
        m.put("topicName", c.getTopicName());
        m.put("topicOrder", c.getTopicOrder());
        m.put("xpReward", c.getXpReward());
        return m;
    }

    private static void requireTenant(UUID tenantId) {
        if (tenantId == null) {
            throw CurriculumException.badRequest("Could not resolve your school");
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw CurriculumException.badRequest(message);
        }
        return value.trim();
    }

    private static void requireStandard(Integer standard) {
        if (standard == null || standard < 1 || standard > 12) {
            throw CurriculumException.badRequest("Grade must be between 1 and 12");
        }
    }
}
