package com.github.wool0826.localcodeassistant.api

import com.intellij.openapi.vfs.VirtualFile

interface ApiClient {
    fun isModelExist(modelName: String): Boolean
    fun downloadModel(modelName: String)

    fun autoComplete(targetFile: VirtualFile?, prompt: String): String
}
