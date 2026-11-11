package edu.videoquiz.core;

import java.util.*;

public final class Models {
    private Models() {}
    public static class Video {
        public transient long revision;
        public String id, owner, title, youtubeId, kind, sha256, status = "UPLOADING", message = "Waiting for upload";
        public long size, received, created = System.currentTimeMillis(), externalWaitSince;
        public double duration;
        public String analysisRunId;
        public String quizLanguage;
        public List<Point> points = new ArrayList<>();
        public List<Question> questions = new ArrayList<>();
    }
    public record Point(double start, double end, String summary, String evidence) {}
    public record Question(String question, List<String> options, int correctIndex,
                           String explanation, int pointIndex, double timestamp) {}
    public static class Attempt {
        public transient long revision;
        public String id, owner, videoId;
        public long created = System.currentTimeMillis();
        public Map<Integer, Integer> answers = new TreeMap<>();
    }
    public record Account(String username, String passwordHash) {}
    public record Feedback(int questionIndex, int selectedIndex, int correctIndex, boolean correct,
                           String explanation, int score, int answered, int total) {}
}
