import javax.imageio.ImageIO;
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
            BufferedImage res = isolateStickerByColorAndExpansion(src);
            String outputName = file.getName().replace(".png", "_transparent.png");
            ImageIO.write(res, "PNG", new File(outputDir, outputName));
        }
    }

    private static BufferedImage isolateStickerByColorAndExpansion(BufferedImage src) {
        int width = src.getWidth();
        int height = src.getHeight();
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        
        boolean[][] isBody = new boolean[width][height];
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = src.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                // Detect "Significant Color" (not white, not grey, not black)
                // We use saturation/chroma check
                int max = Math.max(r, Math.max(g, b));
                int min = Math.min(r, Math.min(g, b));
                int diff = max - min;
                
                // If the pixel has a strong color OR is dark (but not white/grey)
                if (diff > 40 || (max < 150 && diff > 20)) {
                    isBody[x][y] = true;
                }
            }
        }

        // Dilate the body to include the white border (approx 12 pixels)
        int borderSize = 12;
        boolean[][] isSticker = new boolean[width][height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (isBody[x][y]) {
                    for (int dy = -borderSize; dy <= borderSize; dy++) {
                        for (int dx = -borderSize; dx <= borderSize; dx++) {
                            int nx = x + dx;
                            int ny = y + dy;
                            if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                                if (dx*dx + dy*dy <= borderSize*borderSize) {
                                    isSticker[nx][ny] = true;
                                }
                            }
                        }
                    }
                }
            }
        }

        // Final output
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (isSticker[x][y]) {
                    int rgb = src.getRGB(x, y);
                    // For the border pixels that are very white, keep them opaque
                    // For pixels near the very edge of the dilation, we can feather them
                    out.setRGB(x, y, rgb | 0xFF000000);
                } else {
                    out.setRGB(x, y, 0x00000000);
                }
            }
        }
        
        return out;
    }
}
