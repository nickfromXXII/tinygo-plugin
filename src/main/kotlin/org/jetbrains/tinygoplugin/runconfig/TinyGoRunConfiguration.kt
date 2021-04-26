package org.jetbrains.tinygoplugin.runconfig

import com.goide.GoFileType
import com.goide.execution.GoModuleBasedConfiguration
import com.goide.execution.GoRunConfigurationBase
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.options.SettingsEditorGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMExternalizerUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jdom.Element
import org.jetbrains.tinygoplugin.TinyGoBundle
import org.jetbrains.tinygoplugin.configuration.ConfigurationWithHistory
import org.jetbrains.tinygoplugin.configuration.TinyGoConfiguration
import org.jetbrains.tinygoplugin.configuration.absoluteOrRelativePath
import org.jetbrains.tinygoplugin.configuration.toRelativePath
import org.jetbrains.tinygoplugin.ui.ConfigurationProvider
import java.io.File

private const val ERROR_SDK_NOT_SET = "run.configuration.errors.sdk"
private const val ERROR_MAIN_FILE_NOT_FOUND = "run.configuration.errors.main"
private const val ERROR_NOT_GO_FILE = "run.configuration.errors.type"
private const val CONFIGURATION_EDITOR_NAME = "run.configuration.editor.name"

class TinyGoRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String,
    runType: TinyGoCommandType,
) :
    GoRunConfigurationBase<TinyGoRunningState>(name, GoModuleBasedConfiguration(project), factory),
    ConfigurationProvider<RunSettings> {
    companion object {
        const val MAIN_FILE = "tinygo_main_file"
        const val CMD_OPTIONS = "tinygo_cmd_options"
    }

    val command = runType.command
    var runConfig: RunSettings
    val cmdlineOptions: List<String>
        get() = runConfig.cmdlineOptions

    override val tinyGoSettings: RunSettings
        get() = runConfig

    val executable: VirtualFile?
        get() = TinyGoConfiguration.getInstance(project).sdk.executable

    init {
        val tinyGoSettings = ConfigurationWithHistory(project)
        val projectFile = project.workspaceFile
        val workspaceFolder = projectFile?.parent?.parent
        val mainPath = workspaceFolder?.canonicalPath ?: ""
        runConfig =
            RunSettings(tinyGoSettings, "", mainPath)
    }

    private fun mainFile(): VirtualFile? {
        if (runConfig.mainFile.isEmpty()) {
            return null
        }
        val file = File(runConfig.mainFile)
        if (!file.exists()) {
            return null
        }
        return VfsUtil.findFile(file.toPath(), false)
    }

    @Throws(RuntimeConfigurationException::class)
    override fun checkConfiguration() {
        if (executable == null) {
            throw RuntimeConfigurationException(TinyGoBundle.message(ERROR_SDK_NOT_SET))
        }
        val main = mainFile() ?: throw RuntimeConfigurationException(TinyGoBundle.message(ERROR_MAIN_FILE_NOT_FOUND))
        if (!main.isValid) {
            throw RuntimeConfigurationException(TinyGoBundle.message(ERROR_MAIN_FILE_NOT_FOUND))
        }
        val mainFiletype = main.fileType
        if (mainFiletype !is GoFileType && !main.isDirectory) {
            throw RuntimeConfigurationException(TinyGoBundle.message(ERROR_NOT_GO_FILE))
        }
    }

    override fun createSettingsEditorGroup(): SettingsEditorGroup<TinyGoRunConfiguration> {
        val result = SettingsEditorGroup<TinyGoRunConfiguration>()
        val editor: SettingsEditor<TinyGoRunConfiguration> = TinyGoRunConfigurationEditor(this)
        result.addEditor(TinyGoBundle.message(CONFIGURATION_EDITOR_NAME, command), editor)
        return result
    }

    override fun newRunningState(environment: ExecutionEnvironment, module: Module): TinyGoRunningState {
        return TinyGoRunningState(environment, module, this)
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        JDOMExternalizerUtil.writeCustomField(element, MAIN_FILE, toRelativePath(runConfig.mainFile, project))
        JDOMExternalizerUtil.writeCustomField(element, CMD_OPTIONS, runConfig.userArguments)
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        val filePath = JDOMExternalizerUtil.readCustomField(element, MAIN_FILE) ?: ""
        runConfig.mainFile =
            absoluteOrRelativePath(filePath, project)?.path ?: project.basePath ?: ""
        val arguments = JDOMExternalizerUtil.readCustomField(element, CMD_OPTIONS)
        runConfig.userArguments = arguments ?: ""
    }
}
