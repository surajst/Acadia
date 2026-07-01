package com.schoolos.management;

import com.schoolos.common.BaseTenantEntity;
import com.schoolos.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "subject_assignments")
public class SubjectAssignment extends BaseTenantEntity {

    @Id
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher;

    @ManyToOne
    @JoinColumn(name = "class_section_id", nullable = false)
    private ClassSection classSection;

    @Column(name = "subject_name", nullable = false)
    private String subjectName;

    @Column(name = "is_home_class", nullable = false)
    private boolean isHomeClass = false;

    // ─── Getters & Setters ────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public User getTeacher() { return teacher; }
    public void setTeacher(User teacher) { this.teacher = teacher; }

    public ClassSection getClassSection() { return classSection; }
    public void setClassSection(ClassSection classSection) { this.classSection = classSection; }

    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }

    public boolean isHomeClass() { return isHomeClass; }
    public void setHomeClass(boolean homeClass) { isHomeClass = homeClass; }
}
