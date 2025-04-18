import kotlinx.cinterop.*
import platform.posix.memset
import platform.posix.system
import platform.windows.*
import kotlin.experimental.ExperimentalNativeApi

// --- 配置 ---
const val JRE_RELATIVE_PATH = "jre"
const val LIB_RELATIVE_PATH = "lib"
const val MAIN_CLASS_NAME = "com.telecwin.subgrade_radar_analysis.Main"

@OptIn(ExperimentalNativeApi::class)
fun main() {
    val os = Platform.osFamily
    val pathSeparator = if (os == OsFamily.WINDOWS) "\\" else "/"
    val javaExecutableName = if (os == OsFamily.WINDOWS) "java.exe" else "java"

    // 1. 构造路径部分，*不*包含开头的引号
    val javaExecutablePathPart = ".${pathSeparator}${JRE_RELATIVE_PATH}${pathSeparator}bin${pathSeparator}${javaExecutableName}"
    val classpathPart = "${LIB_RELATIVE_PATH}${pathSeparator}*" // lib\* 或 lib/*

    // 2. 组装最终命令字符串
    //    - 为可执行文件路径加上引号 (即使没有空格，也是良好实践)
    //    - 为 classpath 参数加上引号 (因为它包含 '*')
    val command = "${javaExecutablePathPart} -cp \"${classpathPart}\" ${MAIN_CLASS_NAME}"

    println("当前操作系统: $os")
    // 打印最终命令，检查是否符合预期格式: "path\to\java.exe" -cp "lib\*" main.class
    println("将要执行的命令: $command")

    // 执行命令
    println("正在尝试启动 Java 应用程序...")
    val result = executeCommand(command,os)

    // 检查执行结果 (错误处理部分保持不变)
    if (result) {
        println("命令似乎已成功启动。Java 应用程序应该正在运行。")
        println("(注意：这不保证 Java 程序内部没有错误或能成功初始化)")
    } else {
        println("命令执行失败，返回码: $result")
        println("请检查：")
        println("1. JRE 路径是否正确且包含 Java 可执行文件 (预期在 .${pathSeparator}${JRE_RELATIVE_PATH}${pathSeparator}bin\\)")
        println("2. lib 目录是否存在且包含所需的 JAR 文件 (预期在 .${pathSeparator}${LIB_RELATIVE_PATH}\\)")
        println("3. 主类名称 '$MAIN_CLASS_NAME' 是否正确")
        println("4. JRE 是否与当前操作系统 (${os}) 和架构兼容")
        println("5. 文件权限问题")
        println("6. 检查 Java 进程是否有任何错误输出 (可能需要重定向输出才能看到)")
        println("7. 确认命令解析是否正确 (查看上面打印的 '将要执行的命令')") // 添加提示
    }
}

// 辅助函数，根据操作系统调用不同的执行方式
@OptIn(ExperimentalNativeApi::class)
fun executeCommand(command: String, osFamily: OsFamily): Boolean {
    return if (osFamily == OsFamily.WINDOWS) {
        executeWindowsCommandNoWindow(command)
    } else {
        // 对于 Linux/macOS，system() 通常不会创建不必要的窗口
        val result = system(command)
        result == 0 // system 返回 0 表示成功
    }
}
// Windows 平台使用 CreateProcessW 隐藏窗口
// Windows 平台使用 CreateProcessW 隐藏窗口
@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class) // <--- 可能需要两个 OptIn
fun executeWindowsCommandNoWindow(command: String): Boolean {
    // 使用 memScoped 来管理 C 结构体和字符串的内存
    memScoped {
        // CreateProcessW 需要一个可变的宽字符命令字符串指针
        val commandLine = command.wcstr.ptr

        // 初始化 STARTUPINFOW 结构体
        val si = alloc<STARTUPINFOW>()
        // ZeroMemory(si.ptr, sizeOf<STARTUPINFOW>().convert()) // <--- 注释掉或删除原来的行
        memset(si.ptr, 0, sizeOf<STARTUPINFOW>().convert()) // <--- 2. 使用 memset 清零结构体
        si.cb = sizeOf<STARTUPINFOW>().convert() // 设置结构体大小

        // 初始化 PROCESS_INFORMATION 结构体
        val pi = alloc<PROCESS_INFORMATION>()
        // 清零 PROCESS_INFORMATION 也是个好习惯，虽然在这个特定场景下可能不是严格必需的
        memset(pi.ptr, 0, sizeOf<PROCESS_INFORMATION>().convert())

        // 调用 CreateProcessW
        val success = CreateProcessW(
            lpApplicationName = null,
            lpCommandLine = commandLine,
            lpProcessAttributes = null,
            lpThreadAttributes = null,
            bInheritHandles = FALSE,
            dwCreationFlags = CREATE_NO_WINDOW.convert<UInt>() or NORMAL_PRIORITY_CLASS.convert<UInt>(),
            lpEnvironment = null,
            lpCurrentDirectory = null,
            lpStartupInfo = si.ptr,
            lpProcessInformation = pi.ptr
        )

        // 检查 CreateProcessW 是否成功
        if (success != 0) {
            // 成功创建进程后，立即关闭句柄
            CloseHandle(pi.hProcess)
            CloseHandle(pi.hThread)
            return true
        } else {
            val errorCode = GetLastError()
            println("CreateProcessW 失败，错误码: $errorCode")
            // 可以考虑添加更详细的错误信息转换，但这超出了 ZeroMemory 的问题范围
            // 例如: https://stackoverflow.com/questions/1387064/how-to-get-the-error-message-from-the-error-code-returned-by-getlasterror
            return false
        }
    } // memScoped 结束，自动释放内存
}
