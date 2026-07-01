package com.schoolos.announcement;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/announcements")
public class AnnouncementController {

    private final AnnouncementService announcementService;

    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @PostMapping
    public ResponseEntity<Announcement> createAnnouncement(@RequestBody Announcement announcement) {
        Announcement saved = announcementService.createAnnouncement(announcement);
        return ResponseEntity.ok(saved);
    }

    @GetMapping
    public ResponseEntity<List<Announcement>> getAnnouncements(
            @RequestParam UUID tenantId,
            @RequestParam UUID academicYearId,
            @RequestParam String targetGrade) {
            
        List<Announcement> announcements = announcementService.getAnnouncementsForParent(
                tenantId, 
                academicYearId, 
                targetGrade
        );
        return ResponseEntity.ok(announcements);
    }
}
