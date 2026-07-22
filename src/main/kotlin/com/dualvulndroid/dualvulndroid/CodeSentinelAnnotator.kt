package com.dualvulndroid.dualvulndroid

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import java.util.concurrent.ConcurrentHashMap

data class FileScanData(
    val path: String,
    val fileText: String,
    val lines: List<LineData>
)

data class LineData(
    val index: Int,
    val text: String,
    val startOffset: Int,
    val endOffset: Int
)

data class LineScanResult(
    val lineText: String,
    val vulnerabilityType: String,
    val confidence: Double,
    val startOffset: Int,
    val endOffset: Int
)

class CodeSentinelAnnotator : ExternalAnnotator<FileScanData, List<LineScanResult>>() {

    companion object {
        private val cache = ConcurrentHashMap<String, List<LineScanResult>>()
        private val scanning = ConcurrentHashMap.newKeySet<String>()
    }

    override fun collectInformation(file: PsiFile): FileScanData? {
        val lang = file.language.id.lowercase()
        if (lang != "java" && lang != "kotlin") return null

        val path = file.virtualFile?.path ?: return null
        val document: Document = FileDocumentManager.getInstance().getDocument(file.virtualFile) ?: return null
        val fileText = file.text

        val linesData = mutableListOf<LineData>()
        val lines = fileText.lines()

        for ((index, line) in lines.withIndex()) {
            val code = line.trim()
            if (code.isBlank() || code.startsWith("//") || code.startsWith("package") || code.startsWith("import")) {
                continue
            }
            if (index < document.lineCount) {
                linesData.add(
                    LineData(
                        index = index,
                        text = code,
                        startOffset = document.getLineStartOffset(index),
                        endOffset = document.getLineEndOffset(index)
                    )
                )
            }
        }

        return FileScanData(path, fileText, linesData)
    }

    override fun doAnnotate(collectedData: FileScanData?): List<LineScanResult>? {
        println("[ANNOTATE] Starting scan...")
        if (collectedData == null) return null

        val path = collectedData.path
        if (!scanning.add(path)) return cache[path]

        val api = APIClient()
        val results = mutableListOf<LineScanResult>()

        try {
            // Scan entire file at once
            val response = api.scanCode(collectedData.fileText)

            if (response != null && response.vulnerable) {
                println("[ANNOTATE] ✓ Vulnerability detected: ${response.vulnerabilityType}")

                // If backend provides specific line detections, use them
                if (response.detections.isNotEmpty()) {
                    response.detections.forEach { detection ->
                        val line = collectedData.lines.find { it.index == detection.line }
                        if (line != null) {
                            results.add(
                                LineScanResult(
                                    lineText = line.text,
                                    vulnerabilityType = detection.type,
                                    confidence = response.confidence,
                                    startOffset = line.startOffset,
                                    endOffset = line.endOffset
                                )
                            )
                        }
                    }
                } else {
                    // No specific detections - highlight entire file
                    results.add(
                        LineScanResult(
                            lineText = "File contains ${response.vulnerabilityType}",
                            vulnerabilityType = response.vulnerabilityType,
                            confidence = response.confidence,
                            startOffset = 0,
                            endOffset = collectedData.fileText.length
                        )
                    )
                }
            }

            cache[path] = results
            return results

        } catch (e: Exception) {
            e.printStackTrace()
            return cache[path]
        } finally {
            scanning.remove(path)
        }
    }

    override fun apply(
        file: PsiFile,
        annotationResult: List<LineScanResult>?,
        holder: AnnotationHolder
    ) {
        val path = file.virtualFile?.path ?: return
        val results = annotationResult ?: cache[path] ?: return

        for (result in results) {
            // Validate range
            if (result.startOffset < 0 || result.endOffset > file.textLength || result.startOffset >= result.endOffset) {
                continue
            }

            val tooltip = """
            <html>
            <b>⚠ CodeSentinel Security Detection</b>
            <br><br>
            <b>Vulnerability Type :</b> ${result.vulnerabilityType}
            <br>
            <b>Confidence :</b> ${"%.2f".format(result.confidence)}%
            <br><br>
            <b>Code :</b>
            <pre>${result.lineText.trim()}</pre>
            </html>
            """.trimIndent()

            holder.newAnnotation(
                HighlightSeverity.ERROR,
                "${result.vulnerabilityType} (${"%.2f".format(result.confidence)}%)"
            )
                .range(TextRange(result.startOffset, result.endOffset))
                .tooltip(tooltip)
                .needsUpdateOnTyping(true)
                .create()
        }

        // Force IDE to repaint the file with annotations
        val project = file.project
        if (!project.isDisposed) {
            DaemonCodeAnalyzer.getInstance(project).restart(file)
            println("[APPLY] ✓ Daemon restarted, annotations should now be visible")
        }
    }

    object ProjectManagerHelper {
        fun clearCache() {
            cache.clear()
            println("✅ CodeSentinel cache cleared")
        }

        fun restartDaemon() {
            ProjectManager.getInstance().openProjects.forEach {
                DaemonCodeAnalyzer.getInstance(it).restart()
            }
        }
    }
}
