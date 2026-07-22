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

// 1. collectInformation එකෙන් doAnnotate එකට Data පාස් කරන්න හදන Class එක
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

// 2. ඩිටෙක්ට් වෙන රිසල්ට් එක
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

    // 🎯 පළවෙනි පියවර: UI/Read Thread එකේදී ආරක්ෂිතව ෆයිල් එකේ Data ටික විතරක් එකතු කරගන්නවා
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

    // 🎯 දෙවැනි පියවර: Background Thread එකේදී Read Action බ්ලොක් නැතුව API Call එක විතරක් කරනවා
    override fun doAnnotate(collectedData: FileScanData?): List<LineScanResult>? {
        println("ANNOTATOR RUNNING BACKGROUND API CALLS")
        if (collectedData == null) return null

        val path = collectedData.path
        if (!scanning.add(path)) return cache[path]

        val api = APIClient()
        val results = mutableListOf<LineScanResult>()

        try {
            for (line in collectedData.lines) {
                // CodeBERT බැක්එන්ඩ් එකට Call එකක් යනවා
                val response = api.scanCode(line.text)

                if (response != null && response.vulnerable) {
                    results.add(
                        LineScanResult(
                            lineText = line.text,
                            vulnerabilityType = response.vulnerabilityType,
                            confidence = response.confidence,
                            startOffset = line.startOffset,
                            endOffset = line.endOffset
                        )
                    )
                    println("Detected: ${response.vulnerabilityType}")
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

    // 🎯 තුන්වැනි පියවර: UI Thread එකේදී ඇනෝටේෂන්ස් ටික IDE එකට දානවා
    override fun apply(
        file: PsiFile,
        annotationResult: List<LineScanResult>?,
        holder: AnnotationHolder
    ) {
        val path = file.virtualFile?.path ?: return
        val results = annotationResult ?: cache[path] ?: return

        for (result in results) {
            if (result.startOffset < 0 || result.endOffset > file.textLength || result.startOffset >= result.endOffset) {
                continue
            }

            val tooltip = """
            <html>
            <b>⚠ CodeSentinel AI Detection</b>
            <br><br>
            <b>Type :</b> ${result.vulnerabilityType}
            <br>
            <b>Confidence :</b> ${"%.2f".format(result.confidence)}%
            <br><br>
            <b>Line :</b>
            <pre>${result.lineText.trim()}</pre>
            </html>
            """.trimIndent()

            holder.newAnnotation(
                HighlightSeverity.ERROR,
                "${result.vulnerabilityType} (${"%.2f".format(result.confidence)}%)"
            )
                .range(TextRange(result.startOffset, result.endOffset))
                .tooltip(tooltip)
                .create()
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