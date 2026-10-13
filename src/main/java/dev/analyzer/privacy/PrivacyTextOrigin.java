package dev.analyzer.privacy;

/** Unserialized origin attached to a received component, independent of its value or style. */
public interface PrivacyTextOrigin {
    void analyzer$markRemote();
    boolean analyzer$isRemote();
}
