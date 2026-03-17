import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class ImageInspector {
    public static void main(String[] args) {
        File dir = new File("c:/Users/user/AndroidStudioProjects/NewDateMapDiary/app/src/main/res/drawable-nodpi");
        
        System.out.println("Name | Width | Height | Size(KB)");
        System.out.println("---|---|---|---");
        
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> name.startsWith("stk_premium_") && name.endsWith(".png"));
            if (files != null) {
                for (File f : files) {
                    try {
                        BufferedImage img = ImageIO.read(f);
                        if (img != null) {
                            System.out.println(f.getName() + " | " + img.getWidth() + " | " + img.getHeight() + " | " + (f.length()/1024));
                        }
                    } catch (Exception e) {
                        // skip errors
                    }
                }
            }
        }
    }
}
