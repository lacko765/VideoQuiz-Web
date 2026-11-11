package edu.videoquiz.core;

import java.net.*;
import java.util.*;
import com.google.gson.*;
import static edu.videoquiz.core.Models.*;

public final class Validation {
    private Validation() {}
    public static String youtubeId(String raw) {
        try {
            URI u = URI.create(raw.trim());
            if (!"https".equalsIgnoreCase(u.getScheme()) || u.getUserInfo() != null || u.getPort() != -1 || u.getFragment() != null) throw new Exception();
            String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT), id = null;
            if (host.equals("youtu.be")) id = u.getPath().substring(1);
            else if (Set.of("youtube.com", "www.youtube.com", "m.youtube.com").contains(host)) {
                if (u.getPath().equals("/watch")) {
                    for (String pair : Objects.toString(u.getRawQuery(), "").split("&")) {
                        if (pair.startsWith("v=")) { if (id != null) throw new Exception(); id = pair.substring(2); }
                    }
                } else if (u.getPath().matches("/(shorts|embed)/[A-Za-z0-9_-]{11}")) id = u.getPath().substring(u.getPath().lastIndexOf('/') + 1);
            }
            if (id == null || !id.matches("[A-Za-z0-9_-]{11}")) throw new Exception();
            return id;
        } catch (Exception e) { throw new IllegalArgumentException("Enter a public HTTPS YouTube watch, shorts or youtu.be video link."); }
    }
    public static String text(JsonObject o, String key, int max) {
        if (!o.has(key) || !o.get(key).isJsonPrimitive() || !o.getAsJsonPrimitive(key).isString()) throw new IllegalArgumentException("AI response has a missing text field: " + key);
        String s = o.get(key).getAsString().trim();
        if (s.isEmpty() || s.length() > max) throw new IllegalArgumentException("AI returned invalid " + key);
        return s;
    }
    public static double number(JsonObject o, String key) {
        if (!o.has(key) || !o.get(key).isJsonPrimitive() || !o.getAsJsonPrimitive(key).isNumber()) throw new IllegalArgumentException("AI returned invalid " + key);
        double n = o.get(key).getAsDouble();
        if (!Double.isFinite(n)) throw new IllegalArgumentException("AI returned invalid " + key);
        return n;
    }
    public static double timestamp(JsonObject obj, String key) {
        JsonElement value=obj.get(key);
        if(value==null || !value.isJsonPrimitive()) throw new IllegalArgumentException("AI returned a missing timestamp: "+key);
        String raw=value.getAsString().trim();
        if(!raw.matches("\\d+(?:\\.\\d+)?") && !raw.matches("\\d+:\\d{2}(?:\\.\\d+)?") && !raw.matches("\\d+:\\d{2}:\\d{2}(?:\\.\\d+)?"))
            throw new IllegalArgumentException("AI timestamps must use seconds, MM:SS or HH:MM:SS.");
        String[] parts=raw.split(":"); double result=0;
        for(int i=0;i<parts.length;i++) {
            double part=Double.parseDouble(parts[i]);
            if(i>0 && part>=60) throw new IllegalArgumentException("AI timestamp minutes/seconds must be below 60.");
            result=result*60+part;
        }
        if(!Double.isFinite(result)) throw new IllegalArgumentException("AI timestamp is not finite.");
        return result;
    }
    public static List<Point> points(JsonObject obj, double duration) {
        if(!Double.isFinite(duration) || duration<=0 || duration>1800) throw new IllegalArgumentException("Video duration is unavailable or exceeds 30 minutes. Wait for video metadata and retry.");
        if (!obj.has("points") || !obj.get("points").isJsonArray()) throw new IllegalArgumentException("AI found no usable educational content.");
        JsonArray arr = obj.getAsJsonArray("points");
        if (arr.isEmpty() || arr.size() > 12) throw new IllegalArgumentException("AI must return 1–12 evidence points.");
        List<Point> out = new ArrayList<>();
        for (JsonElement e : arr) {
            JsonObject p = e.getAsJsonObject(); double start = timestamp(p,"start"), end = timestamp(p,"end");
            if (start < 0 || end <= start || end > duration) throw new IllegalArgumentException("AI evidence timestamps fall outside the video. Retry analysis.");
            out.add(new Point(start, end, text(p,"summary",1000), text(p,"evidence",2000)));
        }
        return out;
    }
    public static List<Question> questions(JsonObject obj, List<Point> points) {
        JsonArray arr = obj.getAsJsonArray("questions");
        if (arr == null || arr.isEmpty() || arr.size() > Math.min(8, points.size())) throw new IllegalArgumentException("AI returned an invalid question count.");
        List<Question> out = new ArrayList<>(); Set<String> seen = new HashSet<>(); Set<Integer> used = new HashSet<>();
        for (JsonElement e : arr) {
            JsonObject q = e.getAsJsonObject(); String question = text(q,"question",1000);
            double pi = number(q,"pointIndex"), ci = number(q,"correctIndex");
            if (pi != Math.floor(pi) || pi < 0 || pi >= points.size() || ci != Math.floor(ci) || ci < 0 || ci > 3) throw new IllegalArgumentException("AI returned invalid answer/evidence indices.");
            if (!seen.add(question.toLowerCase(Locale.ROOT)) || !used.add((int)pi)) throw new IllegalArgumentException("AI returned duplicate questions or evidence.");
            JsonArray options = q.getAsJsonArray("options");
            if (options == null || options.size() != 4) throw new IllegalArgumentException("AI must supply four options.");
            List<String> choices = new ArrayList<>();
            for (JsonElement option : options) {
                if (!option.isJsonPrimitive() || !option.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Invalid answer option.");
                String s = option.getAsString().trim();
                if (s.isBlank() || s.length() > 500 || choices.stream().anyMatch(x -> x.equalsIgnoreCase(s))) throw new IllegalArgumentException("Invalid or duplicate answer option.");
                choices.add(s);
            }
            out.add(new Question(question, choices, (int)ci, text(q,"explanation",2000), (int)pi, points.get((int)pi).end()));
        }
        out.sort(Comparator.comparingDouble(Question::timestamp)); return out;
    }
    public static int score(Attempt a, Video v) {
        return (int)a.answers.entrySet().stream().filter(e -> e.getValue() == v.questions.get(e.getKey()).correctIndex()).count();
    }
    public static Feedback answer(Attempt a, Video v, int index, int selected) {
        if (index < 0 || index >= v.questions.size() || selected < 0 || selected > 3) throw new IllegalArgumentException("Invalid question or answer.");
        a.answers.putIfAbsent(index, selected);
        int stored = a.answers.get(index); Question q = v.questions.get(index);
        return new Feedback(index, stored, q.correctIndex(), stored == q.correctIndex(), q.explanation(), score(a,v), a.answers.size(), v.questions.size());
    }
}
