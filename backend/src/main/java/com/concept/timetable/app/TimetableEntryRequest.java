package com.concept.timetable.app;

import java.util.UUID;

/**
 * Flat request payload for admin timetable create/update. Lives in the
 * application layer so the web controller (binds it) and the service (consumes
 * it) share it without the app layer depending on web.
 */
public class TimetableEntryRequest {
    private UUID classSectionId;
    private UUID teacherId;
    private String dayOfWeek;
    private Integer periodNumber;
    private String startTime;
    private String endTime;
    private String subjectName;
    private String roomNumber;

    public UUID getClassSectionId() { return classSectionId; }
    public void setClassSectionId(UUID classSectionId) { this.classSectionId = classSectionId; }

    public UUID getTeacherId() { return teacherId; }
    public void setTeacherId(UUID teacherId) { this.teacherId = teacherId; }

    public String getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(String dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public Integer getPeriodNumber() { return periodNumber; }
    public void setPeriodNumber(Integer periodNumber) { this.periodNumber = periodNumber; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }

    public String getRoomNumber() { return roomNumber; }
    public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }
}
