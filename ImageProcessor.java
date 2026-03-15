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

        File[] files = inputDir.listFiles((dir, name) -> name.endsWith(".png") && !name.contains("_transparent") && !name.contains("_final"));
        if (files == null) return;

        for (File file : files) {
            System.out.println("Young-ja is performing Master-Nukki: " + file.getName());
            BufferedImage src = ImageIO.read(file);
            BufferedImage res = youngjaMasterNukki(src);
            String outputName = file.getName().replace(".png", "_youngja_perfect.png");
            ImageIO.write(res, "PNG", new File(outputDir, outputName));
        }
    }

    private static BufferedImage youngjaMasterNukki(BufferedImage src) {
        int width = src.getWidth();
        int height = src.getHeight();
        
        // Step 1: Detect the "Core" of the sticker (the colorful part)
        boolean[][] core = new boolean[width][height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = src.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                // Saturation check: max - min
                int max = Math.max(r, Math.max(g, b));
                int min = Math.min(r, Math.min(g, b));
                int chroma = max - min;

                // If it has color (chroma > 40) OR it's a dark part of the object (max < 180 and chroma > 15)
                if (chroma > 35 || (max < 170 && chroma > 15)) {
                    core[x][y] = true;
                }
            }
        }

        // Step 2: Distance Field calculation (Simplified)
        // We want to keep everything within ~14 pixels of the core to preserve the white border
        int borderThickness = 14; 
        boolean[][] finalMask = new boolean[width][height];
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (core[x][y]) {
                    // Dilate
                    for (int dy = -borderThickness; dy <= borderThickness; dy++) {
                        for (int dx = -borderThickness; dx <= borderThickness; dx++) {
                            int nx = x + dx;
                            int ny = y + dy;
                            if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                                if (dx*dx + dy*dy <= borderThickness * borderThickness) {
                                    finalMask[nx][ny] = true;
                                }
                            }
                        }
                    }
                }
            }
        }

        // Step 3: Create final image with the mask and smooth edges
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (finalMask[x][y]) {
                    int rgb = src.getRGB(x, y);
                    // To prevent edge harshness, we can check neighbor alpha
                    out.setRGB(x, y, rgb | 0xFF000000);
                } else {
                    out.setRGB(x, y, 0x00000000);
                }
            }
        }

        // Step 4: Auto-Crop (Trim)
        return trim(out);
    }

    private static BufferedImage trim(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        int top = height, bottom = 0, left = width, right = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (((img.getRGB(x, y) >> 24) & 0xFF) > 0) {
                    if (x < left) left = x;
                    if (x > right) right = x;
                    if (y < top) top = y;
                    if (y > bottom) bottom = y;
                }
            }
        }
        if (left > right || top > bottom) return img;
        return img.getSubimage(left, top, right - left + 1, bottom - top + 1);
    }
}
