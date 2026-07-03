package com.schoolos.management;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminProgressService {

    private final StudentRepository studentRepository;
    private final StudentProgressRepository studentProgressRepository;
    private final CurriculumRepository curriculumRepository;

    public AdminProgressService(StudentRepository studentRepository, 
                                StudentProgressRepository studentProgressRepository,
                                CurriculumRepository curriculumRepository) {
        this.studentRepository = studentRepository;
        this.studentProgressRepository = studentProgressRepository;
        this.curriculumRepository = curriculumRepository;
    }

    public Map<String, Object> getSchoolWideProgress(UUID tenantId) {
        List<Student> allStudents = tenantId != null ? studentRepository.findByTenantId(tenantId) : List.of();
        Set<UUID> tenantStudentIds = allStudents.stream().map(Student::getId).collect(Collectors.toSet());
        List<StudentProgress> allProgress = studentProgressRepository.findAll().stream()
                .filter(p -> p.getStudent() != null && tenantStudentIds.contains(p.getStudent().getId()))
                .collect(Collectors.toList());
        List<Curriculum> allCurriculum = tenantId != null ? curriculumRepository.findByTenantId(tenantId) : List.of();

        int totalStudents = allStudents.size();
        
        Map<Integer, List<Student>> studentsByStandard = allStudents.stream()
                .collect(Collectors.groupingBy(this::extractStandard));

        Map<Integer, List<Curriculum>> curriculumByStandard = allCurriculum.stream()
                .filter(c -> c.getStandard() != null)
                .collect(Collectors.groupingBy(Curriculum::getStandard));

        long totalExpectedProgress = 0;
        for (Map.Entry<Integer, List<Student>> entry : studentsByStandard.entrySet()) {
            int std = entry.getKey();
            int stdStudentsCount = entry.getValue().size();
            int stdCurriculumCount = curriculumByStandard.getOrDefault(std, Collections.emptyList()).size();
            totalExpectedProgress += (long) stdStudentsCount * stdCurriculumCount;
        }

        long totalCompleted = allProgress.stream().filter(StudentProgress::isCompleted).count();
        int overallCompletionPercent = totalExpectedProgress == 0 ? 0 : (int) Math.round((double) totalCompleted * 100 / totalExpectedProgress);

        Map<String, Object> byClass = new LinkedHashMap<>();
        for (int std = 5; std <= 10; std++) {
            List<Student> stdStudents = studentsByStandard.getOrDefault(std, Collections.emptyList());
            List<Curriculum> stdCurriculum = curriculumByStandard.getOrDefault(std, Collections.emptyList());
            Set<UUID> stdStudentIds = stdStudents.stream().map(Student::getId).collect(Collectors.toSet());
            
            List<StudentProgress> stdProgress = allProgress.stream()
                    .filter(p -> stdStudentIds.contains(p.getStudent().getId()))
                    .collect(Collectors.toList());
            
            int stdExpected = stdStudents.size() * stdCurriculum.size();
            long stdCompleted = stdProgress.stream().filter(StudentProgress::isCompleted).count();
            int stdCompletionPercent = stdExpected == 0 ? 0 : (int) Math.round((double) stdCompleted * 100 / stdExpected);

            Map<String, Object> bySubject = new LinkedHashMap<>();
            Map<String, List<Curriculum>> stdCurrBySubject = stdCurriculum.stream()
                    .collect(Collectors.groupingBy(c -> c.getSubjectCode()));
            
            for (Map.Entry<String, List<Curriculum>> subjEntry : stdCurrBySubject.entrySet()) {
                String subject = subjEntry.getKey();
                int subjExpected = stdStudents.size() * subjEntry.getValue().size();
                Set<UUID> subjCurriculumIds = subjEntry.getValue().stream().map(Curriculum::getId).collect(Collectors.toSet());
                long subjCompleted = stdProgress.stream()
                        .filter(p -> p.isCompleted() && subjCurriculumIds.contains(p.getCurriculum().getId()))
                        .count();
                int subjCompletionPercent = subjExpected == 0 ? 0 : (int) Math.round((double) subjCompleted * 100 / subjExpected);
                
                Map<String, Object> subjStats = new HashMap<>();
                subjStats.put("completionPercent", subjCompletionPercent);
                bySubject.put(subject, subjStats);
            }

            Map<String, Object> classInfo = new HashMap<>();
            classInfo.put("students", stdStudents.size());
            classInfo.put("completionPercent", stdCompletionPercent);
            classInfo.put("bySubject", bySubject);
            
            byClass.put(String.valueOf(std), classInfo);
        }

        List<Map<String, Object>> lowestChapters = allCurriculum.stream().map(c -> {
            int std = c.getStandard() != null ? c.getStandard() : -1;
            int stdStudentsCount = studentsByStandard.getOrDefault(std, Collections.emptyList()).size();
            long compCount = allProgress.stream()
                    .filter(p -> p.isCompleted() && p.getCurriculum().getId().equals(c.getId()))
                    .count();
            int compPct = stdStudentsCount == 0 ? 0 : (int) Math.round((double) compCount * 100 / stdStudentsCount);
            
            Map<String, Object> map = new HashMap<>();
            map.put("chapterId", c.getId());
            map.put("topicName", c.getTopicName());
            map.put("subject", c.getSubjectCode());
            map.put("standard", std);
            map.put("completionPercent", compPct);
            return map;
        })
        .sorted(Comparator.comparingInt(m -> (int) m.get("completionPercent")))
        .limit(5)
        .collect(Collectors.toList());

        Map<UUID, Integer> studentXp = new HashMap<>();
        for (StudentProgress p : allProgress) {
            if (p.isCompleted() && "APPROVED".equals(p.getStatus())) {
                studentXp.merge(p.getStudent().getId(), p.getCurriculum().getXpReward(), Integer::sum);
            }
        }
        
        List<Map<String, Object>> topStudents = studentXp.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(e -> {
                    Student s = studentRepository.findById(e.getKey()).orElse(null);
                    Map<String, Object> map = new HashMap<>();
                    map.put("studentId", e.getKey());
                    map.put("name", s != null ? s.getFirstName() + " " + s.getLastName() : "Unknown");
                    map.put("standard", s != null ? extractStandard(s) : -1);
                    map.put("xp", e.getValue());
                    return map;
                })
                .collect(Collectors.toList());
        
        long pendingVerifications = allProgress.stream()
                .filter(p -> "PENDING".equals(p.getStatus()))
                .count();

        int totalXpAwarded = studentXp.values().stream().mapToInt(Integer::intValue).sum();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalStudents", totalStudents);
        response.put("overallCompletionPercent", overallCompletionPercent);
        response.put("byClass", byClass);
        response.put("lowestCompletionChapters", lowestChapters);
        response.put("topStudents", topStudents);
        response.put("pendingVerifications", pendingVerifications);
        response.put("totalXpAwarded", totalXpAwarded);

        return response;
    }

    public Map<String, Object> getClassProgress(UUID tenantId, int standard) {
        List<Student> allStudents = tenantId != null ? studentRepository.findByTenantId(tenantId) : List.of();
        List<Student> classStudents = allStudents.stream()
                .filter(s -> extractStandard(s) == standard)
                .collect(Collectors.toList());

        List<Curriculum> tenantCurriculum = tenantId != null ? curriculumRepository.findByTenantId(tenantId) : List.of();
        List<Curriculum> classCurriculum = tenantCurriculum.stream()
                .filter(c -> c.getStandard() != null && c.getStandard() == standard)
                .collect(Collectors.toList());
                
        Set<UUID> classStudentIds = classStudents.stream().map(Student::getId).collect(Collectors.toSet());
        List<StudentProgress> classProgress = studentProgressRepository.findAll().stream()
                .filter(p -> classStudentIds.contains(p.getStudent().getId()))
                .collect(Collectors.toList());
                
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("standard", standard);
        response.put("totalStudents", classStudents.size());
        
        Map<String, Object> subjects = new LinkedHashMap<>();
        Map<String, List<Curriculum>> currBySubject = classCurriculum.stream()
                .collect(Collectors.groupingBy(c -> c.getSubjectCode()));
                
        for (Map.Entry<String, List<Curriculum>> entry : currBySubject.entrySet()) {
            String subject = entry.getKey();
            List<Curriculum> currs = entry.getValue();
            Set<UUID> currIds = currs.stream().map(Curriculum::getId).collect(Collectors.toSet());
            
            long completed = classProgress.stream()
                    .filter(p -> p.isCompleted() && currIds.contains(p.getCurriculum().getId()))
                    .count();
            int expected = classStudents.size() * currs.size();
            int pct = expected == 0 ? 0 : (int) Math.round((double) completed * 100 / expected);
            
            Map<String, Object> subjStats = new HashMap<>();
            subjStats.put("totalChapters", currs.size());
            subjStats.put("avgCompletionPercent", pct);
            subjects.put(subject, subjStats);
        }
        response.put("subjects", subjects);
        
        List<Map<String, Object>> chapters = new ArrayList<>();
        for (Curriculum c : classCurriculum) {
            long compCount = classProgress.stream()
                    .filter(p -> p.isCompleted() && p.getCurriculum().getId().equals(c.getId()))
                    .count();
            int pct = classStudents.isEmpty() ? 0 : (int) Math.round((double) compCount * 100 / classStudents.size());
            
            Map<String, Object> map = new HashMap<>();
            map.put("chapterId", c.getId());
            map.put("topicName", c.getTopicName());
            map.put("subject", c.getSubjectCode());
            map.put("completedByStudents", compCount);
            map.put("completionPercent", pct);
            chapters.add(map);
        }
        response.put("chapters", chapters);
        
        List<Map<String, Object>> studentList = new ArrayList<>();
        for (Student s : classStudents) {
            long compCount = classProgress.stream()
                    .filter(p -> p.isCompleted() && p.getStudent().getId().equals(s.getId()))
                    .count();
            int expected = classCurriculum.size();
            int pct = expected == 0 ? 0 : (int) Math.round((double) compCount * 100 / expected);
            
            Map<String, Object> map = new HashMap<>();
            map.put("studentId", s.getId());
            map.put("name", s.getFirstName() + " " + s.getLastName());
            map.put("completedCount", compCount);
            map.put("completionPercent", pct);
            studentList.add(map);
        }
        
        studentList.sort((a, b) -> Long.compare((long) b.get("completedCount"), (long) a.get("completedCount")));
        
        response.put("students", studentList);
        return response;
    }

    private int extractStandard(Student student) {
        if (student.getSchoolClass() != null && student.getSchoolClass().getGradeLevel() != null) {
            try {
                return Integer.parseInt(student.getSchoolClass().getGradeLevel().replaceAll("[^0-9]", ""));
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        if (student.getClassSection() != null && student.getClassSection().getGradeName() != null) {
            try {
                return Integer.parseInt(student.getClassSection().getGradeName().replaceAll("[^0-9]", ""));
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        return -1;
    }
}
