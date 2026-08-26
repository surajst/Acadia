package com.concept.shared.data;

import com.concept.common.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "students")
public class Student extends BaseTenantEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column
    private String rollNumber;

    /**
     * When the student actually joined. Null means they started with the
     * academic year, which is the common case; a September admission bills from
     * September rather than inheriting April's schedule.
     */
    @jakarta.persistence.Column(name = "admission_date")
    private java.time.LocalDate admissionDate;

    @Column(name = "user_id")
    private UUID userId;

    /**
     * Date of birth. Optional, because most of the roster predates the column
     * and a required field would block editing every existing child. A
     * preschool groups by age band and is usually required to hold this.
     */
    @Column(name = "date_of_birth")
    private java.time.LocalDate dateOfBirth;

    /**
     * Allergies, dietary needs and anything a teacher must know before handing
     * this child food or medicine. Free text on purpose: a coded list would be
     * wrong more often than right, and the cost of a wrong code here is a
     * child eating something they should not.
     */
    @Column(name = "medical_notes", length = 1000)
    private String medicalNotes;

    @Column(name = "emergency_contact_name")
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 32)
    private String emergencyContactPhone;

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    @ManyToOne
    @JoinColumn(name = "class_section_id", nullable = false)
    private ClassSection classSection;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getRollNumber() { return rollNumber; }
    public void setRollNumber(String rollNumber) { this.rollNumber = rollNumber; }

    public ClassSection getClassSection() { return classSection; }
    public void setClassSection(ClassSection classSection) { this.classSection = classSection; }



    @ManyToMany
    @JoinTable(
        name = "student_parents",
        joinColumns = @JoinColumn(name = "student_id"),
        inverseJoinColumns = @JoinColumn(name = "parent_id")
    )
    private Set<Parent> parents = new HashSet<>();

    public Set<Parent> getParents() { return parents; }
    public void setParents(Set<Parent> parents) { this.parents = parents; }

    public java.time.LocalDate getAdmissionDate() {
        return admissionDate;
    }

    public void setAdmissionDate(java.time.LocalDate admissionDate) {
        this.admissionDate = admissionDate;
    }

    public java.time.LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(java.time.LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getMedicalNotes() { return medicalNotes; }
    public void setMedicalNotes(String medicalNotes) { this.medicalNotes = medicalNotes; }

    public String getEmergencyContactName() { return emergencyContactName; }
    public void setEmergencyContactName(String emergencyContactName) { this.emergencyContactName = emergencyContactName; }

    public String getEmergencyContactPhone() { return emergencyContactPhone; }
    public void setEmergencyContactPhone(String emergencyContactPhone) { this.emergencyContactPhone = emergencyContactPhone; }
}
