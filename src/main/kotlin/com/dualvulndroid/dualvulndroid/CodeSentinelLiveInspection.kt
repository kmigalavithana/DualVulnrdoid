package com.dualvulndroid.dualvulndroid

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile

class CodeSentinelLiveInspection : LocalInspectionTool() {

    override fun getShortName(): String = "CodeSentinelLiveInspection"

    override fun getDisplayName(): String = "CodeSentinel Security Scanner"

    override fun getGroupDisplayName(): String = "Security"

    override fun getStaticDescription(): String? {
        return "CodeSentinel AI-based vulnerability detection for Java and Kotlin code"
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                super.visitFile(file)

                val lang = file.language.id.lowercase()
                if (lang != "java" && lang != "kotlin") return

                val path = file.virtualFile?.path ?: return

                println("[INSPECTION] Scanning file: ${file.name}")

                try {
                    val api = APIClient()
                    val response = api.scanCode(file.text)

                    if (response != null && response.vulnerable) {
                        println("[INSPECTION] ✓ VULNERABILITY FOUND: ${response.vulnerabilityType}")

                        // Highlight the entire file
                        val problems = mutableListOf<Pair<PsiElement, String>>()

                        // Find the first meaningful code element
                        var targetElement: PsiElement? = file.firstChild
                        while (targetElement != null) {
                            val text = targetElement.text.trim()
                            if (text.isNotEmpty() && 
                                !text.startsWith("package") && 
                                !text.startsWith("import") &&
                                !text.startsWith("//")) {
                                break
                            }
                            targetElement = targetElement.nextSibling
                        }

                        if (targetElement != null) {
                            holder.registerProblem(
                                targetElement,
                                "${response.vulnerabilityType} (${String.format("%.2f", response.confidence)}%) - ${response.explanation}",
                                com.intellij.codeInspection.ProblemHighlightType.ERROR
                            )
                            println("[INSPECTION] ✓ Problem registered on element")
                        } else {
                            // If no specific element found, register on the file itself
                            holder.registerProblem(
                                file,
                                "${response.vulnerabilityType} (${String.format("%.2f", response.confidence)}%) - ${response.explanation}",
                                com.intellij.codeInspection.ProblemHighlightType.ERROR
                            )
                            println("[INSPECTION] ✓ Problem registered on file")
                        }
                    } else {
                        println("[INSPECTION] ✓ File is safe")
                    }

                } catch (e: Exception) {
                    println("[INSPECTION] ERROR: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }

    override fun isEnabledByDefault(): Boolean = true
}
