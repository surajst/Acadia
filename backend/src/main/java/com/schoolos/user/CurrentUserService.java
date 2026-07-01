package com.schoolos.user;

import com.schoolos.management.Parent;
import com.schoolos.management.ParentRepository;
import com.schoolos.management.Student;
import com.schoolos.management.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Single source of truth for "who is making this request" — replaces the
 * fuzzy firstName/email-prefix matching that used to be duplicated across
 * MobileParentRestController, ParentPortalApiController, ReportCardApiController,
 * TeacherTaskApiController, MobileStudentRestController, and StudentPortalController.
 *
 * Resolution is a direct lookup: Authentication -> User (by email, always set
 * as the JWT/session subject) -> Parent/Student (by userId FK). No fallback
 * heuristics — if a Parent/Student isn't linked via userId, that's a data
 * problem to fix at onboarding, not something to paper over at request time.
 */
@Service
public class CurrentUserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ParentRepository parentRepository;

    @Autowired
    private StudentRepository studentRepository;

    public Optional<User> getCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return Optional.empty();
        }
        return userRepository.findByEmail(authentication.getName());
    }

    public Optional<UUID> getCurrentTenantId(Authentication authentication) {
        return getCurrentUser(authentication).map(User::getTenantId);
    }

    public Optional<UUID> getCurrentAcademicYearId(Authentication authentication) {
        return getCurrentUser(authentication).map(User::getAcademicYearId);
    }

    public Optional<Parent> getCurrentParent(Authentication authentication) {
        return getCurrentUser(authentication).flatMap(user -> parentRepository.findByUserId(user.getId()));
    }

    public Optional<Student> getCurrentStudent(Authentication authentication) {
        return getCurrentUser(authentication).flatMap(user -> studentRepository.findByUserId(user.getId()));
    }
}
