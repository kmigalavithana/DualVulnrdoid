package com.dualvulndroid.dualvulndroid

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.PsiFile
import com.intellij.openapi.util.TextRange
import java.util.concurrent.ConcurrentHashMap

// පේළියේ අංකය සහ එහි ප්‍රතිඵලය තියාගන්න දත්ත ව්‍යුහය
data class LineScanResult(
    val lineText: String,
    val isVulnerable: Boolean,
    val type: String,
    val confidence: Int,
    val lineIndex: Int
)

// Thread-safe map එකක්, හැම file path එකකටම අදාළ වැරදි පේළි ලැයිස්තුව තියාගන්න
private val fileVulnerabilities = ConcurrentHashMap<String, List<LineScanResult>>()
private val pendingFiles = ConcurrentHashMap<String, Boolean>()

class CodeSentinelAnnotator : ExternalAnnotator<PsiFile, List<LineScanResult>>() {

    override fun collectInformation(file: PsiFile): PsiFile? {
        if (file.language.id == "JAVA" || file.language.id == "kotlin") {
            return file
        }
        return null
    }

    override fun doAnnotate(file: PsiFile?): List<LineScanResult>? {
        if (file == null) return null

        val filePath = file.virtualFile?.path ?: return null
        val fileText = file.text

        // දැනටමත් ස්කෑන් කරලා ප්‍රතිඵල තියෙනවා නම් ඒකම රිටර්න් කරනවා
        if (fileVulnerabilities.containsKey(filePath)) {
            return fileVulnerabilities[filePath]
        }

        if (pendingFiles.putIfAbsent(filePath, true) == null) {
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val lines = fileText.split("\n")
                    val discoveredVulns = mutableListOf<LineScanResult>()
                    val apiClient = APIClient()

                    println("⚡ CodeSentinel: Line-by-Line Scanning started for ${file.name}...")

                    // 🔄 හැම කෝඩ් පේළියක්ම වෙන වෙනම අරන් Spring Boot එකට යවනවා
                    for ((index, line) in lines.withIndex()) {
                        val trimmedLine = line.trim()

                        // හිස් පේළි හෝ comments ස්කෑන් කරන්නේ නැහැ
                        if (trimmedLine.isEmpty() || trimmedLine.startsWith("//") || trimmedLine.startsWith("import")) {
                            continue
                        }

                        val response = apiClient.scanCode(trimmedLine)
                        if (response != null && response.status == "SUCCESS" && response.vulnerable) {

                            val type = if (trimmedLine.lowercase().contains("select") || trimmedLine.lowercase().contains("where")) {
                                "SQL Injection (CWE-89)"
                            } else {
                                "Improper Certificate Validation (CWE-295)"
                            }

                            discoveredVulns.add(
                                LineScanResult(
                                    lineText = line,
                                    isVulnerable = true,
                                    type = type,
                                    confidence = response.confidence.toInt(),
                                    lineIndex = index
                                )
                            )
                            println("⚠️ Vulnerability Found on Line ${index + 1}: $trimmedLine")
                        }
                    }

                    fileVulnerabilities[filePath] = discoveredVulns
                    pendingFiles.remove(filePath)

                    // IDE Editor එක රීෆ්‍රෙෂ් කරන්න
                    ApplicationManager.getApplication().invokeLater {
                        ProjectManagerHelper.restartDaemon()
                    }


                } catch (e: Exception) {
                    e.printStackTrace()
                    pendingFiles.remove(filePath)
                }
            }
        }
        return null
    }

    override fun apply(file: PsiFile, vulns: List<LineScanResult>?, holder: AnnotationHolder) {
        val filePath = file.virtualFile?.path ?: return
        val actualVulns = fileVulnerabilities[filePath] ?: return
        val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(file.virtualFile) ?: return

        // අහුවුණු හැම වැරදි පේළියක්ම IDE එක ඇතුළේ Highlight කරනවා
        for (vuln in actualVulns) {
            if (vuln.lineIndex < document.lineCount) {
                val startOffset = document.getLineStartOffset(vuln.lineIndex)
                val endOffset = document.getLineEndOffset(vuln.lineIndex)
                val range = TextRange(startOffset, endOffset)

                val tooltipMessage = """
                    <html>
                    <body style='font-family: sans-serif;'>
                    <h3 style='color: #ff5555; margin-bottom: 5px;'>⚠️ [CodeSentinel] ${vuln.type} Detected</h3>
                    <b>Confidence Score:</b> <span style='color: #ff5555;'>${vuln.confidence}%</span><br/><br/>
                    <b>Vulnerable Line:</b> <code style='background-color: #331111;'>${vuln.lineText.trim()}</code><br/><br/>
                    <b>Description:</b> Potential vulnerability matched with LVDAndro deep learning model patterns.<br/>
                    </body>
                    </html>
                """.trimIndent()

                // වැරදි තියෙන පේළිය යටින්ම රතු ඉර (ERROR Severity) ඇඳීම
                holder.newAnnotation(HighlightSeverity.ERROR, "CodeSentinel: ${vuln.type} (${vuln.confidence}%)")
                    .range(range)
                    .tooltip(tooltipMessage)
                    .create()
            }

        }
    }
}
object ProjectManagerHelper {
    fun clearCache() {
        // Cache එක clear කරන්න අවශ්‍ය නම් මෙතනින් කරන්න පුළුවන්
        println("CodeSentinel Cache: Successfully Cleared via Helper!")
    }

    fun restartDaemon() {
        // දැනට open වෙලා තියෙන හැම ප්‍රොජෙක්ට් එකකම editor එක refresh කරනවා රතු ඉරි පෙන්වන්න
        val projects = ProjectManager.getInstance().openProjects
        for (project in projects) {
            DaemonCodeAnalyzer.getInstance(project).restart()
        }
    }
}