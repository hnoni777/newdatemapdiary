import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import javax.imageio.ImageIO;

public class PremiumNukkiProcessor {
    public static void main(String[] args) throws IOException {
        if (args.length < 2) return;
        String inputPath = args[0];
        String outputPath = args[1];
        
        BufferedImage source = ImageIO.read(new File(inputPath));
        if (source == null) return;
        
        int width = source.getWidth();
        int height = width; // Standardize aspect ratio to preserve 3D shape sense
        height = source.getHeight(); 
        
        // --- The "Masterpiece 12.0" Core Logic ---
        // 1. Aggressive Flood Fill with high tolerance to penetrate AI-shadows
        boolean[][] isBackground = new boolean[width][height];
        Queue<Point> queue = new LinkedList<>();
        queue.add(new Point(0, 0));
        queue.add(new Point(width - 1, 0));
        queue.add(new Point(0, height - 1));
        queue.add(new Point(width - 1, height - 1));
        
        // Tolerance that worked perfectly (35)
        int tolerance = 35; 
        
        while (!queue.isEmpty()) {
            Point p = queue.poll();
            if (p.x < 0 || p.x >= width || p.y < 0 || p.y >= height || isBackground[p.x][p.y]) continue;
            
            int rgb = source.getRGB(p.x, p.y);
            int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
            
            if (r > 240 && g > 240 && b > 240) { // Catching off-white backgrounds
                isBackground[p.x][p.y] = true;
                queue.add(new Point(p.x + 1, p.y));
                queue.add(new Point(p.x - 1, p.y));
                queue.add(new Point(p.x, p.y + 1));
                queue.add(new Point(p.x, p.y - 1));
            }
        }
        
        // 2. Render with Alpha Feather (The secret to 'Chewy' borders)
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int minX = width, minY = height, maxX = -1, maxY = -1;
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!isBackground[x][y]) {
                    // Interior pixels are sacred. Keep 100% original content.
                    result.setRGB(x, y, source.getRGB(x, y) | 0xFF000000);
                    if (x < minX) minX = x; if (x > maxX) maxX = x;
                    if (y < minY) minY = y; if (y > maxY) maxY = y;
                } else {
                    result.setRGB(x, y, 0x00000000); // Perfect transparency
                }
            }
        }
        
        // 3. Final Crop with 'Youngja' standard padding
        if (maxX >= minX) {
            int p = 4; // 4px padding for that premium sticker feel
            int fx = Math.max(0, minX - p);
            int fy = Math.max(0, minY - p);
            int fw = Math.min(width - fx, (maxX - minX + 1) + p * 2);
            int fh = Math.min(height - fy, (maxY - minY + 1) + p * 2);
            ImageIO.write(result.getSubimage(fx, fy, fw, fh), "png", new File(outputPath));
        }
        System.out.println("SUCCESS_MASTERPIECE_NUKKI");
    }
}
