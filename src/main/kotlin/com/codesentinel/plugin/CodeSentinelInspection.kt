package com.codesentinel.plugin

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethod

class CodeSentinelInspection : LocalInspectionTool() {

    // 🚀 plugin.xml එකේ language="UAST" එකත් එක්ක match වෙන්න shortName එක දෙනවා
    override fun getShortName(): String = "CodeSentinelInspection"

    override fun getDisplayName(): String = "CodeSentinel Security Scanner"
    override fun getGroupDisplayName(): String = "Security"

    // 💡 HTML description file එකක් වෙනුවට කෝඩ් එකෙන්ම static text එකක් සෙට් කරනවා
    override fun getStaticDescription(): String? {
        return "CodeSentinel security analysis tool targeting injection vulnerabilities and SSL flaws."
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {

            // 🛡️ UAST (Universal Abstract Syntax Tree) නිසා Java/Kotlin methods දෙකම මෙතනින් handle වෙනවා
            override fun visitElement(element: PsiElement) {
                super.visitElement(element)

                if (element is PsiMethod) {
                    // 🚀 Real-time scanning, network calls සහ UI highlights (රතු ඉරි)
                    // සේරම අපේ CodeSentinelAnnotator එකෙන් පට්ටම ලස්සනට backgroundthread එකකින්
                    // කරන නිසා Inspection එක සරලව තබා ගනී.
                    // මේකෙන් IDE එක freeze වෙන එක 100%ක්ම වැළකෙනවා!
                }
            }
        }
    }

    override fun isEnabledByDefault(): Boolean = true
}