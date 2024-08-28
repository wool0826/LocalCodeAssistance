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
import javax.swing.ScrollPaneConstants

class AskAction : AnAction() {
    private val apiClient: ApiClient = OllamaApiClient()
    private val markDownParser = Parser.builder().build()
    private val htmlRenderer = HtmlRenderer.builder().build()

    private val keywordToQueryMap =
        mapOf(
            "@review" to "Review the code below.",
            "@refactor" to "Refactor the code below.",
            "@rename" to "Suggest a name of all variables in the code below.",
            "@performance" to "Check performance issues the code below.",
            "@security" to "Check security issues the code below.",
        )

    override fun actionPerformed(e: AnActionEvent) {
        val project: Project? = e.project
        val editor: Editor? = e.getData(com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR)

        if (editor == null) {
            throwExceptionWithMessageDialog("No text selected")
        }

        val selectedFile = FileEditorManager.getInstance(project!!).selectedFiles.firstOrNull()
        val selectedText = requireNotNull(editor).selectionModel.selectedText

        if (selectedText.isNullOrEmpty()) {
            return
        }

        val prompt =
            selectedText
                .split("\n")
                .map { it.trim() }
                .validateQueries()
                .replaceKeywordsToQuery()

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
                        ).apply {
                            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
                        }

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

    private fun List<String>.validateQueries(): List<String> {
        require(this.isNotEmpty()) { "List of query cannot be empty" }

        val commandLine = this.first()
        if (!commandLine.startsWith("//")) {
            throwExceptionWithMessageDialog("First line must start with '//'")
        }

        return this
    }

    private fun List<String>.replaceKeywordsToQuery(): String {
        val commands =
            this.first()
                .replace("//", "")
                .trim()
                .split(" ")

        val codeSnippets = this.drop(1).joinToString(separator = "\n")

        val replacedCommand: String =
            commands.joinToString(separator = " and ") { replaceKeywordsToQuery(it) }

        return "$replacedCommand\n$codeSnippets"
    }

    private fun replaceKeywordsToQuery(command: String): String {
        return when {
            keywordToQueryMap.containsKey(command) -> requireNotNull(keywordToQueryMap[command])
            command.contains("@language") -> {
                val languageCode = validateLanguageCommandAndGetLanguageCode(command)
                "Translate your response to $languageCode"
            }
            else -> command
        }
    }

    private fun validateLanguageCommandAndGetLanguageCode(command: String): String {
        val parts = command.split(":")

        require(parts.size == 2) {
            throwExceptionWithMessageDialog("You must use @language command like this: '@language:korean'")
        }

        val languageCode = parts.last()
        if (languageCode.any { it !in 'A'..'Z' && it !in 'a'..'z' }) {
            throwExceptionWithMessageDialog("Language code must be letters")
        }

        return parts.last()
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

    private fun throwExceptionWithMessageDialog(message: String) {
        ApplicationManager.getApplication().invokeLater {
            Messages.showErrorDialog(message, "Error")
        }

        throw IllegalArgumentException(message)
    }
}
