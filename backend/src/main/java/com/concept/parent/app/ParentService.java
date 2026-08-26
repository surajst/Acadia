package com.concept.parent.app;

import com.concept.academics.data.StudentMetric;
import com.concept.academics.data.StudentMetricRepository;
import com.concept.announcement.Announcement;
import com.concept.announcement.AnnouncementRepository;
import com.concept.language.SpeechService;
import com.concept.language.SupportedLanguages;
import com.concept.language.TranslationService;
import com.concept.shared.data.AcademicSubmission;
import com.concept.shared.data.AcademicSubmissionRepository;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.Parent;
import com.concept.parent.data.ParentQuest;
import com.concept.parent.data.ParentQuestRepository;
import com.concept.parent.data.ParentReward;
import com.concept.parent.data.ParentRewardRepository;
import com.concept.shared.data.ParentRepository;
import com.concept.shared.data.Student;
import com.concept.oversight.app.StudentProgressService;
import com.concept.shared.data.StudentRepository;
import com.concept.parentapp.AttendanceRecord;
import com.concept.parentapp.DateRange;
import com.concept.parentapp.SisDataProvider;
import com.concept.parentapp.StudentSummary;
import com.concept.transport.BusRoute;
import com.concept.transport.BusRouteRepository;
import com.concept.user.CurrentUserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application layer for the parent-facing surface (mobile app + web portal).
 * Owns every decision — parent resolution, child-ownership checks, XP math,
 * translation/TTS, and persistence — so the web controllers only bind requests
 * and shape responses (ADR 0001).
 *
 * <p>Every child a request touches is resolved through the caller's own linked
 * children, so a parent can never read or mutate another family's data by
 * passing a different id — the recurring cross-tenant/cross-family IDOR class
 * is handled here, once.
 */
@Service
public class ParentService {

    private final StudentRepository studentRepository;
    private final StudentMetricRepository studentMetricRepository;
    private final AcademicSubmissionRepository academicSubmissionRepository;
    private final ParentRewardRepository parentRewardRepository;
    private final ParentQuestRepository parentQuestRepository;
    private final ParentRepository parentRepository;
    private final AnnouncementRepository announcementRepository;
    private final BusRouteRepository busRouteRepository;
    private final StudentProgressService studentProgressService;
    private final SisDataProvider sisDataProvider;
    private final TranslationService translationService;
    private final SpeechService speechService;
    private final CurrentUserService currentUserService;

    public ParentService(StudentRepository studentRepository,
                         StudentMetricRepository studentMetricRepository,
                         AcademicSubmissionRepository academicSubmissionRepository,
                         ParentRewardRepository parentRewardRepository,
                         ParentQuestRepository parentQuestRepository,
                         ParentRepository parentRepository,
                         AnnouncementRepository announcementRepository,
                         BusRouteRepository busRouteRepository,
                         StudentProgressService studentProgressService,
                         SisDataProvider sisDataProvider,
                         TranslationService translationService,
                         SpeechService speechService,
                         CurrentUserService currentUserService) {
        this.studentRepository = studentRepository;
        this.studentMetricRepository = studentMetricRepository;
        this.academicSubmissionRepository = academicSubmissionRepository;
        this.parentRewardRepository = parentRewardRepository;
        this.parentQuestRepository = parentQuestRepository;
        this.parentRepository = parentRepository;
        this.announcementRepository = announcementRepository;
        this.busRouteRepository = busRouteRepository;
        this.studentProgressService = studentProgressService;
        this.sisDataProvider = sisDataProvider;
        this.translationService = translationService;
        this.speechService = speechService;
        this.currentUserService = currentUserService;
    }

    // ─── Resolve helpers ────────────────────────────────────────────────────

    private Parent requireParent(Authentication authentication) {
        return currentUserService.getCurrentParent(authentication)
                .orElseThrow(() -> ParentException.badRequest("No parent record found"));
    }

    /**
     * Resolves the student a parent-scoped request should act on. A supplied
     * studentId must belong to one of this parent's own linked children —
     * otherwise falls back to the first linked child. Prevents reading another
     * family's data by passing a different studentId.
     */
    private UUID resolveStudentId(UUID studentId, Authentication authentication) {
        List<Student> children = currentUserService.getCurrentParent(authentication)
                .map(studentRepository::findByParentsContaining)
                .orElse(List.of());
        if (children.isEmpty()) return null;
        if (studentId != null && children.stream().anyMatch(s -> s.getId().equals(studentId))) {
            return studentId;
        }
        return children.get(0).getId();
    }

