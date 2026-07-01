package com.schoolos.management;

import com.schoolos.common.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "class_sections")
public class ClassSection extends BaseTenantEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String gradeName;

    @Column(nullable = false)
    private String sectionName;

    @Column
    private String roomNumber;

    @Column(name = "teacher_id") // Adds the missing link to the teacher
    private UUID teacherId;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public String getGradeName() { return gradeName; }
    public void setGradeName(String gradeName) { this.gradeName = gradeName; }
    
    public String getSectionName() { return sectionName; }
    public void setSectionName(String sectionName) { this.sectionName = sectionName; }
    
    public String getRoomNumber() { return roomNumber; }
    public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }

    public UUID getTeacherId() { return teacherId; }
    public void setTeacherId(UUID teacherId) { this.teacherId = teacherId; }
}
