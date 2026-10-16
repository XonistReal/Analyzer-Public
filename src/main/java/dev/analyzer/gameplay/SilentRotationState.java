package dev.analyzer.gameplay;

/** Only native movement's last emitted angular bookkeeping; never the visible camera. */
public interface SilentRotationState {
    void analyzer$recordSentLook(float yaw, float pitch);
}
