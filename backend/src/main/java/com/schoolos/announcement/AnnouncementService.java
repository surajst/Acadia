package com.schoolos.announcement;

import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;

    public AnnouncementService(AnnouncementRepository announcementRepository) {
        this.announcementRepository = announcementRepository;
    }

    public Announcement createAnnouncement(Announcement announcement) {
        if (announcement.getId() == null) {
            announcement.setId(UUID.randomUUID());
        }
        if (announcement.getCreatedAt() == null) {
            announcement.setCreatedAt(LocalDateTime.now());
        }
        
        Announcement saved = announcementRepository.save(announcement);
        
        System.out.println("[MOCK WHATSAPP DISPATCH] Sending to " + saved.getTargetGrade() + ": " + saved.getTitle());
        
        return saved;
    }

    public List<Announcement> getAnnouncementsForParent(UUID tenantId, UUID academicYearId, String targetGrade) {
        return announcementRepository.findByTenantIdAndAcademicYearIdAndTargetGradeIn(
                tenantId, 
                academicYearId, 
                List.of(targetGrade, "ALL")
        );
    }
}
