package com.dualvulndroid.dualvulndroid

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.psi.KtFile // අලුතින් දාන්න
import org.jetbrains.kotlin.psi.KtVisitorVoid // අලුතින් දාන්න

class CodeSentinelInspection : LocalInspectionTool() {

    override fun getShortName() = "CodeSentinel"
    override fun getDisplayName() = "CodeSentinel Security Scanner"
    override fun getGroupDisplayName() = "Security"

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): PsiElementVisitor {

        // KtVisitorVoid පාවිච්චි කරලා PsiElementVisitor එකක් විදිහට return කරනවා
        return object : KtVisitorVoid() {

            // visitFile වෙනුවට Kotlin files සඳහා visitKtFile පාවිච්චි කරනවා
            override fun visitKtFile(file: KtFile) {
                super.visitKtFile(file)

                println("VISITING FILE = ${file.name}")
                val code = file.text

                try {
                    val response = APIClient().scanCode(code)
                    println("API RESPONSE = $response")

                    val vulnerable = response?.contains(
                        Regex("\"vulnerable\"\\s*:\\s*true")
                    ) == true

                    println("VULNERABLE = $vulnerable")

                    if (vulnerable) {
                        println("REGISTERING PROBLEM")
                        holder.registerProblem(
                            file,
                            "Vulnerability detected by CodeSentinel",
                            ProblemHighlightType.WARNING
                        )
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}