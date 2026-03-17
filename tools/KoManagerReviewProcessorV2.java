import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import javax.imageio.ImageIO;

public class KoManagerReviewProcessorV2 {
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
            BufferedImage processed = null;

            // 1:1 CUSTOM LOGIC PER ITEM
            if (fileName.contains("moon") || fileName.contains("clover") || fileName.contains("cloud")) {
                // TYPE A: Use Young-ja's original perfect logic (Chroma Core + 14px Shield)
                processed = youngjaMasterNukki(src, 14, 35); 
            } else if (fileName.contains("wine") || fileName.contains("butterfly")) {
                // TYPE B: Use High-Sensitivity for thin lines (Wine stem, wing edges)
                processed = youngjaMasterNukki(src, 10, 20); // Tighter shield, higher sensitivity
            } else {
                // TYPE C: Complex edges (Crown, Puppy, Sakura)
                // Use the v9 White Shield logic for these gap-heavy items
                processed = whiteShieldNukki(src);
            }
            
            String shortName = fileName.split("_")[2];
            ImageIO.write(processed, "png", new File(baseDir + "v11_" + shortName + ".png"));
        }
    }

    private static BufferedImage youngjaMasterNukki(BufferedImage src, int thickness, int chromaT) {
        int w = src.getWidth(), h = src.getHeight();
        boolean[][] core = new boolean[w][h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
                if (max - min > chromaT || (max < 170 && max - min > 15)) core[x][y] = true;
            }
        }
        boolean[][] mask = new boolean[w][h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (core[x][y]) {
                    for (int dy = -thickness; dy <= thickness; dy++) {
                        for (int dx = -thickness; dx <= thickness; dx++) {
                            int nx = x + dx, ny = y + dy;
                            if (nx >= 0 && nx < w && ny >= 0 && ny < h && (dx*dx+dy*dy <= thickness*thickness)) mask[nx][ny] = true;
                        }
                    }
                }
            }
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (mask[x][y]) out.setRGB(x, y, src.getRGB(x, y) | 0xFF000000);
            }
        }
        return trim(out);
    }

    private static BufferedImage whiteShieldNukki(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        boolean[][] isBg = new boolean[w][h];
        Queue<Point> q = new LinkedList<>();
        for (int x = 0; x < w; x++) { q.add(new Point(x, 0)); q.add(new Point(x, h - 1)); }
        for (int y = 0; y < h; y++) { q.add(new Point(0, y)); q.add(new Point(w - 1, y)); }
        while (!q.isEmpty()) {
            Point p = q.poll();
            if (p.x < 0 || p.x >= w || p.y < 0 || p.y >= h || isBg[p.x][p.y]) continue;
            int rgb = src.getRGB(p.x, p.y);
            int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
            if (r < 255 || g < 255 || b < 255) {
                isBg[p.x][p.y] = true;
                q.add(new Point(p.x + 1, p.y)); q.add(new Point(p.x - 1, p.y));
                q.add(new Point(p.x, p.y + 1)); q.add(new Point(p.x, p.y - 1));
            }
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!isBg[x][y]) out.setRGB(x, y, src.getRGB(x, y) | 0xFF000000);
            }
        }
        return trim(out);
    }

    private static BufferedImage trim(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int l = w, r = 0, t = h, b = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (((img.getRGB(x, y) >> 24) & 0xFF) > 0) {
                    if (x < l) l = x; if (x > r) r = x;
                    if (y < t) t = y; if (y > b) b = y;
                }
            }
        }
        if (l > r || t > b) return img;
        return img.getSubimage(l, t, r - l + 1, b - t + 1);
    }
}
