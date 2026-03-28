
import java.io.File
import javax.imageio.ImageIO

fun main() {
    val dir = File("c:/Users/user/AndroidStudioProjects/NewDateMapDiary/app/src/main/res/drawable")
    val files = listOf("master_balloon_up.png", "premium_balloon_3d.png")
    
    for (filename in files) {
        val file = File(dir, filename)
        if (!file.exists()) {
            println("$filename not found")
            continue
        }
        val img = ImageIO.read(file)
        println("$filename size: ${img.width}x${img.height}, type: ${img.type}")
        val hasAlpha = img.colorModel.hasAlpha()
        println("Has alpha: $hasAlpha")
        
        // Sample a corner pixel
        val pixel = img.getRGB(0, 0)
        val alpha = (pixel shr 24) and 0xFF
        println("Top-left alpha: $alpha")
    }
}
