package com.kztutorial99.chessengine.engine

import android.content.Context
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class StockfishEngine(private val context: Context) : AutoCloseable {
    private var process: Process? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null

    @Synchronized
    fun start(): Boolean {
        if (process?.isAlive == true) return true
        return try {
            val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull { it == "arm64-v8a" || it == "armeabi-v7a" }
                ?: return false
            val executable = File(context.filesDir, "stockfish-$abi")
            if (!executable.exists()) {
                context.assets.open("stockfish/$abi/stockfish").use { input ->
                    executable.outputStream().use { output -> input.copyTo(output) }
                }
            }
            executable.setExecutable(true, true)
            process = ProcessBuilder(executable.absolutePath)
                .redirectErrorStream(true)
                .start()
            reader = BufferedReader(InputStreamReader(process!!.inputStream))
            writer = BufferedWriter(OutputStreamWriter(process!!.outputStream))
            command("uci")
            if (!waitFor("uciok", 5000)) return false
            command("isready")
            waitFor("readyok", 5000)
        } catch (_: Exception) {
            stop()
            false
        }
    }

    @Synchronized
    fun analyze(fen: String, depth: Int = 18): AnalysisResult? {
        if (!start()) return null
        command("position fen $fen")
        command("go depth ${depth.coerceIn(1, 30)}")
        var bestMove: String? = null
        var cp: Int? = null
        var mate: Int? = null
        while (true) {
            val line = reader?.readLine() ?: break
            if (line.startsWith("info ")) {
                Regex("score cp (-?\\d+)").find(line)?.let { cp = it.groupValues[1].toInt() }
                Regex("score mate (-?\\d+)").find(line)?.let { mate = it.groupValues[1].toInt() }
            }
            if (line.startsWith("bestmove ")) {
                bestMove = line.substringAfter("bestmove ").substringBefore(' ')
                break
            }
        }
        return bestMove?.takeIf { it != "(none)" }?.let { AnalysisResult(it, cp, mate) }
    }

    private fun command(value: String) {
        writer?.write(value)
        writer?.newLine()
        writer?.flush()
    }

    private fun waitFor(token: String, timeoutMs: Long): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (reader?.readLine()?.contains(token) == true) return true
        }
        return false
    }

    fun stop() {
        try { command("quit") } catch (_: Exception) {}
        try { process?.destroy() } catch (_: Exception) {}
        process = null
        reader = null
        writer = null
    }

    override fun close() = stop()

    data class AnalysisResult(val bestMove: String, val centipawns: Int?, val mate: Int?)
}
