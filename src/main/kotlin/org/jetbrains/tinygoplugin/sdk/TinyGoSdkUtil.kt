package org.jetbrains.tinygoplugin.sdk

import com.goide.GoNotifications
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.tinygoplugin.TinyGoBundle
import org.jetbrains.tinygoplugin.services.TinyGoSettingsService
import java.io.File

const val CONFIGURATION_INCOMPLETE_NOTIFICATION = "notifications.tinygoSDK.configuration.incomplete"

fun notifyTinyGoNotConfigured(
    project: Project?,
    content: String,
) {
    val notification = GoNotifications.getGeneralGroup()
        .createNotification(
            TinyGoBundle.message(CONFIGURATION_INCOMPLETE_NOTIFICATION),
            content,
            NotificationType.INFORMATION
        )
    notification.addAction(object : NotificationAction("TinyGo settings") {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
            ShowSettingsUtil.getInstance().editConfigurable(project, TinyGoSettingsService(project!!))
        }
    })
    notification.notify(project)
}

fun getTinyGoExecutable(sdkRoot: VirtualFile?): VirtualFile? {
    if (sdkRoot != null && sdkRoot.isValid && sdkRoot.isDirectory) {
        val sdkBinDir = sdkRoot.findChild("bin")
        if (sdkBinDir != null && sdkBinDir.isValid && sdkBinDir.isDirectory) {
            val sdkTinyGoExecutable = sdkBinDir.findChild(osManager.executableName())
            if (sdkTinyGoExecutable != null && sdkTinyGoExecutable.isValid) {
                return sdkTinyGoExecutable
            }
        }
    }
    return null
}

fun suggestSdkDirectoryStr(): String = suggestSdkDirectory()?.canonicalPath ?: ""

fun suggestSdkDirectories(): Collection<File> {
    return osManager.suggestedDirectories().asSequence().map { File(it) }.filter(File::exists)
        .filter(::checkBin).filter(::checkTargets).filter(::checkMachinesSources).toList()
}

fun findTinyGoInPath(): File? {
    val tinygoExec =
        PathEnvironmentVariableUtil.findExecutableInPathOnAnyOS(osManager.executableBaseName()) ?: return null
    // resolve links and go 2 directories up: $tinygoRoot/bin/tinygo -> $tinygoRoot/bin -> $tinygoRoot
    return tinygoExec.canonicalFile.parentFile?.parentFile
}

fun suggestSdkDirectory(): File? {
    val tinygoPath = findTinyGoInPath()
    if (tinygoPath != null) {
        return tinygoPath
    }
    return suggestSdkDirectories().firstOrNull()
}