    private Student resolveChildForParent(Parent parent) {
        if (parent == null) return null;
        List<Student> linked = studentRepository.findByParentsContaining(parent);
        return linked.isEmpty() ? null : linked.get(0);
    }

    // ─── Mobile dashboard ───────────────────────────────────────────────────

    public Map<String, Object> mobileDashboard(UUID requestedStudentId, Authentication authentication) {
        Parent parent = currentUserService.getCurrentParent(authentication).orElse(null);
        if (parent == null) {
            throw ParentException.badRequest("No student found for this parent.");
        }

        List<Student> children = studentRepository.findByParentsContaining(parent);
        if (children.isEmpty()) {
            throw ParentException.badRequest("No student found for this parent.");
        }

        Student student = children.stream()
                .filter(s -> requestedStudentId != null && s.getId().equals(requestedStudentId))
                .findFirst()
                .orElse(children.get(0));

        UUID studentId = student.getId();

        StudentMetric studentMetrics = studentMetricRepository.findByStudentId(studentId).orElse(new StudentMetric());

        int totalXp = studentMetrics.getSchoolXp() != null ? studentMetrics.getSchoolXp() : 0;
        int scholarLevel = (totalXp / 500) + 1;
        int levelProgress = (totalXp % 500) * 100 / 500;
        int xpToNextLevel = 500 - (totalXp % 500);

        List<AcademicSubmission> submissions = academicSubmissionRepository.findByStudentId(studentId);
        List<ParentReward> pendingRewards = parentRewardRepository.findByStudentIdAndStatus(studentId, "PENDING");
        List<ParentQuest> parentQuests = parentQuestRepository.findByStudentId(studentId);
        List<ParentReward> parentRewards = parentRewardRepository.findByStudentId(studentId);

        List<AttendanceRecord> allAttendance = sisDataProvider.getAttendance(studentId,
                new DateRange(LocalDate.of(2000, 1, 1), LocalDate.now()));
        String attendanceStatus = allAttendance.isEmpty() ? "NOT MARKED" : allAttendance.get(0).status();

        Map<String, Object> response = new HashMap<>();

        Map<String, Object> parentInfo = new HashMap<>();
        parentInfo.put("id", parent.getId());
        parentInfo.put("firstName", parent.getFirstName());
        parentInfo.put("lastName", parent.getLastName());
        parentInfo.put("preferredLanguage", parent.getPreferredLanguage());
        response.put("parent", parentInfo);

        StudentSummary studentSummary = sisDataProvider.getStudent(studentId, student.getTenantId()).orElse(null);
        Map<String, Object> studentInfo = new HashMap<>();
        if (studentSummary != null) {
            studentInfo.put("id", studentSummary.id());
            studentInfo.put("firstName", studentSummary.firstName());
            studentInfo.put("lastName", studentSummary.lastName());
            if (studentSummary.gradeName() != null) {
                studentInfo.put("gradeName", studentSummary.gradeName());
                studentInfo.put("sectionName", studentSummary.sectionName());
            }
        }
        response.put("student", studentInfo);

        List<Map<String, Object>> childrenList = children.stream().map(s -> {
            Map<String, Object> c = new HashMap<>();
            c.put("id", s.getId());
            c.put("firstName", s.getFirstName());
            c.put("lastName", s.getLastName());
            if (s.getClassSection() != null) {
                c.put("gradeName", s.getClassSection().getGradeName());
                c.put("sectionName", s.getClassSection().getSectionName());
            }
            return c;
        }).collect(Collectors.toList());
        response.put("children", childrenList);

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("schoolXp", studentMetrics.getSchoolXp());
        metrics.put("parentXp", studentMetrics.getParentXp());
        metrics.put("activeStreak", studentMetrics.getActiveStreak());
        metrics.put("scholarLevel", scholarLevel);
        metrics.put("levelProgress", levelProgress);
        metrics.put("xpToNextLevel", xpToNextLevel);
        metrics.put("totalXp", totalXp);
        response.put("metrics", metrics);

        response.put("attendanceStatus", attendanceStatus);
        response.put("submissions", submissions);
        response.put("pendingRewards", pendingRewards);
        response.put("parentQuests", parentQuests);
        response.put("parentRewards", parentRewards);
        response.put("subjectPerformance", sisDataProvider.getSubjectPerformance(studentId));

        return response;
    }

