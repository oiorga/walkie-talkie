package walkie.talkie.git
import org.gradle.api.Project

fun Project.gitCommit(): String =
    providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
    }.standardOutput.asText.get().trim()

fun Project.gitBranch(): String =
    providers.exec {
        commandLine("git", "rev-parse", "--abbrev-ref", "HEAD")
    }.standardOutput.asText.get().trim()

fun Project.gitRemote(): String =
    providers.exec {
        commandLine("git", "config", "--get", "remote.origin.url")
    }.standardOutput.asText.get().trim()