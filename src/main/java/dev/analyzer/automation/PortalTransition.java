package dev.analyzer.automation;

/** A narrow transport authorization, separate from server/save identity checked by the task owner. */
public final class PortalTransition {
    private String source = "", destination = "";
    private boolean nearPortal;
    public void arm(String source, String destination) {
        if (!supported(source) || !supported(destination) || source.equals(destination))
            throw new IllegalArgumentException("A portal transition needs two different vanilla dimensions.");
        this.source = source; this.destination = destination; nearPortal = false;
    }
    public void observedEntry(boolean nearPortal) { this.nearPortal |= nearPortal; }
    public boolean expects(String oldDimension, String newDimension) {
        return nearPortal && source.equals(normalize(oldDimension)) && destination.equals(normalize(newDimension));
    }
    public boolean awaitingTransition() { return nearPortal && !source.isEmpty() && !destination.isEmpty(); }
    public void clear() { source = ""; destination = ""; nearPortal = false; }
    public static String normalize(String dimension) {
        if (dimension == null) return "";
        return dimension.startsWith("minecraft:") ? dimension.substring("minecraft:".length()) : dimension;
    }
    public static boolean supported(String dimension) {
        return dimension != null && (dimension.equals("overworld") || dimension.equals("the_nether") || dimension.equals("the_end"));
    }
}
