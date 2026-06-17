package com.dualvulndroid.dualvulndroid

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import java.util.concurrent.ConcurrentHashMap

class CodeSentinelAnnotator : ExternalAnnotator<String, Boolean>() {

    companion object {
        // API responses සහ දැනට සිදුවන scans track කිරීමට map දෙකක්
        private val scanResults = ConcurrentHashMap<String, Boolean>()
        private val pendingScans = ConcurrentHashMap<String, Boolean>()
    }

    override fun collectInformation(file: PsiFile): String? {
        if (file is KtFile) {
            return file.text
        }
        return null
    }

    override fun doAnnotate(collectedInfo: String?): Boolean? {
        if (collectedInfo == null) return false

        // කලින්ම scan කරලා ප්‍රතිඵල තියෙනවා නම් කෙලින්ම ඒක දෙනවා
        if (scanResults.containsKey(collectedInfo)) {
            return scanResults[collectedInfo]
        }

        // දැනටමත් මේ කෝඩ් එක scan වෙමින් පවතිනවා නම් ආයෙත් API call කරන්නේ නැහැ
        if (pendingScans.putIfAbsent(collectedInfo, true) == null) {

            // 🌐 වෙනමම background pooled thread එකක API call එක දානවා (IDE එක freeze වෙන්නේ නැහැ)
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    println("ExternalAnnotator: Fetching real API Response asynchronously...")

                    // ඔයාගේ වෙනමම තියෙන APIClient class එක මෙතනින් call වෙනවා
                    val response = APIClient().scanCode(collectedInfo)

                    val isVulnerable = response?.contains(Regex("\"vulnerable\"\\s*:\\s*true")) == true

                    // ප්‍රතිඵලය cache එකට දානවා
                    scanResults[collectedInfo] = isVulnerable
                    pendingScans.remove(collectedInfo)

                    println("ExternalAnnotator: API Scan Done! Vulnerable = $isVulnerable. Restarting Daemon for UI Highlight...")

                    // 🎨 UI එකට Highlight එක push කරන්න IntelliJ Daemon එක restart කරනවා
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

        // මුල්ම පාර API එකෙන් response එක එනකම් දැනට false දීලා UI එක block නොවී තියාගන්නවා
        return false
    }

    override fun apply(file: PsiFile, isVulnerable: Boolean?, holder: AnnotationHolder) {
        val currentText = file.text
        val actualVulnerability = scanResults[currentText] ?: false

        if (file is KtFile && actualVulnerability) {
            println("ExternalAnnotator: UI Applying warning highlight on function...")

            // File එකේ තියෙන හැම function එකක්ම check කරනවා (nested ඒවාත් එක්කම)
            val functions = PsiTreeUtil.findChildrenOfType(file, KtNamedFunction::class.java)
            val firstFunction = functions.firstOrNull()

            if (firstFunction != null) {
                val target = firstFunction.nameIdentifier ?: firstFunction
                holder.newAnnotation(HighlightSeverity.WARNING, "Vulnerability detected by CodeSentinel")
                    .range(target.textRange)
                    .create()
            }
        }
    }
}

// 🛠️ Daemon එක restart කරන්න වෙනමම තියෙන එකම Helper Object එක
object ProjectManagerHelper {
    fun restartDaemon() {
        val projects = com.intellij.openapi.project.ProjectManager.getInstance().openProjects
        for (project in projects) {
            DaemonCodeAnalyzer.getInstance(project).restart()
        }
    }
}