package com.schoolos.management;

import com.schoolos.common.BaseTenantEntity;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "school_classes")
public class SchoolClass extends BaseTenantEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String gradeLevel;

    @Column(nullable = false)
    private String sectionName;

    @Column
    private String roomNumber;

    @Column(nullable = false)
    private Integer totalCapacity;

    @OneToMany(mappedBy = "schoolClass", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<Student> students = new ArrayList<>();

    // Constructors
    public SchoolClass() {}

    public SchoolClass(UUID id, String gradeLevel, String sectionName, String roomNumber, Integer totalCapacity) {
        this.id = id;
        this.gradeLevel = gradeLevel;
        this.sectionName = sectionName;
        this.roomNumber = roomNumber;
        this.totalCapacity = totalCapacity;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getGradeLevel() { return gradeLevel; }
    public void setGradeLevel(String gradeLevel) { this.gradeLevel = gradeLevel; }

    public String getSectionName() { return sectionName; }
    public void setSectionName(String sectionName) { this.sectionName = sectionName; }

    public String getRoomNumber() { return roomNumber; }
    public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }

    public Integer getTotalCapacity() { return totalCapacity; }
    public void setTotalCapacity(Integer totalCapacity) { this.totalCapacity = totalCapacity; }

    public List<Student> getStudents() { return students; }
    public void setStudents(List<Student> students) { this.students = students; }
}
