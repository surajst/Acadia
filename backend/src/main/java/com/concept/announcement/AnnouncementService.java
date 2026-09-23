package com.concept.announcement;

import com.concept.common.GradeLevel;
import com.concept.common.NotificationDeliveryService;
import com.concept.notification.app.NotificationPublisher;
import com.concept.shared.data.Parent;
import com.concept.shared.data.Student;
import com.concept.shared.data.StudentRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final NotificationDeliveryService notificationDeliveryService;
    private final NotificationPublisher notificationPublisher;
    private final StudentRepository studentRepository;

    public AnnouncementService(AnnouncementRepository announcementRepository,
                                NotificationDeliveryService notificationDeliveryService,
                                NotificationPublisher notificationPublisher,
                                StudentRepository studentRepository) {
        this.announcementRepository = announcementRepository;
        this.notificationDeliveryService = notificationDeliveryService;
        this.notificationPublisher = notificationPublisher;
        this.studentRepository = studentRepository;
    }

    public Announcement createAnnouncement(Announcement announcement) {
        if (announcement.getId() == null) {
            announcement.setId(UUID.randomUUID());
        }
        if (announcement.getCreatedAt() == null) {
            announcement.setCreatedAt(LocalDateTime.now());
        }
        
        Announcement saved = announcementRepository.save(announcement);

        notificationDeliveryService.send(saved.getTargetGrade(),
                "[MOCK WHATSAPP DISPATCH] Sending to " + saved.getTargetGrade() + ": " + saved.getTitle());

        notifyAudience(saved);

        return saved;
    }

    /**
     * Put the announcement in the app, for the pupils it is addressed to and
     * their guardians.
     *
     * <p>Posting school news reached WhatsApp (in mock) and nothing else, so
     * neither a child nor a parent saw anything in the product itself until
     * they went looking for the news screen.
     *
     * <p>"ALL" means the whole school; any other value is matched by grade the
     * same way the parent-facing read above matches it.
     */
    private void notifyAudience(Announcement saved) {
        String target = saved.getTargetGrade();
        boolean everyone = target == null || target.isBlank() || "ALL".equalsIgnoreCase(target);
        int wanted = everyone ? GradeLevel.UNKNOWN : GradeLevel.parse(target);

        List<UUID> students = new ArrayList<>();
        List<UUID> guardians = new ArrayList<>();
        for (Student s : studentRepository.findByTenantId(saved.getTenantId())) {
            if (!everyone) {
                if (s.getClassSection() == null) continue;
                if (GradeLevel.parse(s.getClassSection().getGradeName()) != wanted) continue;
            }
            if (s.getUserId() != null) students.add(s.getUserId());
            if (s.getParents() != null) {
                for (Parent p : s.getParents()) {
                    if (p.getUserId() != null) guardians.add(p.getUserId());
                }
            }
        }
        notificationPublisher.announcementPosted(students, "STUDENT",
                saved.getTenantId(), saved.getAcademicYearId(), saved.getId(), saved.getTitle());
        notificationPublisher.announcementPosted(guardians, "PARENT",
                saved.getTenantId(), saved.getAcademicYearId(), saved.getId(), saved.getTitle());
    }

    public List<Announcement> getAnnouncementsForParent(UUID tenantId, UUID academicYearId, String targetGrade) {
        return announcementRepository.findByTenantIdAndAcademicYearIdAndTargetGradeIn(
                tenantId, 
                academicYearId, 
                List.of(targetGrade, "ALL")
        );
    }
}
