package com.dualvulndroid.dualvulndroid

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import com.intellij.psi.PsiMethod // Java methods සඳහා
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

// Backend එකෙන් එන දත්ත මතක තියාගන්න හදන Class එකක්
data class ScanResult(
    val isVulnerable: Boolean,
    val type: String,
    val explanation: String,
    val confidence: Int
)

// 🚀 FIX 2: Memory leak warning එක නැති කරන්න Cache එක Class එකෙන් පිටත Top-Level එකක් කලා
private val scanResults = ConcurrentHashMap<String, ScanResult>()
private val pendingScans = ConcurrentHashMap<String, Boolean>()

class CodeSentinelAnnotator : ExternalAnnotator<String, ScanResult>() {

    override fun collectInformation(file: PsiFile): String? {
        // 🔄 FIX: Kotlin (.kt) සහ Java (.java) ෆයිල් දෙකම ලස්සනට හඳුනාගන්නවා
        if (file is KtFile || file.language.id == "JAVA") {
            return file.text
        }
        return null
    }

    override fun doAnnotate(collectedInfo: String?): ScanResult? {
        if (collectedInfo == null) return null

        if (scanResults.containsKey(collectedInfo)) {
            return scanResults[collectedInfo]
        }

        if (pendingScans.putIfAbsent(collectedInfo, true) == null) {
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    println("ExternalAnnotator: Fetching full security metrics...")
                    val response = APIClient().scanCode(collectedInfo) ?: ""

                    val isVulnerable = response.contains("\"vulnerable\":true")
                    val type = extractJsonValue(response, "vulnerabilityType") ?: "Security Flaw"
                    val explanation = extractJsonValue(response, "explanation") ?: "Potential vulnerability detected."
                    val confidence = extractJsonInt(response, "confidence")

                    val result = ScanResult(isVulnerable, type, explanation, confidence)
                    scanResults[collectedInfo] = result
                    pendingScans.remove(collectedInfo)

                    ApplicationManager.getApplication().invokeLater {
                        ApplicationManager.getApplication().runWriteAction {
                            ProjectManagerHelper.restartDaemon()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    pendingScans.remove(collectedInfo)
                }
            }
        }
        return null
    }

    override fun apply(file: PsiFile, result: ScanResult?, holder: AnnotationHolder) {
        val currentText = file.text
        val actualResult = scanResults[currentText] ?: return

        if (actualResult.isVulnerable) {

            // 📝 Tooltip message එක HTML වලින් හදාගන්නවා
            val tooltipMessage = """
                <html>
                <b>[CodeSentinel] ${actualResult.type} Detected</b><br/>
                <font color='red'>Confidence Score: ${actualResult.confidence}%</font><br/><br/>
                <i>Explanation:</i> ${actualResult.explanation}
                </html>
            """.trimIndent()

            // 1. ජාවා ෆයිල් එකක් නම් highlight කරන ක්‍රමය
            if (file.language.id == "JAVA") {
                val methods = PsiTreeUtil.findChildrenOfType(file, PsiMethod::class.java)
                val firstMethod = methods.firstOrNull()
                if (firstMethod != null) {
                    val target = firstMethod.nameIdentifier ?: firstMethod
                    holder.newAnnotation(HighlightSeverity.ERROR, "${actualResult.type} (${actualResult.confidence}%)")
                        .range(target.textRange)
                        .tooltip(tooltipMessage)
                        .create()
                }
            }
            // 2. කොට්ලින් ෆයිල් එකක් නම් highlight කරන ක්‍රමය
            else if (file is KtFile) {
                val functions = PsiTreeUtil.findChildrenOfType(file, KtNamedFunction::class.java)
                val firstFunction = functions.firstOrNull()
                if (firstFunction != null) {
                    val target = firstFunction.nameIdentifier ?: firstFunction
                    val annotation = holder.newAnnotation(HighlightSeverity.ERROR, "${actualResult.type} (${actualResult.confidence}%)")
                        .range(target.textRange)
                        .tooltip(tooltipMessage)

                    if (actualResult.type.contains("SQL", ignoreCase = true)) {
                        annotation.withFix(SQLQuickFix(firstFunction))
                    }
                    annotation.create()
                }
            }
        }
    }

    private fun extractJsonValue(json: String, key: String): String? {
        val pattern = Pattern.compile("\"$key\"\\s*:\\s*\"([^\"]+)\"")
        val matcher = pattern.matcher(json)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun extractJsonInt(json: String, key: String): Int {
        val pattern = Pattern.compile("\"$key\"\\s*:\\s*(\\d+)")
        val matcher = pattern.matcher(json)
        return if (matcher.find()) matcher.group(1).toInt() else 0
    }
}

// 🚀 FIX 1: හැලීලා තිබ්බ ProjectManagerHelper Object එක ආපහු එකතු කලා
object ProjectManagerHelper {
    fun restartDaemon() {
        val projects = ProjectManager.getInstance().openProjects
        for (project in projects) {
            DaemonCodeAnalyzer.getInstance(project).restart()
        }
    }
}