import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

public class KoManagerReviewProcessor {
    public static void main(String[] args) throws IOException {
        String baseDir = "C:/Users/user/.gemini/antigravity/brain/3cfff642-b9ca-46e6-a18a-ef91a1c7e5f8/";
        String[] stickers = {
            "stk_premium_moon_1773641667863.png",
            "stk_premium_sakura_1773641709052.png",
            "stk_premium_crown_1773641810278.png",
            "stk_premium_butterfly_1773641776692.png",
            "stk_premium_cloud_1773641689983.png",
            "stk_premium_clover_1773641726245.png",
            "stk_premium_balloon_1773641743013.png",
            "stk_premium_letter_1773641759137.png",
            "stk_premium_puppy_1773641793411.png",
            "stk_premium_wine_1773641823230.png"
        };

        for (String fileName : stickers) {
            File inputFile = new File(baseDir + fileName);
            if (!inputFile.exists()) continue;

            BufferedImage src = ImageIO.read(inputFile);
            // Dynamic tuning per item characteristics (Internal simulation of Young-ja's eye)
            BufferedImage processed = processWithAdaptiveLogic(src, fileName);
            
            String shortName = fileName.split("_")[2];
            ImageIO.write(processed, "png", new File(baseDir + "review_" + shortName + ".png"));
        }
    }

    private static BufferedImage processWithAdaptiveLogic(BufferedImage src, String name) {
        int w = src.getWidth(), h = src.getHeight();
        boolean[][] isCore = new boolean[w][h];
        
        // 1. Detect core based on chroma (saturation)
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
                if (max - min > 30 || max < 160) isCore[x][y] = true;
            }
        }

        // 2. Adaptive Expansion (Dilate) - Tighter for thinner items, wider for bulky ones
        int thickness = 13; 
        if (name.contains("butterfly") || name.contains(" sakur")) thickness = 11; // Thinner
        if (name.contains("moon") || name.contains("crown")) thickness = 15; // Bulky

        boolean[][] mask = new boolean[w][h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (isCore[x][y]) {
                    for (int dy = -thickness; dy <= thickness; dy++) {
                        for (int dx = -thickness; dx <= thickness; dx++) {
                            int nx = x + dx, ny = y + dy;
                            if (nx >= 0 && nx < w && ny >= 0 && ny < h && (dx*dx + dy*dy <= thickness*thickness)) {
                                mask[nx][ny] = true;
                            }
                        }
                    }
                }
            }
        }

        // 3. Final Crop & Clean: Only keep mask IF it's not noise-white
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (mask[x][y]) {
                    int rgb = src.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                    // Strict edge cutoff: If it's near the edge of the mask and too white-gray, kill it
                    if (r > 250 && g > 250 && b > 250) {
                        // Check neighbors
                        boolean hasCoreNeighbor = false;
                        for (int dy = -2; dy <= 2; dy++) {
                            for (int dx = -2; dx <= 2; dx++) {
                                int nx = x + dx, ny = y + dy;
                                if (nx >= 0 && nx < w && ny >= 0 && ny < h && isCore[nx][ny]) {
                                    hasCoreNeighbor = true; break;
                                }
                            }
                        }
                        if (!hasCoreNeighbor) { 
                            out.setRGB(x, y, 0); continue; 
                        }
                    }
                    out.setRGB(x, y, rgb | 0xFF000000);
                    if (x < minX) minX = x; if (x > maxX) maxX = x;
                    if (y < minY) minY = y; if (y > maxY) maxY = y;
                }
            }
        }

        if (maxX >= minX) return out.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
        return out;
    }
}
