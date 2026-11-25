package edu.videoquiz;

import edu.videoquiz.core.Gemini;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** A fresh immutable credential binding per verification or job; never a global API key. */
@Component
public class GeminiClients {
    private final String model;
    public GeminiClients(@Value("${videoquiz.gemini-model}") String model) { this.model = model; }
    public Gemini forKey(String key) { return new Gemini(key, model); }
}
