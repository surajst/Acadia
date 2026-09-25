package com.concept.tasks.app;
import com.concept.tasks.data.TaskType;
import com.concept.tasks.data.TeacherTaskRequest;
import com.concept.tasks.data.TeacherTaskRepository;
import com.concept.tasks.data.TeacherTask;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
public class TeacherTaskService {

    @Autowired
    private TeacherTaskRepository teacherTaskRepository;

    public UUID resolveTeacherId(String username) {
        if (username == null) return UUID.fromString("11111111-1111-1111-1111-111111111111");
        return UUID.nameUUIDFromBytes(username.getBytes());
    }

    @Transactional
    public TeacherTask createTask(TeacherTaskRequest request, String teacherUsername, UUID tenantId, UUID academicYearId) {
        TeacherTask task = new TeacherTask();
        task.setId(UUID.randomUUID());
        task.setTenantId(tenantId);
        task.setAcademicYearId(academicYearId);
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setSubjectCode(request.getSubjectCode());
        task.setTaskType(request.getTaskType());
        task.setStandard(request.getStandard());

        Boolean isAssignedToClass = request.getAssignedToClass();
        if (isAssignedToClass == null) isAssignedToClass = true;
        task.setAssignedToClass(isAssignedToClass);

        // Which section it was set for. Null on a personal task, and null on
        // every task raised before the column existed -- where it still means
        // grade-wide, see TeacherTask.classSectionId.
        task.setClassSectionId(request.getClassSectionId());
        task.setStudentId(request.getStudentId());
        task.setCreatedByTeacherId(resolveTeacherId(teacherUsername));
        task.setXpReward(request.getXpReward() != null ? request.getXpReward() : 50);
        task.setDueDate(request.getDueDate());

        if (TaskType.READING.equals(request.getTaskType())) {
            task.setQuestion1(request.getQuestion1());
            task.setQuestion2(request.getQuestion2());
            task.setQuestion3(request.getQuestion3());
        }

        return teacherTaskRepository.save(task);
    }

    /**
     * @deprecated a student's grade is not narrow enough to decide which class
     *     tasks are theirs -- it returns every section's. Kept only for a
     *     student who is in no section. Use
     *     {@link #getTasksForStudent(UUID, int, UUID, UUID)}.
     */
    @Deprecated
    @Transactional
    public List<TeacherTask> getTasksForStudent(UUID studentId, int standard, UUID tenantId) {
        return getTasksForStudent(studentId, standard, null, tenantId);
    }

    /**
     * A student's task list, scoped to their own section.
     *
     * <p>This used to ask for every class task in the grade, so a task Priya set
     * for 6-A appeared on every 6-B child's list -- they could hand in work
     * their teacher never set them, and the teacher saw submissions from
     * children they do not teach.
     *
     * <p>A task with no section recorded still reaches the whole grade. Those are
     * the rows raised before the column existed, and nothing can recover which
     * section was meant, so they keep the reach they have always had rather than
     * disappearing from lists children are working from.
     *
     * @param sectionId the student's section, or null when they are in none, in
     *                  which case there is nothing to match on and the grade is
     *                  the only answer available
     */
    @Transactional
    public List<TeacherTask> getTasksForStudent(UUID studentId, int standard, UUID sectionId, UUID tenantId) {
        List<TeacherTask> classTasks = sectionId != null
                ? teacherTaskRepository.findClassTasksForSection(standard, sectionId, tenantId)
                : teacherTaskRepository.findByStandardAndAssignedToClassTrueAndTenantId(standard, tenantId);
        List<TeacherTask> studentTasks = teacherTaskRepository.findByStudentIdAndTenantId(studentId, tenantId);

        Set<TeacherTask> allTasks = new HashSet<>(classTasks);
        allTasks.addAll(studentTasks);
        
        LocalDate today = LocalDate.now();
        List<TeacherTask> activeTasks = new ArrayList<>();
        
        for (TeacherTask task : allTasks) {
            if ("ACTIVE".equals(task.getTaskStatus()) && task.getDueDate() != null && task.getDueDate().isBefore(today)) {
                task.setTaskStatus("OVERDUE");
                teacherTaskRepository.save(task);
            }
            if ("ACTIVE".equals(task.getTaskStatus()) || "OVERDUE".equals(task.getTaskStatus())) {
                activeTasks.add(task);
            }
        }
        
        // Sort by due date (nulls last) and then by creation date desc
        activeTasks.sort((t1, t2) -> {
            if (t1.getDueDate() == null && t2.getDueDate() == null) {
                return t2.getCreatedAt().compareTo(t1.getCreatedAt());
            }
            if (t1.getDueDate() == null) return 1;
            if (t2.getDueDate() == null) return -1;
            return t1.getDueDate().compareTo(t2.getDueDate());
        });
        
        return activeTasks;
    }

    public List<TeacherTask> getTasksCreatedByTeacher(String teacherUsername, UUID tenantId) {
        return teacherTaskRepository.findByCreatedByTeacherIdAndTenantId(resolveTeacherId(teacherUsername), tenantId);
    }

    public Map<String, String> getQuestionsForTask(UUID taskId, UUID tenantId) {
        TeacherTask task = teacherTaskRepository.findByIdAndTenantId(taskId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        
        Map<String, String> questions = new HashMap<>();
        if (TaskType.READING.equals(task.getTaskType())) {
            if (task.getQuestion1() != null && !task.getQuestion1().isEmpty()) questions.put("question1", task.getQuestion1());
            if (task.getQuestion2() != null && !task.getQuestion2().isEmpty()) questions.put("question2", task.getQuestion2());
            if (task.getQuestion3() != null && !task.getQuestion3().isEmpty()) questions.put("question3", task.getQuestion3());
        }
        return questions;
    }
}
