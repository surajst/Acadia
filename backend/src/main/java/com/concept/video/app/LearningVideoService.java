package com.concept.video.app;

import com.concept.assignment.app.TeachingScope;
import com.concept.notification.app.NotificationPublisher;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.ClassSectionRepository;
import com.concept.shared.data.Parent;
import com.concept.shared.data.Student;
import com.concept.shared.data.StudentRepository;
import com.concept.user.CurrentUserService;
import com.concept.user.User;
import com.concept.user.UserRole;
import com.concept.video.data.LearningVideo;
import com.concept.video.data.LearningVideoRepository;
import com.concept.video.data.VideoView;
import com.concept.video.data.VideoViewRepository;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Learning videos: a teacher points one class at a YouTube video.
 *
 * <p>Who may post is the same question tasks already answer, and it is answered by
 * the same object -- {@link TeachingScope}. A second copy of an authorisation rule
 * is how this codebase has been caught before, so there isn't one.
 *
 * <p>Who may watch is settled by the section. A pupil sees their own section's
 * videos; a parent sees their own children's. Neither is trusted to say which
 * section that is -- it is read from their record, because a section id in a
 * request is not a permission.
 */
@Service
public class LearningVideoService {

    /** One row as any of the three audiences needs it. */
    public record VideoView_(UUID id,
                             String youtubeId,
                             String embedUrl,
                             String title,
                             String thumbnailUrl,
                             String note,
                             String subjectCode,
                             UUID sectionId,
                             String sectionName,
                             String postedBy,
                             java.time.LocalDateTime postedAt,
                             boolean watched,
                             long watchedCount) {}

    private final LearningVideoRepository videoRepository;
    private final VideoViewRepository viewRepository;
    private final ClassSectionRepository classSectionRepository;
    private final StudentRepository studentRepository;
    private final CurrentUserService currentUserService;
    private final TeachingScope teachingScope;
    private final VideoLookup videoLookup;
    private final NotificationPublisher notificationPublisher;

    public LearningVideoService(LearningVideoRepository videoRepository,
                                VideoViewRepository viewRepository,
                                ClassSectionRepository classSectionRepository,
                                StudentRepository studentRepository,
                                CurrentUserService currentUserService,
                                TeachingScope teachingScope,
                                VideoLookup videoLookup,
                                NotificationPublisher notificationPublisher) {
        this.videoRepository = videoRepository;
        this.viewRepository = viewRepository;
        this.classSectionRepository = classSectionRepository;
        this.studentRepository = studentRepository;
        this.currentUserService = currentUserService;
        this.teachingScope = teachingScope;
        this.videoLookup = videoLookup;
        this.notificationPublisher = notificationPublisher;
    }

    // ── Posting ──────────────────────────────────────────────────────────────

    /**
     * Add a video for one class.
     *
     * <p>Order matters here. The link is parsed before anything else, because a
     * playlist should be refused without a network call; the teaching scope is
     * checked before the lookup, so somebody poking at another class's section id
     * cannot use this endpoint to probe which YouTube videos exist.
     */
    @Transactional
    public VideoView_ post(String url, UUID sectionId, String subjectCode, String note,
                           Authentication authentication) {
        String youtubeId;
        try {
            youtubeId = YouTubeUrls.videoId(url);
        } catch (YouTubeUrls.NotAVideo e) {
            throw VideoException.badRequest(e.getMessage());
        }

        User caller = currentUserService.getCurrentUser(authentication)
                .orElseThrow(() -> VideoException.forbidden("Sign in to add a video."));
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        UUID yearId = currentUserService.getCurrentAcademicYearId(authentication).orElse(null);
        if (tenantId == null) {
            throw VideoException.forbidden("Sign in to add a video.");
        }

        if (sectionId == null) {
            throw VideoException.badRequest("Choose which class this video is for.");
        }
        ClassSection section = classSectionRepository.findByIdAndTenantId(sectionId, tenantId)
                .orElseThrow(() -> VideoException.badRequest("That class was not found."));

        if (!teachingScope.isUnrestricted(caller) && !teachingScope.teachesSection(caller, section)) {
            throw VideoException.forbidden("You are not assigned to "
                    + section.getGradeName() + " - " + section.getSectionName()
                    + ", so you cannot add a video for it.");
        }
        if (subjectCode == null || subjectCode.isBlank()) {
            throw VideoException.badRequest("Choose a subject for this video.");
        }
        String refused = teachingScope.subjectTheyDoNotTeach(caller, section, subjectCode, tenantId);
        if (refused != null) {
            throw VideoException.badRequest("You're not assigned to teach " + refused + ".");
        }

        if (videoRepository.countLiveForSectionAndVideo(tenantId, sectionId, youtubeId) > 0) {
            // A double-click, not an intention -- and posting it twice would notify
            // every child in the class a second time.
            throw VideoException.conflict("That video is already on this class's list.");
        }

        // Last, because it leaves the building. By now we know the caller is
        // entitled to ask.
        VideoDetails details;
        try {
            details = videoLookup.describe(youtubeId);
        } catch (VideoLookup.Unplayable e) {
            throw VideoException.badRequest(e.getMessage());
        }

        LearningVideo video = new LearningVideo();
        video.setId(UUID.randomUUID());
        video.setTenantId(tenantId);
        video.setAcademicYearId(yearId);
        video.setSectionId(sectionId);
        video.setSubjectCode(subjectCode);
        video.setYoutubeId(youtubeId);
        video.setTitle(details.title());
        video.setNote(note == null || note.isBlank() ? null : note.trim());
        video.setCreatedByUserId(caller.getId());
        video.setCreatedAt(LocalDateTime.now());
        videoRepository.save(video);

        notificationPublisher.videoPosted(pupilsIn(sectionId, tenantId), tenantId, yearId,
                video.getId(), video.getTitle(), video.getSubjectCode());

        return describe(video, section, caller.getFullName(), false);
    }

