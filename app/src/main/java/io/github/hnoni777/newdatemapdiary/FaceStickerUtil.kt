package io.github.hnoni777.newdatemapdiary

import android.graphics.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.android.gms.tasks.Task

object FaceStickerUtil {

    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .build()
    )

    fun createFaceSticker(originalBitmap: Bitmap, onComplete: (Bitmap?) -> Unit) {
        val inputImage = InputImage.fromBitmap(originalBitmap, 0)
        
        faceDetector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isEmpty()) {
                    onComplete(null)
                    return@addOnSuccessListener
                }
                
                val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }!!
                val box = face.boundingBox

                // 🔥 [중심 맞춤] 얼굴이 중앙에 오도록 프로필 스티커 생성
                val result = createCircularProfileSticker(originalBitmap, box)
                onComplete(result)
            }
            .addOnFailureListener { onComplete(null) }
    }

    private fun createCircularProfileSticker(original: Bitmap, faceBox: Rect): Bitmap? {
        val width = original.width
        val height = original.height
        
        // 1. 🔥 [중앙 정렬 최적화] 얼굴 중심 좌표 계산
        val boxHeight = faceBox.height().toFloat()
        val centerX = faceBox.centerX().toFloat()
        // 머리카락 포함을 위해 살짝 위를 '스티커 중심'으로 잡되, 얼굴은 중앙에 정렬되도록 반지름 조절
        val centerY = faceBox.centerY().toFloat() - (boxHeight * 0.15f)
        
        // 반지름을 충분히 잡아 얼굴이 짤리지 않고 중앙에 오게 함
        val radius = (faceBox.width() / 2f) * 2.3f 
        
        val stickerSize = (radius * 2).toInt() + 100 // 마진 넉넉히
        val output = Bitmap.createBitmap(stickerSize, stickerSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val sc = stickerSize / 2f

        // 2. 프리미엄 느낌을 위한 하얀 원형 테두리 & 그림자
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            setShadowLayer(20f, 0f, 10f, Color.parseColor("#44000000"))
        }
        canvas.drawCircle(sc, sc, radius + 10, shadowPaint)
        
        // 3. 사진을 원형으로 잘라내기 위한 패스 설정
        val path = Path().apply { addCircle(sc, sc, radius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(path)
        
        // 4. 🔥 [중앙 정렬 로직 핵심] 원본 이미지의 얼굴 영역을 원형 내 정중앙에 그리기
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            isDither = true
        }

        // 스티커의 중심(sc, sc)에 원본의 (centerX, centerY)가 오도록 매트릭스 계산
        val matrix = Matrix()
        val scale = (radius * 2) / (radius * 2) // 비율 1:1로 일단 대응 (잘릴 경우 대비)
        
        // 번거로운 srcRect 대신 Matrix로 직관적으로 중앙에 배치
        matrix.postTranslate(-centerX, -centerY) // 원본 얼굴의 중심을 (0,0)으로 이동
        matrix.postTranslate(sc, sc) // 다시 스티커의 중앙(sc, sc)으로 이동
        
        canvas.drawBitmap(original, matrix, paint)
        canvas.restore()

        // 5. 유리 광택 느낌의 마감 테두리
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            color = Color.argb(40, 255, 255, 255)
        }
        canvas.drawCircle(sc, sc, radius, borderPaint)

        return output
    }
}
