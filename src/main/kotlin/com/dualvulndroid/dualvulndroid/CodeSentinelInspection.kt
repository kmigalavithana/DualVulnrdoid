package com.dualvulndroid.dualvulndroid

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.KtNamedFunction

class CodeSentinelInspection : LocalInspectionTool() {

    override fun getShortName() = "CodeSentinel"
    override fun getDisplayName() = "CodeSentinel Security Scanner"
    override fun getGroupDisplayName() = "Security"

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitElement(element: com.intellij.psi.PsiElement) {
                super.visitElement(element)
                if (element is KtFile) {
                    element.accept(kotlinVisitor(holder))
                }
            }
        }
    }

    private fun kotlinVisitor(holder: ProblemsHolder): KtVisitorVoid {
        return object : KtVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                super.visitNamedFunction(function)

                val file = function.containingKtFile
                val code = file.text

                // 🚀 FIX: Network call එක UI thread එකෙන් ඉවත් කරලා Pooled (Background) Thread එකකට දානවා
                ApplicationManager.getApplication().executeOnPooledThread {
                    try {
                        println("Background Scan Started for function: ${function.name}")
                        val response = APIClient().scanCode(code)
                        println("API RESPONSE = $response")

                        val vulnerable = response?.contains(
                            Regex("\"vulnerable\"\\s*:\\s*true")
                        ) == true

                        if (vulnerable) {
                            println("VULNERABILITY FOUND! Requesting UI Update...")

                            // 🎨 FIX: Highlight එක ඇඳීම (UI update එක) ආපහු Read Action එකක් ඇතුළේ කරන්න ඕනේ
                            ApplicationManager.getApplication().runReadAction {
                                holder.registerProblem(
                                    function.nameIdentifier ?: function,
                                    "Vulnerability detected by CodeSentinel inside this function",
                                    ProblemHighlightType.WARNING
                                )
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }
}