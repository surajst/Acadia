package com.schoolos;

import com.schoolos.management.Parent;
import com.schoolos.management.ParentRepository;
import com.schoolos.management.Student;
import com.schoolos.management.StudentRepository;
import com.schoolos.management.TeacherTask;
import com.schoolos.management.TeacherTaskRepository;
import com.schoolos.tenant.Tenant;
import com.schoolos.tenant.TenantRepository;
import com.schoolos.user.User;
import com.schoolos.user.UserRepository;
import com.schoolos.user.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One-time backfill linking existing seeded PARENT/STUDENT login accounts to
 * their corresponding Parent/Student business-entity rows via userId, so
 * request-time resolution no longer needs fuzzy firstName/email matching.
 * Runs after all other seeders (so Users, Parents, Students all exist) and is
 * idempotent — skips any User already linked.
 */
@Component
@Order(4)
public class UserAccountLinkageSeeder implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ParentRepository parentRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private TeacherTaskRepository teacherTaskRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("\n--- STARTING USER ACCOUNT LINKAGE BACKFILL ---");

        List<User> parentUsers = userRepository.findAll().stream()
                .filter(u -> u.getRole() == UserRole.PARENT)
                .toList();
        for (User user : parentUsers) {
            parentRepository.findByUserId(user.getId()).ifPresentOrElse(
                    p -> { /* already linked */ },
                    () -> matchParentByFullName(parentRepository.findAll(), user).ifPresent(parent -> {
                        parent.setUserId(user.getId());
                        parentRepository.save(parent);
                        System.out.println(">> Linked PARENT user " + user.getEmail() + " -> Parent " + parent.getId());
                    })
            );
        }

        List<User> studentUsers = userRepository.findAll().stream()
                .filter(u -> u.getRole() == UserRole.STUDENT)
                .toList();
        for (User user : studentUsers) {
            studentRepository.findByUserId(user.getId()).ifPresentOrElse(
                    s -> { /* already linked */ },
                    () -> matchStudentByFullName(studentRepository.findAll(), user).ifPresent(student -> {
                        student.setUserId(user.getId());
                        studentRepository.save(student);
                        System.out.println(">> Linked STUDENT user " + user.getEmail() + " -> Student " + student.getId());
                    })
            );
        }

        backfillTeacherTaskTenant();

        System.out.println("--- USER ACCOUNT LINKAGE BACKFILL COMPLETED ---\n");
    }

    /**
     * TeacherTask didn't carry tenant_id/academic_year_id before this sprint.
     * Since this codebase only ever ran a single demo tenant, backfilling
     * every existing null-tenant row to "the" tenant is correct, not a guess.
     * New rows are tenant-scoped from creation going forward
     * (see TeacherTaskService.createTask).
     */
    private void backfillTeacherTaskTenant() {
        List<TeacherTask> untaggedTasks = teacherTaskRepository.findAll().stream()
                .filter(t -> t.getTenantId() == null)
                .toList();
        if (untaggedTasks.isEmpty()) return;

        List<Tenant> tenants = tenantRepository.findAll();
        if (tenants.size() != 1) {
            System.out.println(">> Skipping TeacherTask tenant backfill: expected exactly 1 tenant, found " + tenants.size());
            return;
        }
        Tenant onlyTenant = tenants.get(0);

        for (TeacherTask task : untaggedTasks) {
            task.setTenantId(onlyTenant.getId());
        }
        teacherTaskRepository.saveAll(untaggedTasks);
        System.out.println(">> Backfilled tenant_id for " + untaggedTasks.size() + " TeacherTask row(s)");
    }

    private java.util.Optional<Parent> matchParentByFullName(List<Parent> parents, User user) {
        return parents.stream()
                .filter(p -> fullNameMatches(p.getFirstName(), p.getLastName(), user.getFullName()))
                .findFirst();
    }

    private java.util.Optional<Student> matchStudentByFullName(List<Student> students, User user) {
        return students.stream()
                .filter(s -> fullNameMatches(s.getFirstName(), s.getLastName(), user.getFullName()))
                .findFirst();
    }

    private boolean fullNameMatches(String firstName, String lastName, String fullName) {
        if (firstName == null || fullName == null) return false;
        String candidate = (firstName + " " + (lastName != null ? lastName : "")).trim();
        return candidate.equalsIgnoreCase(fullName.trim());
    }
}
