package com.dualvulndroid.dualvulndroid

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
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

        // 🚀 Android වලට ආරක්ෂිත Parameterized Query කේතය (Template එක) auto-generate කරනවා
        val secureCode = """
            fun login(username: String) {
                // Secure: Using positional placeholders (?) instead of concatenation
                val query = "SELECT * FROM users WHERE name = ?"
                val selectionArgs = arrayOf(username)
                
                rawQuery(query, selectionArgs)
            }
        """.trimIndent()

        // IntelliJ PSI factory එකෙන් අලුත් ආරක්ෂිත function එකක් object එකක් විදිහට හදනවා
        val ktPsiFactory = KtPsiFactory(project)
        val newFunction = ktPsiFactory.createFunction(secureCode)

        // පරණ අනාරක්ෂිත function එක වෙනුවට අලුත් ආරක්ෂිත එක replace කරනවා!
        startElement.replace(newFunction)
    }
}