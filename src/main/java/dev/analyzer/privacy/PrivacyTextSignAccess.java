package dev.analyzer.privacy;

/** Read-only acceptance diagnostic; the returned copy cannot change the editable sign. */
public interface PrivacyTextSignAccess {
    String[] analyzer$privacyLines();
}
