package br.edu.tradutoroffline.features.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** Synthetic images only: no camera permission, user photos, models or saved measurements. */
@RunWith(AndroidJUnit4::class)
class CameraSurfaceTest {
    @Test fun translatedPanelStaysInsidePerspectiveSurface() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val image = Bitmap.createBitmap(500, 300, Bitmap.Config.ARGB_8888)
            val overlay = TranslationOverlay(instrumentation.targetContext)
            overlay.layout(0, 0, 500, 300)
            val quad = TextQuad(listOf(Vertex(80f, 60f), Vertex(420f, 95f),
                Vertex(380f, 230f), Vertex(110f, 200f)))
            overlay.show(listOf(OverlayText(1, quad, "A estação fica a dois quarteirões de distância.", 1f)))
            overlay.draw(Canvas(image))
            assertTrue(Color.alpha(image.getPixel(250, 140)) > 0)
            assertEquals(0, Color.alpha(image.getPixel(82, 210)))
            assertEquals(0, Color.alpha(image.getPixel(400, 65)))
            assertEquals(0, Color.alpha(image.getPixel(20, 20)))
            image.recycle()
        }
    }

    @Test fun bundledOcrReadsSyntheticImageAndReturnsValidSurfaceCorners() {
        val image = Bitmap.createBitmap(1200, 400, Bitmap.Config.ARGB_8888)
        Canvas(image).apply {
            drawColor(Color.WHITE)
            drawText("The station is two blocks away.", 40f, 190f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 62f })
        }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val result = Tasks.await(recognizer.process(InputImage.fromBitmap(image, 0)), 30, TimeUnit.SECONDS)
            assertTrue(result.text.contains("station", ignoreCase = true))
            val block = result.textBlocks.first { it.text.contains("station", ignoreCase = true) }
            val corners = requireNotNull(block.cornerPoints)
            assertEquals(4, corners.size)
            assertTrue(TextQuad(corners.map { Vertex(it.x.toFloat(), it.y.toFloat()) }).valid())
        } finally { recognizer.close(); image.recycle() }
    }
}
