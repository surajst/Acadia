package com.schoolos.management;

import com.schoolos.academics.StudentMetric;
import com.schoolos.academics.StudentMetricRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StudentProgressService {

    private final StudentProgressRepository studentProgressRepository;
    private final CurriculumRepository curriculumRepository;
    private final StudentRepository studentRepository;
    private final StudentMetricRepository studentMetricRepository;

    public StudentProgressService(StudentProgressRepository studentProgressRepository,
                                   CurriculumRepository curriculumRepository,
                                   StudentRepository studentRepository,
                                   StudentMetricRepository studentMetricRepository) {
        this.studentProgressRepository = studentProgressRepository;
        this.curriculumRepository = curriculumRepository;
        this.studentRepository = studentRepository;
        this.studentMetricRepository = studentMetricRepository;
    }

    /**
     * Returns all curriculum topics for a student grouped by subject,
     * with completion status merged from student_progress records.
     */
    public Map<String, SubjectProgressDto> getProgressByStudent(UUID studentId) {
        // Load all existing progress records for this student
        List<StudentProgress> progressRecords = studentProgressRepository.findByStudentId(studentId);
        Map<UUID, StudentProgress> progressByTopic = progressRecords.stream()
                .collect(Collectors.toMap(
                        p -> p.getCurriculum().getId(),
                        p -> p,
                        (a, b) -> a
                ));

        // Load all curriculum topics and group by subject
        List<Curriculum> allTopics = curriculumRepository.findAll();

        Map<String, List<TopicDto>> grouped = new LinkedHashMap<>();
        for (Curriculum topic : allTopics) {
            String subject = topic.getSubjectCode();
            grouped.computeIfAbsent(subject, k -> new ArrayList<>());

            StudentProgress progress = progressByTopic.get(topic.getId());
            boolean completed = (progress != null && progress.isCompleted());
            LocalDateTime completedAt = (progress != null) ? progress.getCompletedAt() : null;
            String status = (progress != null) ? progress.getStatus() : null;
            String rejectionReason = (progress != null) ? progress.getRejectionReason() : null;

            grouped.get(subject).add(new TopicDto(
                    topic.getId(),
                    topic.getTopicName(),
                    subject,
                    topic.getStandard(),
                    topic.getXpReward(),
                    topic.getTopicOrder(),
                    completed,
                    completedAt,
                    status,
                    rejectionReason
            ));
        }

        // Sort each subject's topics by topicOrder
        grouped.forEach((subject, topics) ->
                topics.sort(Comparator.comparingInt(TopicDto::topicOrder)));

        // Build final response shape
        Map<String, SubjectProgressDto> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<TopicDto>> entry : grouped.entrySet()) {
            long completedCount = entry.getValue().stream().filter(TopicDto::completed).count();
            result.put(entry.getKey(), new SubjectProgressDto(
                    (int) completedCount,
                    entry.getValue().size(),
                    entry.getValue()
            ));
        }
        return result;
    }

    /**
     * Marks a specific curriculum topic as completed for a student,
     * awards XP, and returns the updated progress map.
     */
    @Transactional
    public Map<String, SubjectProgressDto> markTopicComplete(UUID studentId, UUID curriculumId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found: " + studentId));

        Curriculum curriculum = curriculumRepository.findById(curriculumId)
                .orElseThrow(() -> new IllegalArgumentException("Curriculum topic not found: " + curriculumId));

        // Find existing progress record or create a new one
        List<StudentProgress> existingList = studentProgressRepository.findByStudentId(studentId);
        StudentProgress progress = existingList.stream()
                .filter(p -> p.getCurriculum().getId().equals(curriculumId))
                .findFirst()
                .orElse(null);

        if (progress == null) {
            progress = new StudentProgress();
            progress.setStudent(student);
            progress.setCurriculum(curriculum);
        }

        if (!"APPROVED".equals(progress.getStatus())) {
            progress.setStatus("PENDING");
            progress.setRejectionReason(null);
            studentProgressRepository.saveAndFlush(progress);
        }

        return getProgressByStudent(studentId);
    }
}
