package com.concept.console.app;

import com.concept.console.data.ConsoleSchoolClassRepository;
import com.concept.console.data.ConsoleStaffRepository;
import com.concept.console.data.ConsoleStudentRepository;
import com.concept.rewards.app.RewardsService;
import com.concept.user.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application layer for the admin console hub. Aggregates the classroom list,
 * reward inventory, and headline counts into one flat {@link ConsoleView} so the
 * web layer only maps it onto the model (ADR 0001). Every read is tenant-scoped.
 */
@Service
public class ConsoleService {

    private final ConsoleSchoolClassRepository schoolClassRepository;
    private final ConsoleStudentRepository studentRepository;
    private final ConsoleStaffRepository staffRepository;
    private final RewardsService rewardsService;

    public ConsoleService(ConsoleSchoolClassRepository schoolClassRepository,
                          ConsoleStudentRepository studentRepository,
                          ConsoleStaffRepository staffRepository,
                          RewardsService rewardsService) {
        this.schoolClassRepository = schoolClassRepository;
        this.studentRepository = studentRepository;
        this.staffRepository = staffRepository;
        this.rewardsService = rewardsService;
    }

    @Transactional(readOnly = true)
    public ConsoleView getConsole(UUID tenantId) {
        if (tenantId == null) {
            return new ConsoleView(Collections.emptyList(), Collections.emptyList(), 0, 0, 0);
        }

        List<ClassroomView> classList = schoolClassRepository.findByTenantId(tenantId).stream()
                .map(c -> new ClassroomView(c.getId(), c.getGradeLevel(), c.getSectionName(), c.getRoomNumber(),
                        studentRepository.countBySchoolClassId(c.getId()), c.getTotalCapacity()))
                .collect(Collectors.toList());

        long totalStudents = studentRepository.countByTenantId(tenantId);
        long totalStaff = staffRepository.countByRoleAndTenantId(UserRole.ADMIN, tenantId)
                + staffRepository.countByRoleAndTenantId(UserRole.PRINCIPAL, tenantId)
                + staffRepository.countByRoleAndTenantId(UserRole.TEACHER, tenantId)
                + staffRepository.countByRoleAndTenantId(UserRole.DRIVER, tenantId);
        long totalClassrooms = schoolClassRepository.countByTenantId(tenantId);

        return new ConsoleView(classList, rewardsService.listInventory(tenantId),
                totalStudents, totalStaff, totalClassrooms);
    }
}
