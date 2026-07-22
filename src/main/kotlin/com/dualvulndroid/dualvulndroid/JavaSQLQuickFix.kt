package com.dualvulndroid.dualvulndroid

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiDocumentManager

class JavaSQLQuickFix(element: PsiMethod) : LocalQuickFixAndIntentionActionOnPsiElement(element) {

    override fun getText(): String = "CodeSentinel: Replace with Parameterized Query (Fix Java SQL Injection)"
    override fun getFamilyName(): String = text

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement
    ) {
        if (startElement !is PsiMethod) return

        // 🚀 FIX: Java වලට Arguments 2ම ගන්න විදිහටම ආරක්ෂිත කෝඩ් එක Generate කරනවා
        val secureJavaCode = """
            public void login(String username) {
                String query = "SELECT * FROM users WHERE name = ?";
                String[] selectionArgs = new String[]{ username };
                rawQuery(query, selectionArgs);
            }
        """.trimIndent()

        try {
            val factory = PsiElementFactory.getInstance(project)
            val newMethod = factory.createMethodFromText(secureJavaCode, startElement.context)

            // 1. පැරණි අනාරක්ෂිත Java method එක වෙනුවට අලුත් එක replace කරනවා
            startElement.replace(newMethod)

            // 2. 🚀 Commit and Clear Cache instantly
            ApplicationManager.getApplication().invokeLater {
                PsiDocumentManager.getInstance(project).commitAllDocuments()
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}