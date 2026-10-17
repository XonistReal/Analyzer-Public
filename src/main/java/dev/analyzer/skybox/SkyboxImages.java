package dev.analyzer.skybox;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Original bounded PNG/JPEG importer. Headers are checked before allocating decoded pixels. */
public final class SkyboxImages {
    public static final long MAX_FILE_BYTES = 32L * 1024 * 1024, MAX_TOTAL_BYTES = 96L * 1024 * 1024;
    public static final long MAX_PIXELS = 32L * 1024 * 1024;
    public static final int MAX_EDGE = 8192, MAX_CUBE_EDGE = 2048;
    public static final List<String> FACES = List.of("px", "nx", "py", "ny", "pz", "nz");
    public record Image(int width, int height, int[] rgba) {
        public Image { if (rgba.length != (long) width * height) throw new IllegalArgumentException("Wrong pixel count"); }
    }
    public record Sky(boolean cube, List<Image> images, String description) {
        public Sky { images = List.copyOf(images); }
    }
    private SkyboxImages() { }
    public static Sky load(Path selected) throws IOException {
        Path real = selected.toRealPath();
        if (Files.isDirectory(real)) {
            List<Image> images = new ArrayList<>(); long bytes = 0, pixels = 0; int edge = -1;
            for (String face : FACES) {
                Path file = faceFile(real, face);
                long size = Files.size(file); bytes += size;
                if (bytes > MAX_TOTAL_BYTES) throw new IOException("Cube images together exceed 96 MiB.");
                Image image = readFile(file);
                if (image.width != image.height || image.width > MAX_CUBE_EDGE) throw new IOException("Cube faces must be square, at most 2048 × 2048.");
                if (edge != -1 && image.width != edge) throw new IOException("All six cube faces must have matching dimensions.");
                edge = image.width; pixels += (long) edge * edge;
                if (pixels > MAX_PIXELS) throw new IOException("Skybox exceeds the decoded pixel budget.");
                images.add(image);
            }
            return new Sky(true, images, "Six faces · " + edge + " × " + edge);
        }
        if (!SkyboxLibrary.supported(real)) throw new IOException("Use a PNG/JPEG panorama or a folder containing six cube faces.");
        Image image = readFile(real);
        if (image.width != image.height * 2) throw new IOException("A panoramic image must be exactly 2:1, for example 4096 × 2048. Use a cube folder for six square faces.");
        return new Sky(false, List.of(image), "Panorama · " + image.width + " × " + image.height);
    }
    public static Path faceFile(Path directory, String face) throws IOException {
        Path found = null;
        for (String extension : List.of(".png", ".jpg", ".jpeg")) {
            Path path = directory.resolve(face + extension);
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) continue;
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)
                    || !path.toRealPath().startsWith(directory.toRealPath())) throw new IOException("Cube faces must be ordinary files inside their folder.");
            if (found != null) throw new IOException("More than one image for cube face " + face + ". Keep one extension per face.");
            found = path;
        }
        if (found == null) throw new IOException("Missing " + face + ".png/.jpg. A cube folder needs px, nx, py, ny, pz and nz.");
        return found;
    }
    private static Image readFile(Path path) throws IOException {
        long bytes = Files.size(path);
        if (bytes < 1 || bytes > MAX_FILE_BYTES) throw new IOException("Each sky image must be between 1 byte and 32 MiB.");
        try (InputStream input = Files.newInputStream(path)) { return read(input); }
    }
    public static Image read(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes((int) MAX_FILE_BYTES + 1);
        if (bytes.length == 0 || bytes.length > MAX_FILE_BYTES) throw new IOException("Sky image exceeds 32 MiB or is empty.");
        try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new IOException("The image is not a readable PNG or JPEG.");
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("png") && !format.equals("jpeg") && !format.equals("jpg")) throw new IOException("Only PNG and JPEG images are supported.");
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_EDGE || height > MAX_EDGE || (long) width * height > MAX_PIXELS)
                    throw new IOException("Image exceeds 8192 pixels per edge or 32 megapixels.");
                var image = reader.read(0);
                int[] rgba = image.getRGB(0, 0, width, height, null, 0, width);
                for (int i = 0; i < rgba.length; i++) {
                    int argb = rgba[i]; // Explicit opaque sky pixels; image transparency cannot reveal terrain incorrectly.
                    rgba[i] = 0xFF000000 | (argb & 255) << 16 | argb & 0xFF00 | argb >>> 16 & 255;
                }
                image.flush();
                return new Image(width, height, rgba);
            } finally { reader.dispose(); }
        }
    }
}
