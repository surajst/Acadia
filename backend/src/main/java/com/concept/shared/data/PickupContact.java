package com.concept.shared.data;

import com.concept.common.BaseTenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Someone allowed to collect this child.
 *
 * <p>A table rather than a text field on the student, because this is a list
 * that gets added to and revoked from, and because a name being <em>removed</em>
 * has to be as unambiguous as a name being present. Free text would leave the
 * answer to "may this person take the child" to whoever is reading the note.
 *
 * <p>Deliberately not modelled as a {@code Parent}: the people on this list are
 * often neither guardians nor account holders -- a grandparent, a driver, a
 * neighbour -- and giving them a login to be collectable would be worse than
 * not tracking them at all.
 */
@Entity
@Table(name = "pickup_contacts")
public class PickupContact extends BaseTenantEntity {

    @Id
    private UUID id;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(nullable = false)
    private String name;

    /** How they are known to the child -- "Grandmother", "Driver", "Aunt". */
    @Column(name = "relationship")
    private String relationship;

    @Column(length = 32)
    private String phone;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRelationship() { return relationship; }
    public void setRelationship(String relationship) { this.relationship = relationship; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
