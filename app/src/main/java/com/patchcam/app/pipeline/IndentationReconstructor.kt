package com.patchcam.app.pipeline

import android.graphics.Rect
import com.patchcam.app.models.OcrLineItem
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Stage 2 — Geometry-based indentation reconstruction (CODE crop only)
 * Collects boundingBox.left for every recognized line, clusters distinct x-positions
 * into indentation steps using character-width tolerance, and reconstructs 4 spaces per step.
 */
object IndentationReconstructor {

    data class Cluster(
        var representativeX: Float,
        var minX: Float,
        var maxX: Float,
        var count: Int,
        var level: Int = 0
    )

    fun reconstruct(lines: List<OcrLineItem>): String {
        val nonEmpty = lines.filter { it.text.trim().isNotEmpty() }
        if (nonEmpty.isEmpty()) return ""

        // 1. Calculate average character width across non-empty lines
        var totalChars = 0
        var totalWidth = 0
        nonEmpty.forEach { item ->
            val len = item.text.trim().length
            if (len > 0 && item.boundingBox.width() > 0) {
                totalChars += len
                totalWidth += item.boundingBox.width()
            }
        }
        val avgCharWidth = if (totalChars > 0) max(8f, totalWidth.toFloat() / totalChars) else 14f
        val tolerance = max(14f, avgCharWidth * 2.2f)

        // 2. Collect left-edge x-coordinate of every line
        val sortedX = nonEmpty.map { it.boundingBox.left.toFloat() }.sorted()

        // 3. Cluster distinct x-positions into indentation steps
        val clusters = mutableListOf<Cluster>()
        for (x in sortedX) {
            val existing = clusters.firstOrNull { abs(it.representativeX - x) <= tolerance }
            if (existing != null) {
                existing.minX = minOf(existing.minX, x)
                existing.maxX = maxOf(existing.maxX, x)
                existing.count++
                existing.representativeX = (existing.minX + existing.maxX) / 2f
            } else {
                clusters.add(Cluster(representativeX = x, minX = x, maxX = x, count = 1))
            }
        }

        clusters.sortBy { it.representativeX }
        val baseCluster = clusters.first()
        val expectedStepWidth = max(avgCharWidth * 3.5f, tolerance * 1.5f)

        clusters.forEach { c ->
            val diff = maxOf(0f, c.representativeX - baseCluster.representativeX)
            c.level = (diff / expectedStepWidth).roundToInt()
        }

        // Monotonic level enforcement
        for (i in 1 until clusters.size) {
            if (clusters[i].level <= clusters[i - 1].level) {
                clusters[i].level = clusters[i - 1].level + 1
            }
        }

        // 4. Rebuild each line using consistent 4-space indentation per detected level
        val builder = StringBuilder()
        for (line in lines) {
            val trimmed = line.text.trim()
            if (trimmed.isEmpty()) {
                builder.append("\n")
                continue
            }
            val lineX = line.boundingBox.left.toFloat()
            val closest = clusters.minByOrNull { abs(it.representativeX - lineX) } ?: baseCluster
            val indentSpaces = " ".repeat(closest.level * 4)
            builder.append(indentSpaces).append(trimmed).append("\n")
        }

        return builder.toString().trimEnd()
    }
}
