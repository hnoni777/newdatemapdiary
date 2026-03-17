import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class StickerOptimizer {
    public static void main(String[] args) {
        File dir = new File("c:/Users/user/AndroidStudioProjects/NewDateMapDiary/app/src/main/res/drawable-nodpi");
        File[] files = dir.listFiles((d, name) -> name.startsWith("stk_premium_") && name.endsWith(".png"));
        
        if (files == null) return;
        
        int maxDim = 400; // Optimal size for mobile stickers
        
        for (File f : files) {
            try {
                BufferedImage original = ImageIO.read(f);
                if (original == null) continue;
                
                int w = original.getWidth();
                int h = original.getHeight();
                
                if (w <= maxDim && h <= maxDim) continue; // Already small enough
                
                // Calculate new dimensions
                double scale = Math.min((double)maxDim / w, (double)maxDim / h);
                int newW = (int)(w * scale);
                int newH = (int)(h * scale);
                
                BufferedImage resized = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = resized.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                g.drawImage(original, 0, 0, newW, newH, null);
                g.dispose();
                
                // Overwrite original with optimized version
                ImageIO.write(resized, "png", f);
                System.out.println("Optimized: " + f.getName() + " (" + w + "x" + h + " -> " + newW + "x" + newH + ")");
                
            } catch (Exception e) {
                System.err.println("Failed to optimize: " + f.getName());
            }
        }
    }
}
