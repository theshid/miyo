package ani.saikou.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale

/**
 * Generates Spotify-style share cards as bitmaps and shares them via Android's share sheet.
 * Cards are rendered to a 1080×1350 (4:5) image — works on Instagram, Stories, X, WhatsApp.
 */
object ShareCardGenerator {

    private const val CARD_W = 1080
    private const val CARD_H = 1350

    // Brand colors matching the app's Primary/Background
    private const val COLOR_PRIMARY = 0xFF9D3BFF.toInt()
    private const val COLOR_BG_START = 0xFF0D0D0D.toInt()
    private const val COLOR_BG_END = 0xFF1A1A2E.toInt()
    private const val COLOR_TEXT = 0xFFE0E0E0.toInt()
    private const val COLOR_TEXT_DIM = 0xFF9E9E9E.toInt()

    /**
     * Generates a "Watching Now" share card.
     */
    suspend fun generateWatchingCard(
        context: Context,
        title: String,
        coverUrl: String?,
        episodeProgress: Int?,
        totalEpisodes: Int?,
        userScore: Int?,
        userName: String?,
    ): Bitmap = withContext(Dispatchers.Default) {
        val bitmap = createBitmap(CARD_W, CARD_H)
        val canvas = Canvas(bitmap)

        // ── Background gradient ──────────────────────────────
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, CARD_H.toFloat(),
                COLOR_BG_START, COLOR_BG_END,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, CARD_W.toFloat(), CARD_H.toFloat(), bgPaint)

        // ── Cover image ──────────────────────────────────────
        val coverBitmap = loadBitmap(context, coverUrl)
        if (coverBitmap != null) {
            // Draw as large centered poster with dark overlay
            val posterW = 600f
            val posterH = 850f
            val posterX = (CARD_W - posterW) / 2f
            val posterY = 100f

            // Rounded poster
            val posterRect = RectF(posterX, posterY, posterX + posterW, posterY + posterH)
            val scaled = coverBitmap.scale(posterW.toInt(), posterH.toInt())
            canvas.drawBitmap(scaled, posterX, posterY, null)

            // Subtle vignette overlay on the poster
            val vignettePaint = Paint().apply {
                shader = LinearGradient(
                    posterX, posterY + posterH * 0.6f,
                    posterX, posterY + posterH,
                    Color.TRANSPARENT, COLOR_BG_END,
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(posterRect, vignettePaint)
        }

        // ── Title ────────────────────────────────────────────
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 52f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val titleY = 1040f
        // Simple word-wrap: split into lines that fit the width
        val titleLines = wrapText(title, titlePaint, CARD_W - 120f)
        var lineY = titleY
        for (line in titleLines.take(2)) {
            canvas.drawText(line, CARD_W / 2f, lineY, titlePaint)
            lineY += 64f
        }

        // ── Progress bar ─────────────────────────────────────
        val progress = episodeProgress ?: 0
        val total = totalEpisodes ?: 0
        val barY = lineY + 20f
        val barH = 8f
        val barMargin = 140f

        // Track
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF2A2A2A.toInt()
        }
        canvas.drawRoundRect(
            RectF(barMargin, barY, CARD_W - barMargin, barY + barH),
            barH / 2, barH / 2, trackPaint,
        )

        // Fill
        if (total > 0) {
            val fraction = (progress.toFloat() / total).coerceIn(0f, 1f)
            val fillWidth = (CARD_W - 2 * barMargin) * fraction
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_PRIMARY
            }
            canvas.drawRoundRect(
                RectF(barMargin, barY, barMargin + fillWidth, barY + barH),
                barH / 2, barH / 2, fillPaint,
            )
        }

        // ── Episode text ─────────────────────────────────────
        val episodeText = if (total > 0) "Episode $progress / $total" else "Episode $progress"
        val epPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_DIM
            textSize = 32f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(episodeText, CARD_W / 2f, barY + 50f, epPaint)

        // ── Score badge ──────────────────────────────────────
        if (userScore != null && userScore > 0) {
            val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_PRIMARY
                textSize = 34f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("★ ${userScore / 10.0}", CARD_W / 2f, barY + 100f, scorePaint)
        }

        // ── Branding footer ──────────────────────────────────
        val brandY = CARD_H - 60f
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_DIM
            textSize = 28f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val brandText = if (userName != null) "$userName · Miyo" else "Miyo — Behold."
        canvas.drawText(brandText, CARD_W / 2f, brandY, brandPaint)

