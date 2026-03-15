import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;

public class ImageProcessor {
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Usage: java ImageProcessor <input_dir> <output_dir>");
            return;
        }

        File inputDir = new File(args[0]);
        File outputDir = new File(args[1]);
        if (!outputDir.exists()) outputDir.mkdirs();

        File[] files = inputDir.listFiles((dir, name) -> name.endsWith(".png") && !name.contains("_transparent") && !name.contains("_final"));
        if (files == null) return;

        for (File file : files) {
            System.out.println("Processing: " + file.getName());
            BufferedImage src = ImageIO.read(file);
            BufferedImage transparent = removeBackgroundFloodFill(src);
            BufferedImage trimmed = trimImage(transparent);
            String outputName = file.getName().replace(".png", "_final_v3.png");
            ImageIO.write(trimmed, "PNG", new File(outputDir, outputName));
        }
    }

    private static BufferedImage removeBackgroundFloodFill(BufferedImage src) {
        int width = src.getWidth();
        int height = src.getHeight();
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        
        // Copy src to out (opaque)
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                out.setRGB(x, y, src.getRGB(x, y) | 0xFF000000);
            }
        }

        boolean[][] isBg = new boolean[width][height];
        Queue<Point> queue = new LinkedList<>();

        // Start from corners and edges
        for (int x = 0; x < width; x++) {
            queue.add(new Point(x, 0));
            queue.add(new Point(x, height - 1));
        }
        for (int y = 0; y < height; y++) {
            queue.add(new Point(0, y));
            queue.add(new Point(width - 1, y));
        }

        while (!queue.isEmpty()) {
            Point p = queue.poll();
            if (p.x < 0 || p.x >= width || p.y < 0 || p.y >= height || isBg[p.x][p.y]) continue;

            int rgb = out.getRGB(p.x, p.y);
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;

            // Background detection: White or Light Grey (Shadow)
            // Shadows are desaturated.
            int max = Math.max(r, Math.max(g, b));
            int min = Math.min(r, Math.min(g, b));
            int diff = max - min;

            // Condition: Very light OR (Greyish and fairly light)
            boolean isBackground = (max > 220) || (max > 150 && diff < 15);

            if (isBackground) {
                isBg[p.x][p.y] = true;
                out.setRGB(p.x, p.y, 0x00000000);

                queue.add(new Point(p.x + 1, p.y));
                queue.add(new Point(p.x - 1, p.y));
                queue.add(new Point(p.x, p.y + 1));
                queue.add(new Point(p.x, p.y - 1));
            }
        }
        return out;
    }

    private static BufferedImage trimImage(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        int top = height, bottom = 0, left = width, right = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int alpha = (img.getRGB(x, y) >> 24) & 0xFF;
                if (alpha > 5) { // Small threshold to ignore tiny transparent noise
                    if (x < left) left = x;
                    if (x > right) right = x;
                    if (y < top) top = y;
                    if (y > bottom) bottom = y;
                }
            }
        }

        if (left > right || top > bottom) return img;

        // Add 2px margin
        left = Math.max(0, left - 2);
        top = Math.max(0, top - 2);
        right = Math.min(width - 1, right + 2);
        bottom = Math.min(height - 1, bottom + 2);

        return img.getSubimage(left, top, right - left + 1, bottom - top + 1);
    }
}
