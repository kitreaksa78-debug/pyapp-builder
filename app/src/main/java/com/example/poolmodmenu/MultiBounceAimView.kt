package com.example.poolmodmenu

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*

class MultiBounceAimView(context: Context) : View(context) {
    private val mainWhiteLine = Paint().apply {
        color = Color.WHITE
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val targetCyanLine = Paint().apply {
        color = Color.CYAN
        strokeWidth = 5f
        isAntiAlias = true
        pathEffect = DashPathEffect(floatArrayOf(15f, 10f), 0f)
    }

    private val bounceRedLine = Paint().apply {
        color = Color.RED
        strokeWidth = 5f
        isAntiAlias = true
    }

    private val ghostBallPaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        // ដែនកំណត់តុប៊ីយ៉ា (Table Boundaries)
        val tableLeft = width * 0.15f
        val tableRight = width * 0.85f
        val tableTop = height * 0.18f
        val tableBottom = height * 0.82f

        // កូអរដោនេគ្រាប់បាល់ស និងគ្រាប់បាល់គោលដៅ
        val cueX = width * 0.35f
        val cueY = height * 0.52f
        val objectX = width * 0.68f
        val objectY = height * 0.52f
        const val radius = 22f

        // ១. គណនា Ghost Ball សម្រាប់ការប៉ះទង្គិច
        val dx = objectX - cueX
        val dy = objectY - cueY
        val angle = atan2(dy, dx)

        val contactX = objectX - (2 * radius * cos(angle))
        val contactY = objectY - (2 * radius * sin(angle))

        // គូសបន្ទាត់សពីគ្រាប់ស ទៅកាន់ចំណុចប៉ះ
        canvas.drawLine(cueX, cueY, contactX, contactY, mainWhiteLine)
        canvas.drawCircle(contactX, contactY, radius, ghostBallPaint)

        // ២. គណនាបន្ទាត់ផ្លាតគោលដៅ និង Multi-Bounce Cushion Reflection (គែមតុ)
        var startRayX = objectX
        var startRayY = objectY
        var rayAngle = atan2(objectY - contactY, objectX - contactX)

        for (i in 0..2) {
            val rayLength = 800f
            val nextX = startRayX + rayLength * cos(rayAngle)
            val nextY = startRayY + rayLength * sin(rayAngle)

            var hitX = nextX
            var hitY = nextY
            var bounced = false

            if (nextX < tableLeft) {
                hitX = tableLeft
                hitY = startRayY + (tableLeft - startRayX) * tan(rayAngle)
                rayAngle = Math.PI.toFloat() - rayAngle
                bounced = true
            } else if (nextX > tableRight) {
                hitX = tableRight
                hitY = startRayY + (tableRight - startRayX) * tan(rayAngle)
                rayAngle = Math.PI.toFloat() - rayAngle
                bounced = true
            }

            if (nextY < tableTop) {
                hitY = tableTop
                hitX = startRayX + (tableTop - startRayY) / tan(rayAngle)
                rayAngle = -rayAngle
                bounced = true
            } else if (nextY > tableBottom) {
                hitY = tableBottom
                hitX = startRayX + (tableBottom - startRayY) / tan(rayAngle)
                rayAngle = -rayAngle
                bounced = true
            }

            val paintToUse = if (i == 0) targetCyanLine else bounceRedLine
            canvas.drawLine(startRayX, startRayY, hitX, hitY, paintToUse)

            if (!bounced) break
            startRayX = hitX
            startRayY = hitY
        }

        invalidate()
    }
}
