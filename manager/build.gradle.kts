plugins {
    alias(libs.plugins.agp.app) apply false
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.kotlin.compose.compiler) apply false
}

project.ext.set("kpmmVersion", "v1.3.2")

project.ext.set("androidMinSdkVersion", 26)
project.ext.set("androidTargetSdkVersion", 37)
project.ext.set("androidCompileSdkVersion", 37)
project.ext.set("androidBuildToolsVersion", "37.0.0")
project.ext.set("androidCompileNdkVersion", "29.0.14206865")
project.ext.set("managerVersionCode", getVersionCode())
project.ext.set("managerVersionName", getVersionName())
fun Project.exec(command: String) = providers.exec {
    commandLine(command.split(" "))
}.standardOutput.asText.get().trim()

fun getGitCommitCount(): Int {
    return exec("git rev-list --count HEAD").trim().toInt()
}

fun getGitDescribe(): String {
    return exec("git rev-parse --verify --short HEAD").trim()
}

fun getVersionCode(): Int {
    val commitCount = getGitCommitCount()
    val major = 1
    return major * 1000 + commitCount
}

fun getBranch(): String {
    return exec("git rev-parse --abbrev-ref HEAD").trim()
}

fun getVersionName(): String {
    return getGitDescribe()
}

tasks.register("printVersion") {
    doLast {
        println("Version code: ${getVersionCode()}")
        println("Version name: ${getVersionName()}")
    }
}
