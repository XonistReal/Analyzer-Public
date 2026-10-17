package dev.analyzer.skybox;

import dev.analyzer.core.AnalyzerCore;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Local image headers, cube structure, shelf boundaries and saved-mode compatibility. */
public final class SkyboxSelfTest {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        Path temporary = Files.createTempDirectory("analyzer-skybox-check-");
        try {
            Path root = SkyboxLibrary.defaultRoot(temporary); Files.createDirectories(root);
            Path panorama = root.resolve("My blue sky.png"); image(panorama, 32, 16, 0xFF123456);
            var sky = SkyboxImages.load(panorama);
            check(!sky.cube() && sky.images().size() == 1, "One spherical panorama");
            check(sky.images().getFirst().width() == 32 && sky.images().getFirst().height() == 16, "Dimensions survive decode");
            check(sky.images().getFirst().rgba()[0] == 0xFF563412, "Explicit RGBA pixel upload layout");
            Path jpg = root.resolve("photo.JPEG"); image(jpg, 24, 12, 0xFF667788);
            check(!SkyboxImages.load(jpg).cube(), "Case-insensitive JPEG selection");
            Path plain = root.resolve("ordinary.png"); image(plain, 12, 12, 0xFF123456);
            rejects(() -> SkyboxImages.load(plain), "Reject nonspherical pictures with aspect explanation");
            Path broken = root.resolve("broken.png"); Files.writeString(broken, "not an image");
            rejects(() -> SkyboxImages.load(broken), "Reject false extension");
            Path cube = root.resolve("Cube"); Files.createDirectories(cube);
            for (String face : SkyboxImages.FACES) image(cube.resolve(face + ".png"), 16, 16, 0xFF003344);
            check(SkyboxImages.load(cube).cube() && SkyboxImages.load(cube).images().size() == 6, "Six named cube faces");
            image(cube.resolve("px.jpg"), 16, 16, 0xFF112233);
            rejects(() -> SkyboxImages.load(cube), "Duplicate face extensions are ambiguous"); Files.delete(cube.resolve("px.jpg"));
            image(cube.resolve("nz.png"), 8, 8, 0xFF334455);
            rejects(() -> SkyboxImages.load(cube), "Unequal cube faces rejected"); Files.delete(cube.resolve("nz.png"));
            rejects(() -> SkyboxImages.load(cube), "Missing cube face rejected");
            image(cube.resolve("nz.png"), 16, 16, 0xFF334455);
            Files.writeString(root.resolve("ignore.vtf"), "Not supported");
            var listing = SkyboxLibrary.scan(root, root);
            check(listing.entries().getFirst().directory(), "Folders sort before images");
            check(listing.entries().stream().noneMatch(entry -> entry.name().endsWith("vtf")), "Raw Valve data is not advertised as image import");
            check(SkyboxLibrary.filter(listing.entries(), "BLUE").size() == 1, "Folder search ignores case");
            check(SkyboxLibrary.selected(root, panorama).equals(panorama.toRealPath()), "Actual absolute selected path");
            rejects(() -> SkyboxLibrary.scan(root, temporary), "Shelf cannot navigate outside root");
            rejects(() -> SkyboxLibrary.selected(root, temporary), "Shelf selection stays inside root");
            try {
                Path link = root.resolve("outside"); Files.createSymbolicLink(link, temporary);
                check(SkyboxLibrary.scan(root, root).entries().stream().noneMatch(entry -> entry.path().equals(link)), "Shelf skips child links");
                rejects(() -> SkyboxLibrary.selected(root, link), "Selected link escape rejected");
            } catch (IOException | UnsupportedOperationException | SecurityException unavailable) {
                System.out.println("Skybox symlink fixture skipped: operating system link permission unavailable");
            }
            AnalyzerCore core = new AnalyzerCore(temporary.resolve("config"));
            check(core.getChoice("sky", "source").equals("Procedural"), "Older source omission retains existing procedural sky");
            check(core.getChoice("sky", "preset").equals("Aurora"), "Existing Aurora default unchanged");
            for (String preset : new String[]{"Aurora", "Nebula", "Ember"}) {
                core.setValue("sky", "preset", preset); core.save();
                AnalyzerCore loaded = new AnalyzerCore(temporary.resolve("config")); loaded.load();
                check(loaded.getChoice("sky", "preset").equals(preset), "Old preset survives " + preset);
            }
            for (String source : new String[]{"Custom", "Minecraft panorama", "Minecraft End"}) {
                core.setValue("sky", "source", source); core.setValue("sky", "custom-path", panorama.toString()); core.save();
                AnalyzerCore loaded = new AnalyzerCore(temporary.resolve("config")); loaded.load();
                check(loaded.getChoice("sky", "source").equals(source) && loaded.getText("sky", "custom-path").equals(panorama.toString()), "Sky source/path round trip " + source);
            }
            check(core.getText("name-changer", "alias").equals("Player"), "Name alias defaults remain independent");
            rejects(() -> core.setValue("name-changer", "alias", "C:/path.png"), "Alias text remains strict");
            rejects(() -> core.setValue("sky", "custom-path", "bad\npath"), "Control characters rejected in local paths");
            System.out.println("SkyboxSelfTest: " + assertions + " assertions passed");
        } finally {
            try (var paths = Files.walk(temporary)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
        }
    }
    private static void image(Path path, int width, int height, int color) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) for (int y = 0; y < height; y++) image.setRGB(x, y, color);
        ImageIO.write(image, path.toString().toLowerCase().endsWith("png") ? "png" : "jpg", path.toFile()); image.flush();
    }
    private interface Action { void run() throws Exception; }
    private static void rejects(Action action, String message) throws Exception {
        try { action.run(); throw new AssertionError(message); } catch (IOException | IllegalArgumentException expected) { check(true, message); }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); assertions++; }
}