        // Accent line above branding
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_PRIMARY }
        canvas.drawRoundRect(
            RectF(CARD_W / 2f - 40f, brandY - 30f, CARD_W / 2f + 40f, brandY - 27f),
            2f, 2f, accentPaint,
        )

        bitmap
    }

    /**
     * Saves a bitmap to the cache dir and returns a shareable content URI.
     */
    fun saveToCacheAndGetUri(context: Context, bitmap: Bitmap, filename: String = "share_card.png"): Uri {
        val dir = File(context.cacheDir, "share_cards")
        dir.mkdirs()
        val file = File(dir, filename)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /**
     * Launches Android's share sheet with the given image URI.
     */
    fun shareImage(context: Context, uri: Uri, text: String? = null) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            if (text != null) putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share via"))
    }

    /**
     * Generates a stats summary share card (1080x1350).
     */
    suspend fun generateStatsCard(
        context: Context,
        userName: String?,
        avatarUrl: String?,
        episodesWatched: Int,
        minutesWatched: Int,
        animeCount: Int,
        meanScore: Float,
        topGenres: List<String>,
        chaptersRead: Int,
        mangaCount: Int,
    ): Bitmap = withContext(Dispatchers.Default) {
        val bitmap = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // ── Background gradient ─────────────────────────────
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, CARD_W.toFloat(), CARD_H.toFloat(),
                COLOR_BG_START, 0xFF1A0033.toInt(),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, CARD_W.toFloat(), CARD_H.toFloat(), bgPaint)

        // ── Decorative circles ──────────────────────────────
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_PRIMARY
            alpha = 25
        }
        canvas.drawCircle(CARD_W * 0.85f, CARD_H * 0.15f, 200f, circlePaint)
        canvas.drawCircle(CARD_W * 0.1f, CARD_H * 0.75f, 160f, circlePaint)

        // ── Avatar ──────────────────────────────────────────
        val avatarBitmap = loadBitmap(context, avatarUrl)
        if (avatarBitmap != null) {
            val avatarSize = 120
            val scaled = Bitmap.createScaledBitmap(avatarBitmap, avatarSize, avatarSize, true)
            canvas.drawBitmap(scaled, (CARD_W - avatarSize) / 2f, 80f, null)
        }

        // ── Username ────────────────────────────────────────
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val nameY = if (avatarBitmap != null) 240f else 140f
        val displayName = userName ?: "User"
        canvas.drawText(displayName, CARD_W / 2f, nameY, namePaint)

        // ── "My Stats" header ───────────────────────────────
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_PRIMARY
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("MY ANIME & MANGA STATS", CARD_W / 2f, nameY + 50f, headerPaint)

        // ── Divider ─────────────────────────────────────────
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_PRIMARY; alpha = 80 }
        canvas.drawRoundRect(
            RectF(CARD_W / 2f - 60f, nameY + 70f, CARD_W / 2f + 60f, nameY + 73f),
            2f, 2f, dividerPaint,
        )

        // ── Stat Grid ───────────────────────────────────────
        val statStartY = nameY + 110f
        val colW = CARD_W / 2f

        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 56f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_DIM
            textSize = 26f
            textAlign = Paint.Align.CENTER
        }

        val statItems = listOf(
            "$animeCount" to "Anime",
            "$episodesWatched" to "Episodes",
            formatMinutes(minutesWatched) to "Watch Time",
            "%.1f".format(meanScore) to "Mean Score",
            "$mangaCount" to "Manga",
            "$chaptersRead" to "Chapters Read",
        )

        statItems.forEachIndexed { index, (value, label) ->
            val col = index % 2
            val row = index / 2
            val cx = colW * col + colW / 2f
            val cy = statStartY + row * 130f

            canvas.drawText(value, cx, cy, valuePaint)
            canvas.drawText(label, cx, cy + 36f, labelPaint)
        }

        // ── Top Genres ──────────────────────────────────────
        if (topGenres.isNotEmpty()) {
            val genreY = statStartY + 420f
            val genreHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_PRIMARY
                textSize = 28f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("TOP GENRES", CARD_W / 2f, genreY, genreHeaderPaint)

            val genreTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TEXT
                textSize = 34f
                textAlign = Paint.Align.CENTER
            }
            val genresStr = topGenres.joinToString("  ·  ")
            canvas.drawText(genresStr, CARD_W / 2f, genreY + 50f, genreTextPaint)
        }

        // ── Branding footer ─────────────────────────────────
        val brandY = CARD_H - 60f
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_DIM
            textSize = 28f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("$displayName · Miyo", CARD_W / 2f, brandY, brandPaint)

        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_PRIMARY }
        canvas.drawRoundRect(
            RectF(CARD_W / 2f - 40f, brandY - 30f, CARD_W / 2f + 40f, brandY - 27f),
            2f, 2f, accentPaint,
        )

        bitmap
    }

    private fun formatMinutes(minutes: Int): String {
        val days = minutes / 1440
        val hours = (minutes % 1440) / 60
        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }

    // ── Helpers ──────────────────────────────────────────────

    private suspend fun loadBitmap(context: Context, url: String?): Bitmap? {
        if (url.isNullOrEmpty()) return null
        return withContext(Dispatchers.IO) {
            try {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false) // must be software bitmap to draw on Canvas
                    .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = ""
        for (word in words) {
            val test = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(test) <= maxWidth) {
                current = test
            } else {
                if (current.isNotEmpty()) lines.add(current)
                current = word
            }
        }
        if (current.isNotEmpty()) lines.add(current)
        return lines
    }
}
