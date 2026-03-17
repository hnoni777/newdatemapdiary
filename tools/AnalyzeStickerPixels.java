import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class AnalyzeStickerPixels {
    public static void main(String[] args) throws IOException {
        String path = "C:/Users/user/.gemini/antigravity/brain/3cfff642-b9ca-46e6-a18a-ef91a1c7e5f8/stk_premium_moon_1773641667863.png";
        BufferedImage img = ImageIO.read(new File(path));
        if (img == null) return;

        System.out.println("--- Pixel Analysis Report ---");
        
        // 1. Sample Background (Corner)
        printPixel("Top-Left Corner (0,0)", img.getRGB(0, 0));
        printPixel("Top-Right Corner", img.getRGB(img.getWidth()-1, 0));
        
        // 2. Sample Border Zone (Let's look for white-ish pixels near the edge)
        System.out.println("\n--- Edge Scan (Checking for border vs background) ---");
        int centerX = img.getWidth() / 2;
        for (int y = 0; y < 100; y += 10) {
            printPixel("Line " + y + " at center", img.getRGB(centerX, y));
        }
        
        // 3. Sample Internal Object (Center)
        printPixel("\nObject Center", img.getRGB(img.getWidth()/2, img.getHeight()/2));
    }

    private static void printPixel(String label, int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        System.out.printf("%s: RGB(%d, %d, %d)\n", label, r, g, b);
    }
}
