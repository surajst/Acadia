package com.schoolos.academics;

import com.schoolos.common.BaseTenantEntity;
import com.schoolos.management.Student;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "student_metrics")
public class StudentMetric extends BaseTenantEntity {
    @Id
    private UUID id;
    private Integer schoolXp;
    private Integer parentXp;
    private Integer activeStreak;

    @OneToOne
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;
    
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Integer getSchoolXp() { return schoolXp; }
    public void setSchoolXp(Integer schoolXp) { this.schoolXp = schoolXp; }
    public Integer getParentXp() { return parentXp; }
    public void setParentXp(Integer parentXp) { this.parentXp = parentXp; }
    public Integer getActiveStreak() { return activeStreak; }
    public void setActiveStreak(Integer activeStreak) { this.activeStreak = activeStreak; }
    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }
}
