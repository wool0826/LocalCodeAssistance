package com.github.wool0826.localcodeassistant.action

import com.github.wool0826.localcodeassistant.api.ApiClient
import com.github.wool0826.localcodeassistant.api.OllamaApiClient
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import javax.swing.JEditorPane
import javax.swing.JScrollPane

class AskAction : AnAction() {
    private val apiClient: ApiClient = OllamaApiClient()
    private val markDownParser = Parser.builder().build()
    private val htmlRenderer = HtmlRenderer.builder().build()

    override fun actionPerformed(e: AnActionEvent) {
        val project: Project? = e.project
        val editor: Editor? = e.getData(com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR)

        if (editor != null) {
            val selectedFile = FileEditorManager.getInstance(project!!).selectedFiles.firstOrNull()
            val selectedText = editor.selectionModel.selectedText

            if (!selectedText.isNullOrEmpty()) {
                val prompt =
                    selectedText
                        .trimQuery()
                        .validateQuery()
                        .replaceCommandInQuery()

                object : Task.Backgroundable(project, "Just a moment, the assistant is processing your request...") {
                    override fun run(indicator: ProgressIndicator) {
                        val result = apiClient.autoComplete(targetFile = selectedFile, prompt = prompt)

                        val document = markDownParser.parse(result)
                        val htmlText = makeHtmlStylish(htmlRenderer.render(document))

                        ToolWindowManager.getInstance(project).invokeLater {
                            // htmlText 가 포함된 Panel 생성
                            val scrollPane =
                                JScrollPane(
                                    JEditorPane(
                                        "text/html",
                                        htmlText
                                    ).apply { isEditable = false }
                                )

                            val panel = SimpleToolWindowPanel(true, true).apply { setContent(scrollPane) }

                            val toolWindowManager = ToolWindowManager.getInstance(project)
                            val toolWindow =
                                toolWindowManager.getToolWindow("Response from the assistant") ?: throw Exception()

                            val contentFactory = ContentFactory.getInstance()
                            val content = contentFactory.createContent(panel.content, "", false)

                            val contentManagerInToolWindow = toolWindow.contentManager
                            contentManagerInToolWindow.removeAllContents(true)
                            contentManagerInToolWindow.addContent(content)

                            toolWindow.show()
                        }
                    }
                }.queue()
            }
        } else {
            Messages.showMessageDialog(project, "No text selected", "Error", Messages.getErrorIcon())
        }
    }

    private fun String.trimQuery(): List<String> {
        val lines = this.split("\n")
        return lines.map { it.trim() }
    }

    private fun List<String>.validateQuery(): List<String> {
        if (this.isEmpty()) {
            throw IllegalArgumentException()
        }

        val commandLine = this.first()
        if (!commandLine.startsWith("//")) {
            ApplicationManager.getApplication().invokeLater {
                Messages.showErrorDialog("You must use comment on the first line", "Error")
            }

            throw IllegalArgumentException("must use comment on the first line")
        }

        return this
    }

    private fun List<String>.replaceCommandInQuery(): String {
        val commandLine = this.first()
        val codeSnippets = this.drop(1).joinToString(separator = "\n")

        val replacedCommand: String =
            when {
                commandLine.contains("@review") -> "Review the code below."
                commandLine.contains("@refactor") -> "Refactor the code below."
                commandLine.contains("@rename") -> "Suggest a name of all variables in the code below."
                commandLine.contains("@performance") -> "Check performance issues the code below."
                commandLine.contains("@security") -> "Check security issues the code below."
                else -> return commandLine
            }

        return "$replacedCommand\n$codeSnippets"
    }

    private fun makeHtmlStylish(plainHtml: String): String {
        return """
            <html>
            <head>
                <style>
                    body {
                        font-family: Arial, sans-serif;
                        line-height: 1.6;
                        padding: 10px;
                        color: #333;
                        background-color: #f9f9f9;
                    }
                    h1, h2, h3 {
                        color: #1a73e8;
                    }
                    pre {
                        background-color: #f4f4f4;
                        padding: 10px;
                        border-radius: 5px;
                    }
                    code {
                        background-color: #f4f4f4;
                        padding: 2px 4px;
                        border-radius: 3px;
                        font-family: monospace;
                    }
                    blockquote {
                        margin: 10px 0;
                        padding: 10px;
                        background-color: #f9f9f9;
                        border-left: 5px solid #ccc;
                    }
                    table {
                        width: 100%;
                        border-collapse: collapse;
                        margin: 10px 0;
                    }
                    table, th, td {
                        border: 1px solid #ddd;
                    }
                    th, td {
                        padding: 8px;
                        text-align: left;
                    }
                    th {
                        background-color: #f2f2f2;
                    }
                </style>
            </head>
            <body>
                $plainHtml
            </body>
            </html>
        """.trimIndent()
    }
}
