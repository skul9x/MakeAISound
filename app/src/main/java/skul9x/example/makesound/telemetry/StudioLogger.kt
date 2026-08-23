package skul9x.example.makesound.telemetry

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Diagnostic log level for MakeAiSound telemetry.
 */
enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}

/**
 * Structured diagnostic log entry.
 */
data class StudioLogEntry(
    val id: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val tag: String,
    val message: String,
    val stage: String? = null
) {
    fun format(): String {
        val timeStr = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
        val stageStr = if (stage != null) " [$stage]" else ""
        return "[$timeStr] [${level.name}] [$tag]$stageStr $message"
    }
}

/**
 * Thread-safe circular log buffer emitting a reactive `StateFlow<List<StudioLogEntry>>`.
 * Supports structured log capture and formatted export.
 */
object StudioLogger {
    private const val MAX_LOG_ENTRIES = 500
    private val lock = ReentrantLock()
    private var sequenceId = 0L
    private val buffer = ArrayDeque<StudioLogEntry>(MAX_LOG_ENTRIES)

    private val _logs = MutableStateFlow<List<StudioLogEntry>>(emptyList())
    val logs: StateFlow<List<StudioLogEntry>> = _logs.asStateFlow()

    fun log(level: LogLevel, tag: String, message: String, stage: String? = null) {
        lock.withLock {
            val entry = StudioLogEntry(
                id = ++sequenceId,
                level = level,
                tag = tag,
                message = message,
                stage = stage
            )
            if (buffer.size >= MAX_LOG_ENTRIES) {
                buffer.removeFirst()
            }
            buffer.addLast(entry)
            _logs.value = buffer.toList()
        }
    }

    fun d(tag: String, message: String, stage: String? = null) = log(LogLevel.DEBUG, tag, message, stage)
    fun i(tag: String, message: String, stage: String? = null) = log(LogLevel.INFO, tag, message, stage)
    fun w(tag: String, message: String, stage: String? = null) = log(LogLevel.WARN, tag, message, stage)
    fun e(tag: String, message: String, throwable: Throwable? = null, stage: String? = null) {
        val fullMsg = if (throwable != null) "$message\n${throwable.stackTraceToString()}" else message
        log(LogLevel.ERROR, tag, fullMsg, stage)
    }

    fun clear() {
        lock.withLock {
            buffer.clear()
            _logs.value = emptyList()
        }
    }

    fun exportLogsToString(): String {
        lock.withLock {
            return buffer.joinToString("\n") { it.format() }
        }
    }

    fun getRecentLogs(): List<StudioLogEntry> {
        lock.withLock {
            return buffer.toList()
        }
    }
}
