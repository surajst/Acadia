package com.concept.assignment.app;

import com.concept.academics.data.Subject;
import com.concept.academics.data.SubjectRepository;
import com.concept.assignment.data.SubjectAssignment;
import com.concept.assignment.data.SubjectAssignmentRepository;
import com.concept.shared.data.ClassSection;
import com.concept.user.User;
import com.concept.user.UserRole;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.UUID;

/**
 * What a member of staff is allowed to put in front of a class.
 *
 * <p>Two questions, asked wherever anybody sets something for a section: may this
 * person address this class at all, and is this subject one they teach it. They
 * were answered privately inside {@code TasksService}, which was fine while tasks
 * were the only thing a teacher could set. Learning videos are the second, and a
 * second copy of an authorisation rule is how this codebase has been caught
 * before -- two writers, two meanings, and the difference only shows up as a
 * hole.
 *
 * <p>This returns findings rather than throwing. Each caller has its own exception
 * type and its own wording, and an authorisation helper that throws somebody
 * else's exception is awkward to reuse -- which is half of why the rule was
 * private in the first place.
 *
 * <p>Lives in the assignment slice because {@link SubjectAssignment} is the real
 * teacher-to-class link and this is a question about it.
 */
@Service
public class TeachingScope {

    private final SubjectAssignmentRepository subjectAssignmentRepository;
    private final SubjectRepository subjectRepository;

    public TeachingScope(SubjectAssignmentRepository subjectAssignmentRepository,
                         SubjectRepository subjectRepository) {
        this.subjectAssignmentRepository = subjectAssignmentRepository;
        this.subjectRepository = subjectRepository;
    }

    /**
     * Staff who may address any class, because they are assigned to none.
     *
     * <p>Somebody has to be able to set work for a teacher who has left, and an
     * admin has no subject assignments to check against.
     */
    public boolean isUnrestricted(User caller) {
        return caller != null && caller.getRole() != null
                && (caller.getRole() == UserRole.ADMIN || caller.getRole() == UserRole.PRINCIPAL);
    }

    /** Whether this teacher is assigned to that section at all, for any subject. */
    public boolean teachesSection(User caller, ClassSection section) {
        return caller != null && section != null
                && subjectAssignmentRepository.existsByTeacherAndClassSection(caller, section);
    }

    /**
     * The subject this caller may not use for that section, or null when there is
     * no objection.
     *
     * <p>The returned value is the subject's display name, for a refusal that names
     * what was refused. Null means "carry on", which covers three distinct cases
     * and they are worth keeping straight:
     *
     * <ul>
     *   <li>they do teach it;</li>
     *   <li>they are unrestricted staff;</li>
     *   <li>the code is not a subject this school offers under any spelling -- so
     *       nobody else's subject is being borrowed, and there is nothing to
     *       compare against. Refusing on a basis this cannot verify is how the rule
     *       would stop a teacher setting work at all.</li>
     * </ul>
     *
     * <p>That last leniency is narrow on purpose: "one this school offers" is
     * matched against both spellings of every catalogue subject, so English is the
     * same claim as ENGLISH. Otherwise the whole rule is one spelling away from
     * nothing.
     */
    public String subjectTheyDoNotTeach(User caller, ClassSection section,
                                        String subjectCode, UUID tenantId) {
        if (caller == null || section == null || tenantId == null
                || subjectCode == null || subjectCode.isBlank() || isUnrestricted(caller)) {
            return null;
        }

        Subject offered = subjectRepository.findByTenantIdOrderBySortOrderAsc(tenantId).stream()
                .filter(subject -> sameSubject(subject.getCode(), subjectCode)
                        || sameSubject(subject.getDisplayName(), subjectCode))
                .findFirst()
                .orElse(null);
        if (offered == null) {
            return null;
        }

        boolean theirs = subjectAssignmentRepository.findByTeacher(caller).stream()
                .filter(a -> a.getClassSection() != null
                        && section.getId().equals(a.getClassSection().getId()))
                .map(SubjectAssignment::getSubjectName)
                .anyMatch(name -> sameSubject(name, offered.getCode())
                        || sameSubject(name, offered.getDisplayName()));
        return theirs ? null : offered.getDisplayName();
    }

    /**
     * "Social Science" and SOCIAL_SCIENCE are one subject written two ways: a task
     * or a video carries a catalogue code, an assignment carries a display name.
     */
    public static boolean sameSubject(String left, String right) {
        return left != null && right != null && shape(left).equals(shape(right));
    }

    private static String shape(String value) {
        return value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
    }
}