    public Object subjectPerformance(UUID studentId, Authentication authentication) {
        UUID resolvedId = resolveStudentId(studentId, authentication);
        if (resolvedId == null) {
            throw ParentException.badRequest("No student found");
        }
        return sisDataProvider.getSubjectPerformance(resolvedId);
    }

    public List<Map<String, String>> attendanceLog(UUID studentId, Authentication authentication) {
        UUID resolvedId = resolveStudentId(studentId, authentication);
        if (resolvedId == null) {
            throw ParentException.badRequest("No student found");
        }
        return sisDataProvider.getAttendance(resolvedId, new DateRange(LocalDate.of(2000, 1, 1), LocalDate.now()))
                .stream()
                .sorted((a, b) -> a.date().compareTo(b.date()))
                .map(a -> {
                    Map<String, String> entry = new HashMap<>();
                    entry.put("date", a.date().toString());
                    entry.put("status", a.status());
                    return entry;
                })
                .collect(Collectors.toList());
    }

    public Map<String, Object> busLocation(UUID studentId, Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        UUID resolvedId = resolveStudentId(studentId, authentication);
        if (resolvedId == null) {
            throw ParentException.badRequest("No student found");
        }
        Student student = studentRepository.findByIdAndTenantId(resolvedId, tenantId).orElse(null);
        UUID busRouteId = (student != null && student.getClassSection() != null)
                ? student.getClassSection().getBusRouteId()
                : null;
        if (busRouteId == null) {
            return Map.of("assigned", false);
        }
        BusRoute route = busRouteRepository.findByIdAndTenantId(busRouteId, tenantId).orElse(null);
        if (route == null) {
            return Map.of("assigned", false);
        }
        Map<String, Object> response = new HashMap<>();
        response.put("assigned", true);
        response.put("routeName", route.getName());
        response.put("latitude", route.getCurrentLatitude());
        response.put("longitude", route.getCurrentLongitude());
        response.put("lastPingAt", route.getLastPingAt());
        return response;
    }

    // ─── Announcements + translation/TTS ────────────────────────────────────

