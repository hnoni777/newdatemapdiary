import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import javax.imageio.ImageIO;

/**
 * KoManager's White-Shield Engine (V9)
 * Strategy: Treat Pure-White (255) as an impenetrable wall.
 * Flood fill the background from corners, stopping exactly at the white border.
 * Preserve everything not reached by the flood fill.
 */
public class KoManagerStickerProcessor {
    public static void main(String[] args) throws IOException {
        if (args.length < 2) return;
        String inputPath = args[0];
        String outputPath = args[1];
        
        BufferedImage src = ImageIO.read(new File(inputPath));
        if (src == null) return;
        
        int w = src.getWidth();
        int h = src.getHeight();
        
        // 1. Map the Background using the "White Barrier" rule
        boolean[][] isBackground = new boolean[w][h];
        Queue<Point> q = new LinkedList<>();
        
        // Start from all edge pixels to ensure we catch all background areas
        for (int x = 0; x < w; x++) { q.add(new Point(x, 0)); q.add(new Point(x, h - 1)); }
        for (int y = 0; y < h; y++) { q.add(new Point(0, y)); q.add(new Point(w - 1, y)); }
        
        while (!q.isEmpty()) {
            Point p = q.poll();
            if (p.x < 0 || p.x >= w || p.y < 0 || p.y >= h || isBackground[p.x][p.y]) continue;
            
            int rgb = src.getRGB(p.x, p.y);
            int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
            
            // CRITICAL: The Pure-White Sticker Border (255,255,255) is the BARRIER.
            // We only flood fill pixels that are NOT pure white.
            // AI background noise is usually < 255 (250, 252, etc.)
            if (r < 255 || g < 255 || b < 255) {
                isBackground[p.x][p.y] = true;
                q.add(new Point(p.x + 1, p.y));
                q.add(new Point(p.x - 1, p.y));
                q.add(new Point(p.x, p.y + 1));
                q.add(new Point(p.x, p.y - 1));
            }
        }
        
        // 2. Final Extraction: Preserve 100% of non-background area
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int minX = w, minY = h, maxX = -1, maxY = -1;
        
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!isBackground[x][y]) {
                    // This is the sticker area (Interior + White Border). Keep original.
                    out.setRGB(x, y, src.getRGB(x, y) | 0xFF000000);
                    if (x < minX) minX = x; if (x > maxX) maxX = x;
                    if (y < minY) minY = y; if (y > maxY) maxY = y;
                } else {
                    out.setRGB(x, y, 0x00000000); // Background is now VOID
                }
            }
        }
        
        // 3. Auto-Crop
        if (maxX >= minX) {
            int padding = 4;
            int fx = Math.max(0, minX - padding);
            int fy = Math.max(0, minY - padding);
            int fw = Math.min(w - fx, (maxX - minX + 1) + padding * 2);
            int fh = Math.min(h - fy, (maxY - minY + 1) + padding * 2);
            ImageIO.write(out.getSubimage(fx, fy, fw, fh), "png", new File(outputPath));
        }
        System.out.println("SUCCESS_V9_WHITE_SHIELD");
    }
}
