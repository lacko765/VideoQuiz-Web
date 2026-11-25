package edu.videoquiz.core;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;

/** Versioned AES-256-GCM envelope, authenticated to its application owner. */
public final class KeyCipher {
    private final SecretKeySpec master;
    private final SecureRandom random = new SecureRandom();
    public KeyCipher(String encoded) {
        if (encoded == null || encoded.isBlank()) { master = null; return; }
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(encoded); }
        catch (IllegalArgumentException e) { throw new IllegalStateException("Key encryption configuration must be base64 encoding of 32 random bytes."); }
        if (bytes.length != 32) throw new IllegalStateException("Key encryption configuration must be base64 encoding of 32 random bytes.");
        master = new SecretKeySpec(bytes, "AES");
        Arrays.fill(bytes, (byte)0);
    }
    public boolean configured() { return master != null; }
    public String encrypt(String owner, String value) {
        requireConfigured();
        byte[] clear = value.getBytes(StandardCharsets.UTF_8);
        try {
            byte[] nonce = new byte[12]; random.nextBytes(nonce);
            byte[] encrypted = cipher(Cipher.ENCRYPT_MODE, owner, nonce).doFinal(clear);
            byte[] envelope = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, envelope, 0, nonce.length);
            System.arraycopy(encrypted, 0, envelope, nonce.length, encrypted.length);
            return "v1." + Base64.getEncoder().encodeToString(envelope);
        } catch (Exception e) { throw new IllegalStateException("Could not protect the API key."); }
        finally { Arrays.fill(clear, (byte)0); }
    }
    public String decrypt(String owner, String envelope) {
        requireConfigured();
        byte[] clear = null;
        try {
            if (!envelope.startsWith("v1.")) throw new IllegalArgumentException();
            byte[] bytes = Base64.getDecoder().decode(envelope.substring(3));
            if (bytes.length < 29) throw new IllegalArgumentException();
            clear = cipher(Cipher.DECRYPT_MODE, owner, Arrays.copyOf(bytes, 12))
                .doFinal(Arrays.copyOfRange(bytes, 12, bytes.length));
            return new String(clear, StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException("The saved API key could not be unlocked. Contact the administrator or replace your key."); }
        finally { if (clear != null) Arrays.fill(clear, (byte)0); }
    }
    private Cipher cipher(int mode, String owner, byte[] nonce) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, master, new GCMParameterSpec(128, nonce));
        cipher.updateAAD(("videoquiz:gemini:v1:" + owner).getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
    private void requireConfigured() {
        if (master == null) throw new IllegalStateException("Personal key storage is unavailable. Contact the administrator.");
    }
}
