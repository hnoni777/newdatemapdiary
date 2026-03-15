import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class ImageProcessor {
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Usage: java ImageProcessor <input_dir> <output_dir>");
            return;
        }

        File inputDir = new File(args[0]);
        File outputDir = new File(args[1]);
        if (!outputDir.exists()) outputDir.mkdirs();

        File[] files = inputDir.listFiles((dir, name) -> name.endsWith(".png"));
        if (files == null) return;

        for (File file : files) {
            System.out.println("Processing: " + file.getName());
            BufferedImage src = ImageIO.read(file);
            BufferedImage res = removeWhiteBackground(src);
            String outputName = file.getName().replace(".png", "_transparent.png");
            ImageIO.write(res, "PNG", new File(outputDir, outputName));
        }
    }

    private static BufferedImage removeWhiteBackground(BufferedImage src) {
        int width = src.getWidth();
        int height = src.getHeight();
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        // Threshold for white (adjust if needed)
        int threshold = 245; 

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = src.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                if (r > threshold && g > threshold && b > threshold) {
                    out.setRGB(x, y, 0x00000000); // Transparent
                } else {
                    out.setRGB(x, y, rgb | 0xFF000000); // Opaque
                }
            }
        }
        return out;
    }
}
