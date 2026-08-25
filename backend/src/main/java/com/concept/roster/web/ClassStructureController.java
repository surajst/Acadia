package com.concept.roster.web;

import com.concept.roster.app.ClassSectionDto;
import com.concept.roster.app.ClassStructureService;
import com.concept.tenant.TenantContext;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Interface layer for admin class-structure management (class sections +
 * classrooms). Binds requests, resolves the tenant, and delegates to the
 * application layer — no persistence logic and no entity types (ADR 0001).
 */
@Controller
public class ClassStructureController {

    private final ClassStructureService classStructureService;
    private final TenantContext tenantContext;

    public ClassStructureController(ClassStructureService classStructureService, TenantContext tenantContext) {
        this.classStructureService = classStructureService;
        this.tenantContext = tenantContext;
    }

    @GetMapping("/web/admin/class-sections")
    @ResponseBody
    public List<ClassSectionDto> listClassSections() {
        return classStructureService.listSections(tenantContext.getTenantId().orElse(null));
    }

    @PostMapping("/web/admin/class-sections/add")
    public String addClassSection(@RequestParam("gradeName") String gradeName,
                                  @RequestParam("sectionName") String sectionName,
                                  @RequestParam(value = "roomNumber", required = false) String roomNumber,
                                  @RequestParam(value = "totalCapacity", required = false) Integer totalCapacity,
                                  Authentication authentication) {
        classStructureService.addSection(tenantContext.getTenantId().orElse(null),
                tenantContext.getAcademicYearId().orElse(null), gradeName, sectionName, roomNumber,
                totalCapacity, authentication);
        return "redirect:/web/admin/management?success=class_section_added";
    }

    @PostMapping("/web/admin/class-sections/{id}/update")
    @ResponseBody
    public Object updateClassSection(@PathVariable("id") UUID id,
                                     @RequestParam("gradeName") String gradeName,
                                     @RequestParam("sectionName") String sectionName,
                                     @RequestParam(value = "roomNumber", required = false) String roomNumber,
                                     Authentication authentication) {
        try {
            classStructureService.updateSection(id, tenantContext.getTenantId().orElse(null),
                    gradeName, sectionName, roomNumber, authentication);
            return Map.of("status", "updated");
        } catch (IllegalArgumentException e) {
            return Map.of("error", e.getMessage());
        }
    }

    @PostMapping("/web/admin/class-sections/{id}/remove")
    @ResponseBody
    public Object removeClassSection(@PathVariable("id") UUID id, Authentication authentication) {
        try {
            classStructureService.removeSection(id, tenantContext.getTenantId().orElse(null), authentication);
            return Map.of("status", "removed");
        } catch (IllegalArgumentException e) {
            return Map.of("error", e.getMessage());
        }
    }

}
