package com.schoolos.management;

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

    @Transactional
    public List<TeacherTask> getTasksForStudent(UUID studentId, int standard, UUID tenantId) {
        List<TeacherTask> classTasks = teacherTaskRepository.findByStandardAndAssignedToClassTrueAndTenantId(standard, tenantId);
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

    public Map<String, String> getQuestionsForTask(UUID taskId) {
        TeacherTask task = teacherTaskRepository.findById(taskId)
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
