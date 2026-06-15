package com.dualvulndroid.dualvulndroid

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile

class CodeSentinelInspection :  LocalInspectionTool() {

    init {
        println("CodeSentinel Inspection Loaded")
    }
    override fun getShortName(): String {
        return "CodeSentinel"
    }

    override fun getDisplayName(): String {
        return "CodeSentinel Security Scanner"
    }

    override fun getGroupDisplayName(): String {
        return "Security"
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): PsiElementVisitor {

        return object : PsiElementVisitor() {

            override fun visitFile(file: PsiFile) {

                val code = file.text

                try {

                    val response =
                        APIClient().scanCode(code)

                    if (
                        response != null &&
                        response.contains("\"vulnerable\":true")
                    ) {

                        holder.registerProblem(
                            file,
                            "Vulnerability detected by CodeSentinel",
                            ProblemHighlightType.WARNING
                        )
                    }

                } catch (e: Exception) {

                    println(
                        "CodeSentinel Error: ${e.message}"
                    )
                }
            }
        }
    }
}