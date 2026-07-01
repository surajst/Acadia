package com.schoolos.academics;

import java.util.List;
import java.util.UUID;

public class BulkScoreEntryRequest {
    private List<ScoreEntry> scores;

    public List<ScoreEntry> getScores() { return scores; }
    public void setScores(List<ScoreEntry> scores) { this.scores = scores; }

    public static class ScoreEntry {
        private UUID studentId;
        private Integer score;

        public UUID getStudentId() { return studentId; }
        public void setStudentId(UUID studentId) { this.studentId = studentId; }

        public Integer getScore() { return score; }
        public void setScore(Integer score) { this.score = score; }
    }
}
