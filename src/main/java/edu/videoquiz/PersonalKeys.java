package edu.videoquiz;

import edu.videoquiz.core.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import java.io.IOException;

@Service
public class PersonalKeys {
    private final Store store;
    private final KeyCipher cipher;
    private final GeminiClients clients;
    public PersonalKeys(Store store, GeminiClients clients,
                        @Value("${videoquiz.key-encryption-key:}") String master) {
        this.store = store; this.clients = clients; cipher = new KeyCipher(master);
    }
    public record Status(boolean configured, boolean available, String status) {}
    public Status status(String owner) throws IOException {
        boolean configured = store.geminiKey(owner) != null;
        return new Status(configured, cipher.configured(), configured ? "API Key Configured" : "API Key Not Configured");
    }
    public Status save(String owner, String input) throws IOException {
        if (!cipher.configured()) throw new Gemini.KeyFailure("STORAGE_UNAVAILABLE", "Personal key storage is unavailable. Contact the administrator.", 503);
        // Keys are opaque: Google owns their format, prefix and validity. Only bound
        // request size and reject characters unsafe in a single HTTP header value.
        String key = input == null ? "" : input.strip();
        if (key.isEmpty() || key.length() > 4096 || key.chars().anyMatch(c -> c < 0x21 || c > 0x7e))
            throw new Gemini.KeyFailure("KEY_INPUT_INVALID", "Enter the complete API key without spaces or control characters (maximum 4096 characters).", 400);
        store.account(owner);
        clients.forKey(key).verifyKey();
        synchronized (store) { store.saveGeminiKey(owner, cipher.encrypt(owner, key)); }
        return new Status(true, true, "Verification Successful");
    }
    public Status remove(String owner) throws IOException {
        synchronized (store) { store.removeGeminiKey(owner); }
        return status(owner);
    }
    public Gemini forAnalysis(String owner) throws IOException {
        var saved = store.geminiKey(owner);
        if (saved == null) throw new Gemini.KeyFailure("KEY_REQUIRED", "Configure your personal Gemini API key in Gemini API Settings before analysis.", 409);
        try { return clients.forKey(cipher.decrypt(owner, saved.encrypted)); }
        catch (IllegalStateException e) { throw new Gemini.KeyFailure("STORAGE_UNAVAILABLE", "The saved API key could not be unlocked. Contact the administrator or replace your key.", 503); }
    }
}
