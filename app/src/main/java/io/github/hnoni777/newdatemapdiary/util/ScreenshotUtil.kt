package io.github.hnoni777.newdatemapdiary.util

import android.graphics.*

object ScreenshotUtil {

    fun applyRoundedCorners(
        bitmap: Bitmap,
        radius: Float
    ): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, bitmap.width, bitmap.height)
        val rectF = RectF(rect)

        canvas.drawARGB(0, 0, 0, 0)
        paint.color = Color.WHITE
        canvas.drawRoundRect(rectF, radius, radius, paint)

        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)
        return output
    }

    fun applyTopRoundedCorners(
        bitmap: Bitmap,
        radius: Float
    ): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, bitmap.width, bitmap.height)
        val rectF = RectF(rect)

        canvas.drawARGB(0, 0, 0, 0)
        paint.color = Color.WHITE
        
        // 상단만 둥글게 깎기 위해: 둥근 사각형을 그린 후 하단 영역을 사각형으로 덮어씌움
        canvas.drawRoundRect(rectF, radius, radius, paint)
        canvas.drawRect(0f, radius, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)

        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)
        return output
    }
}