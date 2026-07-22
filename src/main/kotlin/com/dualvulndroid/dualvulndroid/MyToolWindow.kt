package com.dualvulndroid.dualvulndroid

import com.dualvulndroid.dualvulndroid.APIClient
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import java.io.File
import javax.swing.JButton

class MyToolWindowFactory : ToolWindowFactory {

    override fun shouldBeAvailable(project: Project) = true

    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow
    ) {

        val myToolWindow = MyToolWindow(project)

        val content = ContentFactory.getInstance()
            .createContent(
                myToolWindow.getContent(),
                null,
                false
            )

        toolWindow.contentManager.addContent(content)
    }

    class MyToolWindow(
        private val project: Project
    ) {

        private val label =
            JBLabel("CodeSentinel Ready")

        private val testButton =
            JButton("Scan Project")

        private val content =
            JBPanel<JBPanel<*>>().apply {

                add(label)
                add(testButton)

                testButton.addActionListener {

                    val basePath = project.basePath

                    if (basePath == null) {
                        label.text = "Project path not found!"
                        return@addActionListener
                    }

                    val sourceFolder =
                        File("$basePath/app/src/main/java")

                    if (!sourceFolder.exists()) {
                        label.text =
                            "Source folder not found!"
                        return@addActionListener
                    }

                    val apiClient =
                        APIClient()

                    var vulnerabilityFound = false

                    sourceFolder.walkTopDown()
                        .filter {
                            it.isFile &&
                                    (it.extension == "kt" || it.extension == "java")                        }
                        .forEach { file ->

                            val code =
                                file.readText()

                            val response =
                                apiClient.scanCode(code)

                            println(response)

                            if (
                                response != null &&
                                response?.vulnerable == true
                            ) {

                                vulnerabilityFound = true

                                label.text = """
<html>
<b>⚠ Vulnerability Detected</b><br><br>
File: ${file.name}<br>
Type: ${response.vulnerabilityType}<br>
Prediction: ${response.prediction}<br>
Confidence: ${"%.2f".format(response.confidence)}%<br><br>
${response.explanation}
</html>
""".trimIndent()

                                return@addActionListener
                            }
                        }

                    if (!vulnerabilityFound) {

                        label.text = """
<html>
<b>✓ Scan Completed</b><br>
No vulnerabilities detected.
</html>
""".trimIndent()
                    }
                }
            }

        fun getContent():
                JBPanel<JBPanel<*>> = content
    }
}