    // ── Removing ─────────────────────────────────────────────────────────────

    /**
     * Take a video off a class's list.
     *
     * <p>Whoever posted it, or any admin or principal -- somebody has to be able to
     * clear up after a teacher who has left, which is the same reasoning the task
     * rules already carry.
     *
     * <p>Marked removed, not deleted: rows in video_views point at it, and taking a
     * video off a list should not take the record of who watched it. Its
     * notifications are retired at the same time, because a notification about
     * something that is gone is the fault R3-P1-3 was about.
     */
    @Transactional
    public Map<String, Object> remove(UUID videoId, Authentication authentication) {
        User caller = currentUserService.getCurrentUser(authentication)
                .orElseThrow(() -> VideoException.forbidden("Sign in first."));
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        LearningVideo video = videoId == null || tenantId == null ? null
                : videoRepository.findByIdAndTenantId(videoId, tenantId).orElse(null);
        if (video == null || video.isRemoved()) {
            throw VideoException.notFound("That video was not found.");
        }

        boolean mine = caller.getId().equals(video.getCreatedByUserId());
        if (!mine && !teachingScope.isUnrestricted(caller)) {
            throw VideoException.forbidden(
                    "That video was added by another teacher, so it is not yours to remove.");
        }

        video.setRemovedAt(LocalDateTime.now());
        videoRepository.save(video);
        notificationPublisher.settledForEveryone(
                NotificationPublisher.TYPE_VIDEO, video.getId(), tenantId);

        return Map.of("status", "removed", "id", video.getId());
    }

    // ── Reading ──────────────────────────────────────────────────────────────

    /** What this teacher has posted. An admin or principal sees the school's. */
    @Transactional(readOnly = true)
    public List<VideoView_> mine(Authentication authentication) {
        User caller = currentUserService.getCurrentUser(authentication)
                .orElseThrow(() -> VideoException.forbidden("Sign in first."));
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        if (tenantId == null) {
            return List.of();
        }
        List<LearningVideo> videos = teachingScope.isUnrestricted(caller)
                ? videoRepository.liveForTenant(tenantId)
                : videoRepository.livePostedBy(tenantId, caller.getId());
        return videos.stream().map(v -> describe(v, sectionOf(v, tenantId), null,
                false, viewRepository.countByVideoId(v.getId()))).collect(Collectors.toList());
    }

    /**
     * What this pupil can watch, newest first, with what they have finished.
     *
     * <p>The section comes from their own record. A pupil asking for another
     * section's list has nothing to ask with.
     */
    @Transactional(readOnly = true)
    public List<VideoView_> forStudent(String subjectCode, Authentication authentication) {
        Student student = currentUserService.getCurrentStudent(authentication)
                .orElseThrow(() -> VideoException.forbidden("No pupil record for this account."));
        return listFor(student, subjectCode);
    }

    /**
     * What one child can watch, for their parent.
     *
     * @param studentId which child; refused unless this parent is linked to them
     */
    @Transactional(readOnly = true)
    public List<VideoView_> forParent(UUID studentId, String subjectCode,
                                      Authentication authentication) {
        Parent parent = currentUserService.getCurrentParent(authentication)
                .orElseThrow(() -> VideoException.forbidden("No guardian record for this account."));
        List<Student> children = studentRepository.findByParentsContaining(parent);
        if (children.isEmpty()) {
            return List.of();
        }
        Student child = studentId == null
                ? children.get(0)
                : children.stream().filter(s -> studentId.equals(s.getId())).findFirst()
                        // Not "not found": a parent asking about a child who is not
                        // theirs is being refused, and saying so plainly is better
                        // than pretending the child does not exist.
                        .orElseThrow(() -> VideoException.forbidden("That is not your child."));
        return listFor(child, subjectCode);
    }

