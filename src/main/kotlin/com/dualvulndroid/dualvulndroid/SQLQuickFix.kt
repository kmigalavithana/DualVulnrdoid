package com.dualvulndroid.dualvulndroid

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory

class SQLQuickFix(element: KtNamedFunction) : LocalQuickFixAndIntentionActionOnPsiElement(element) {

    override fun getText(): String = "CodeSentinel: Replace with Parameterized Query (Fix SQL Injection)"
    override fun getFamilyName(): String = text

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement
    ) {
        if (startElement !is KtNamedFunction) return

        val secureCode = """
            fun login(username: String) {
                val query = "SELECT * FROM users WHERE name = ?"
                val selectionArgs = arrayOf(username)
                rawQuery(query, selectionArgs)
            }
        """.trimIndent()

        try {
            val ktPsiFactory = KtPsiFactory(project)
            val newFunction = ktPsiFactory.createFunction(secureCode)

            startElement.replace(newFunction)

            // 🚀 Safe විදිහට Document එක commit කරලා Helper එක හරහා Cache clear කරනවා
            ApplicationManager.getApplication().invokeLater {
                PsiDocumentManager.getInstance(project).commitAllDocuments()
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}