package dev.analyzer.automation;

/** Pure scoring contract shared by loaded-world surveys and synthetic acceptance tests. */
public final class BuildSitePolicy {
    private BuildSitePolicy() { }
    public record Survey(boolean fullyLoaded, boolean inBounds, int occupied, int fluids, int hazards,
                         int hostiles, int groundCells, int supportedCells, int slope, boolean accessible, double distance) { }
    public record Result(boolean accepted, double score, String reason) { }
    public static Result evaluate(Survey survey) {
        if (survey.occupied() < 0 || survey.fluids() < 0 || survey.hazards() < 0 || survey.hostiles() < 0 || survey.supportedCells() < 0 || survey.slope() < 0)
            return reject("Invalid site survey counts");
        if (!survey.fullyLoaded()) return reject("Some footprint or access chunks are not loaded");
        if (!survey.inBounds()) return reject("The footprint leaves the build height or world border");
        if (survey.occupied() > 0) return reject("The build would clear an existing solid block or block entity");
        if (survey.fluids() > 0) return reject("The footprint contains water or lava");
        if (survey.hazards() > 0) return reject("The footprint or access contains hazardous terrain");
        if (survey.hostiles() > 0) return reject("Hostile mobs are too close to the proposed build");
        if (survey.groundCells() < 1 || survey.supportedCells() != survey.groundCells()) return reject("The complete foundation needs solid ground");
        if (survey.slope() > 1) return reject("The terrain is too uneven for a foundation without clearing");
        if (!survey.accessible()) return reject("No loaded two-block-high entrance approach was found");
        if (!Double.isFinite(survey.distance()) || survey.distance() < 0) return reject("Invalid site distance");
        return new Result(true, 1000 - survey.distance() * 2 - survey.slope() * 30, "Loaded, level, supported and clear");
    }
    private static Result reject(String reason) { return new Result(false, Double.NEGATIVE_INFINITY, reason); }
}