    private List<VideoView_> listFor(Student student, String subjectCode) {
        if (student.getClassSection() == null) {
            return List.of();
        }
        List<LearningVideo> videos = videoRepository.liveForSection(
                student.getTenantId(), student.getClassSection().getId());
        if (subjectCode != null && !subjectCode.isBlank()) {
            videos = videos.stream()
                    .filter(v -> TeachingScope.sameSubject(v.getSubjectCode(), subjectCode))
                    .collect(Collectors.toList());
        }
        if (videos.isEmpty()) {
            return List.of();
        }
        // One query for the lot rather than one per video.
        Set<UUID> watched = new HashSet<>(viewRepository.watchedAmong(student.getId(),
                videos.stream().map(LearningVideo::getId).collect(Collectors.toList())));
        ClassSection section = student.getClassSection();
        return videos.stream()
                .map(v -> describe(v, section, null, watched.contains(v.getId())))
                .collect(Collectors.toList());
    }

    // ── Watching ─────────────────────────────────────────────────────────────

    /**
     * This pupil finished this video.
     *
     * <p>Idempotent, and by the database rather than by the client's good manners:
     * the player fires ENDED every time it reaches the end, which for a child who
     * rewinds is more than once.
     */
    @Transactional
    public Map<String, Object> markWatched(UUID videoId, Authentication authentication) {
        Student student = currentUserService.getCurrentStudent(authentication)
                .orElseThrow(() -> VideoException.forbidden("No pupil record for this account."));
        LearningVideo video = videoId == null ? null
                : videoRepository.findByIdAndTenantId(videoId, student.getTenantId()).orElse(null);
        if (video == null || video.isRemoved()) {
            throw VideoException.notFound("That video was not found.");
        }
        // Their own section's, or it is not theirs to have watched.
        if (student.getClassSection() == null
                || !video.getSectionId().equals(student.getClassSection().getId())) {
            throw VideoException.forbidden("That video is not on your list.");
        }

        VideoView existing = viewRepository
                .findByVideoIdAndStudentId(video.getId(), student.getId()).orElse(null);
        if (existing != null) {
            return Map.of("status", "watched", "id", video.getId(), "first", false);
        }
        VideoView view = new VideoView();
        view.setId(UUID.randomUUID());
        view.setVideoId(video.getId());
        view.setStudentId(student.getId());
        view.setCompletedAt(LocalDateTime.now());
        viewRepository.save(view);

        // The video is watched, so the home screen should stop saying it is
        // waiting -- the same reasoning as handing a task in.
        notificationPublisher.settledFor(student.getUserId(),
                NotificationPublisher.TYPE_VIDEO, video.getId());

        return Map.of("status", "watched", "id", video.getId(), "first", true);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private List<Student> pupilsIn(UUID sectionId, UUID tenantId) {
        List<Student> in = new ArrayList<>();
        for (Student s : studentRepository.findByTenantId(tenantId)) {
            if (s.getClassSection() != null && sectionId.equals(s.getClassSection().getId())) {
                in.add(s);
            }
        }
        return in;
    }

    private ClassSection sectionOf(LearningVideo video, UUID tenantId) {
        return classSectionRepository.findByIdAndTenantId(video.getSectionId(), tenantId).orElse(null);
    }

    private VideoView_ describe(LearningVideo v, ClassSection section, String postedBy,
                                boolean watched) {
        return describe(v, section, postedBy, watched, 0);
    }

    private VideoView_ describe(LearningVideo v, ClassSection section, String postedBy,
                                boolean watched, long watchedCount) {
        String sectionName = section == null ? null
                : section.getGradeName() + " - " + section.getSectionName();
        return new VideoView_(v.getId(), v.getYoutubeId(), YouTubeUrls.embedUrl(v.getYoutubeId()),
                v.getTitle(), YouTubeUrls.thumbnailUrl(v.getYoutubeId()), v.getNote(), v.getSubjectCode(),
                v.getSectionId(), sectionName, postedBy, v.getCreatedAt(), watched, watchedCount);
    }

    /** Whether this caller may post at all, for hiding a control they cannot use. */
    public boolean canPost(Authentication authentication) {
        User caller = currentUserService.getCurrentUser(authentication).orElse(null);
        return caller != null && caller.getRole() != null
                && (caller.getRole() == UserRole.TEACHER || teachingScope.isUnrestricted(caller));
    }
}