    public List<Map<String, Object>> announcements(Authentication authentication) {
        Parent parent = currentUserService.getCurrentParent(authentication).orElse(null);
        if (parent == null) {
            return List.of();
        }
        List<Student> children = studentRepository.findByParentsContaining(parent);
        String targetGrade = children.stream()
                .map(Student::getClassSection)
                .filter(cs -> cs != null)
                .map(ClassSection::getGradeName)
                .findFirst()
                .orElse("ALL");

        List<Announcement> announcements = announcementRepository.findByTenantIdAndAcademicYearIdAndTargetGradeIn(
                parent.getTenantId(), parent.getAcademicYearId(), List.of(targetGrade, "ALL"));
        announcements.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));

        return announcements.stream().map(a -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", a.getId());
            row.put("title", a.getTitle());
            row.put("content", a.getContent());
            row.put("createdAt", a.getCreatedAt());
            return row;
        }).collect(Collectors.toList());
    }

    public Map<String, Object> announcementLocalized(UUID id, String lang, Authentication authentication) {
        Announcement announcement = resolveOwnAnnouncement(id, authentication);
        if (announcement == null) {
            throw ParentException.badRequest("Announcement not found");
        }
        if (!SupportedLanguages.isSupported(lang)) {
            throw ParentException.badRequest("Unsupported language");
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("title", translationService.translate(announcement.getTitle(), lang));
        row.put("content", translationService.translate(announcement.getContent(), lang));
        return row;
    }

    public Map<String, Object> announcementSpeech(UUID id, String lang, Authentication authentication) {
        Announcement announcement = resolveOwnAnnouncement(id, authentication);
        if (announcement == null) {
            throw ParentException.badRequest("Announcement not found");
        }
        if (!SupportedLanguages.isSupported(lang)) {
            throw ParentException.badRequest("Unsupported language");
        }
        String localizedText = translationService.translate(
                announcement.getTitle() + ". " + announcement.getContent(), lang);
        byte[] audio = speechService.synthesizeSpeech(localizedText, lang);
        return Map.of(
                "audioBase64", Base64.getEncoder().encodeToString(audio),
                "contentType", "audio/mpeg");
    }

    @Transactional
    public Map<String, Object> setPreferredLanguage(String language, Authentication authentication) {
        if (!SupportedLanguages.isSupported(language)) {
            throw ParentException.badRequest("Unsupported language");
        }
        Parent parent = currentUserService.getCurrentParent(authentication).orElse(null);
        if (parent == null) {
            throw ParentException.badRequest("No parent profile found");
        }
        parent.setPreferredLanguage(language);
        parentRepository.save(parent);
        return Map.of("language", language);
    }

    /** An announcement is only visible to a parent if it belongs to their own tenant. */
    private Announcement resolveOwnAnnouncement(UUID id, Authentication authentication) {
        Parent parent = currentUserService.getCurrentParent(authentication).orElse(null);
        if (parent == null) return null;
        Announcement announcement = announcementRepository.findByIdAndTenantId(id, parent.getTenantId()).orElse(null);
        if (announcement == null) return null;
        return announcement;
    }

    // ─── /api/parent JSON endpoints ─────────────────────────────────────────

    @Transactional
    public Map<String, Object> approveQuestApi(UUID id, Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        ParentQuest quest = parentQuestRepository.findByIdAndTenantId(id, tenantId).orElse(null);
        if (quest == null) {
            throw ParentException.badRequest("Quest not found");
        }
        Parent parent = currentUserService.getCurrentParent(authentication).orElse(null);
        if (parent == null || !quest.getStudent().getParents().contains(parent)) {
            throw ParentException.forbidden("Not authorized for this quest");
        }
        quest.setStatus("APPROVED");
        parentQuestRepository.save(quest);

        StudentMetric metric = studentMetricRepository.findByStudentId(quest.getStudent().getId()).orElseThrow();
        metric.setParentXp(metric.getParentXp() + quest.getXpBounty());
        studentMetricRepository.save(metric);

        return Map.of("status", "success");
    }

    @Transactional
    public Map<String, Object> assignQuestApi(AssignQuestRequest dto, Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        Student student = studentRepository.findByIdAndTenantId(dto.getStudentId(), tenantId).orElse(null);
        if (student == null) {
            throw ParentException.badRequest("Student not found");
        }
        Parent parent = currentUserService.getCurrentParent(authentication).orElse(null);
        if (parent == null) {
            throw ParentException.badRequest("Parent not found");
        }
        if (!student.getParents().contains(parent)) {
            throw ParentException.forbidden("Not authorized for this student");
        }

        ParentQuest quest = new ParentQuest();
        quest.setId(UUID.randomUUID());
        String taskDesc = dto.getTitle();
        if (dto.getDescription() != null && !dto.getDescription().trim().isEmpty()) {
            taskDesc += ": " + dto.getDescription();
        }
        quest.setTaskDescription(taskDesc);
        quest.setXpBounty(dto.getXpReward());
        quest.setStatus("PENDING");
        quest.setStudent(student);
        quest.setParent(parent);
        quest.setTenantId(student.getTenantId());
        quest.setAcademicYearId(student.getAcademicYearId());
        parentQuestRepository.save(quest);

        return Map.of("status", "success", "questId", quest.getId());
    }

    public List<Map<String, Object>> childAttendance(Authentication authentication) {
        Parent parent = currentUserService.getCurrentParent(authentication).orElse(null);
        Student child = resolveChildForParent(parent);
        if (child == null) {
            return List.of();
        }
        return sisDataProvider.getAttendance(child.getId(), DateRange.lastDays(60)).stream()
                .map(a -> Map.<String, Object>of(
                        "date", a.date().toString(),
                        "status", a.status(),
                        "dayOfWeek", a.date().getDayOfWeek().toString()))
                .collect(Collectors.toList());
    }

    public Object childSyllabus(Authentication authentication) {
        Parent parent = currentUserService.getCurrentParent(authentication).orElse(null);
        Student child = resolveChildForParent(parent);
        if (child == null) {
            return Map.of();
        }
        return studentProgressService.getProgressByStudent(child.getId(), child.getTenantId());
    }

    public Object childProgress(UUID studentId, Authentication authentication) {
        Parent parent = currentUserService.getCurrentParent(authentication).orElse(null);
        if (parent == null) {
            throw ParentException.forbidden("No parent record found");
        }
        List<Student> linkedStudents = studentRepository.findByParentsContaining(parent);
        Student targetStudent = linkedStudents.stream().filter(s -> s.getId().equals(studentId)).findFirst()
                .orElseThrow(() -> ParentException.forbidden("Not authorized to view this student's progress"));
        return studentProgressService.getProgressByStudent(studentId, targetStudent.getTenantId());
    }

    // ─── /web/parent form-post actions ──────────────────────────────────────

    @Transactional
    public void approveReward(UUID id, Authentication authentication) {
        ParentReward reward = requireOwnedReward(id, authentication);
        reward.setStatus("APPROVED");
        parentRewardRepository.saveAndFlush(reward);
    }

    @Transactional
    public void holdReward(UUID id, Authentication authentication) {
        ParentReward reward = requireOwnedReward(id, authentication);
        reward.setStatus("HELD");
        parentRewardRepository.saveAndFlush(reward);
    }

    @Transactional
    public void releaseReward(UUID id, Authentication authentication) {
        ParentReward reward = requireOwnedReward(id, authentication);
        reward.setStatus("DELIVERED");
        parentRewardRepository.saveAndFlush(reward);
    }

    @Transactional
    public void assignTask(UUID studentId, String taskDescription, Integer xpBounty, Authentication authentication) {
        Parent parent = requireParent(authentication);
        Student student = studentRepository.findByIdAndTenantId(studentId, parent.getTenantId())
                .orElseThrow(() -> ParentException.badRequest("Invalid student ID"));
        if (!student.getParents().contains(parent)) {
            throw ParentException.forbidden("Not authorized for this student");
        }
        ParentQuest quest = new ParentQuest();
        quest.setId(UUID.randomUUID());
        quest.setParent(parent);
        quest.setStudent(student);
        quest.setTaskDescription(taskDescription);
        quest.setXpBounty(xpBounty);
        quest.setStatus("PENDING");
        quest.setTenantId(parent.getTenantId());
        quest.setAcademicYearId(parent.getAcademicYearId());
        parentQuestRepository.saveAndFlush(quest);
    }

    @Transactional
    public void addReward(UUID studentId, String rewardTitle, Integer xpCost, Authentication authentication) {
        Parent parent = requireParent(authentication);
        Student student = studentRepository.findByIdAndTenantId(studentId, parent.getTenantId())
                .orElseThrow(() -> ParentException.badRequest("Invalid student ID"));
        if (!student.getParents().contains(parent)) {
            throw ParentException.forbidden("Not authorized for this student");
        }
        ParentReward reward = new ParentReward();
        reward.setId(UUID.randomUUID());
        reward.setParent(parent);
        reward.setStudent(student);
        reward.setRewardTitle(rewardTitle);
        reward.setXpCost(xpCost);
        reward.setStatus("AVAILABLE");
        reward.setTenantId(student.getTenantId());
        reward.setAcademicYearId(student.getAcademicYearId());
        parentRewardRepository.saveAndFlush(reward);
    }

    @Transactional
    public void approveQuestWeb(UUID id, Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        ParentQuest quest = parentQuestRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> ParentException.badRequest("Invalid parent quest ID: " + id));
        Parent parent = currentUserService.getCurrentParent(authentication).orElse(null);
        if (parent == null || !quest.getStudent().getParents().contains(parent)) {
            throw ParentException.forbidden("Not authorized for this quest");
        }
        quest.setStatus("APPROVED");
        parentQuestRepository.saveAndFlush(quest);

        UUID studentId = quest.getStudent().getId();
        StudentMetric metric = studentMetricRepository.findByStudentId(studentId).orElse(null);
        if (metric == null) {
            metric = new StudentMetric();
            metric.setId(UUID.randomUUID());
            metric.setStudent(quest.getStudent());
            metric.setTenantId(quest.getStudent().getTenantId());
            metric.setAcademicYearId(quest.getStudent().getAcademicYearId());
            metric.setSchoolXp(0);
            metric.setParentXp(0);
            metric.setActiveStreak(0);
        }
        int currentParentXp = metric.getParentXp() != null ? metric.getParentXp() : 0;
        metric.setParentXp(currentParentXp + quest.getXpBounty());
        studentMetricRepository.saveAndFlush(metric);
    }

    private ParentReward requireOwnedReward(UUID id, Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        ParentReward reward = parentRewardRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> ParentException.badRequest("Invalid parent reward ID: " + id));
        Parent parent = currentUserService.getCurrentParent(authentication).orElse(null);
        if (parent == null || !reward.getStudent().getParents().contains(parent)) {
            throw ParentException.forbidden("Not authorized for this reward");
        }
        return reward;
    }
}
