package com.schoolos.academics;

import com.schoolos.common.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Maps a grade level (ClassSection.gradeName) to a Subject offered there.
 * Configured once per grade by an admin; every class section under that
 * grade inherits the same subject list (no per-section duplication).
 */
@Entity
@Table(name = "grade_subjects")
public class GradeSubject extends BaseTenantEntity {

    @Id
    private UUID id;

    @Column(name = "grade_name", nullable = false)
    private String gradeName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getGradeName() { return gradeName; }
    public void setGradeName(String gradeName) { this.gradeName = gradeName; }

    public Subject getSubject() { return subject; }
    public void setSubject(Subject subject) { this.subject = subject; }
}
