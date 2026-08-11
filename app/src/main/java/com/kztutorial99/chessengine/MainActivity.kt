package com.kztutorial99.chessengine

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity(), ChessBoardView.Listener {

    private lateinit var board: ChessBoardView
    private lateinit var status: TextView
    private lateinit var line: TextView
    private lateinit var analyzeBtn: Button
    private lateinit var sideBtn: Button
    private lateinit var undoBtn: Button
    private lateinit var extremeBtn: Button

    private val engine = ChessEngine()
    private val ui = Handler(Looper.getMainLooper())
    private var worker: Thread? = null
    private var liveAnalysis = false
    private var playerWhite = true
    private var extremeMode = false
    /** Fixed think budgets: no user-selected duration, the engine simply analyses properly. */
    private val normalMs = 6000L
    private val extremeMs = 15000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bg = Color.rgb(15, 18, 23)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            fitsSystemWindows = true
        }

        val title = TextView(this).apply {
            text = "\u265F  ChessEngine Analyzer"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }

        board = ChessBoardView(this).apply { listener = this@MainActivity }
        // The board sits centred inside a flexible frame so it always fits the screen.
        val boardHolder = FrameLayout(this).apply {
            setPadding(dp(8), dp(4), dp(8), dp(4))
            addView(
                board,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                ),
            )
        }

        status = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.rgb(225, 230, 238))
            setPadding(dp(16), dp(8), dp(16), dp(2))
        }
        line = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.rgb(150, 160, 175))
            setPadding(dp(16), 0, dp(16), dp(8))
            maxLines = 2
        }

        val controls = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(2), dp(10), dp(12))
        }
        val reset = styledButton("Reset", Color.rgb(52, 58, 70)) {
            stopAnalysis()
            liveAnalysis = false
            analyzeBtn.text = "Analyze"
            board.reset()
        }
        analyzeBtn = styledButton("Analyze", Color.rgb(38, 140, 86)) { toggleAnalysis() }

        controls.addView(reset, LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(dp(6), 0, dp(6), 0) })
        controls.addView(analyzeBtn, LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(dp(6), 0, dp(6), 0) })

        val controls2 = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), 0, dp(10), dp(4))
        }
        undoBtn = styledButton("\u21A9 Undo", Color.rgb(150, 90, 40)) { undoMove() }
        sideBtn = styledButton("Play: White", Color.rgb(60, 72, 96)) { switchSide() }
        extremeBtn = styledButton("\u26A1 Normal", Color.rgb(52, 58, 70)) { toggleExtreme() }
        controls2.addView(undoBtn, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(6), 0, dp(6), 0) })
        controls2.addView(sideBtn, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(6), 0, dp(6), 0) })
        controls2.addView(extremeBtn, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(6), 0, dp(6), 0) })

        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        root.addView(boardHolder, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(status, LinearLayout.LayoutParams(-1, -2))
        root.addView(line, LinearLayout.LayoutParams(-1, -2))
        root.addView(controls2, LinearLayout.LayoutParams(-1, -2))
        root.addView(controls, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)

        // Fastest-mate hunting is the default training mode.
        toggleExtreme()
        updateStatus()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun styledButton(label: String, color: Int, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 13f
            setPadding(dp(4), 0, dp(4), 0)
            maxLines = 1
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(color)
            }
            setOnClickListener { onClick() }
        }

    // --- side / undo / engine opponent -----------------------------------------

    /** Switch the colour you play: flips the board and hands the other side to the engine. */
    private fun switchSide() {
        stopAnalysis()
        playerWhite = !playerWhite
        board.flipped = !playerWhite
        sideBtn.text = if (playerWhite) "Play: White" else "Play: Black"
        updateStatus()
        if (liveAnalysis) startAnalysis()
    }

    /** Takes back your move (plus the engine's reply when the engine is playing). */
    private fun undoMove() {
        stopAnalysis()
        if (!board.undo()) return
        updateStatus()
        if (liveAnalysis) startAnalysis()
    }

    /**
     * EXTREME ANALYZE THINK.
     * Off = classical analysis (material + position).
     * On  = the engine only cares about one thing: hunting the enemy king. It
     *       proves deep forced mates, prefers checks, sacrifices and mating nets
     *       over safe material, and reports the fastest mate it can force.
     */
    private fun toggleExtreme() {
        extremeMode = !extremeMode
        engine.extreme = extremeMode
        extremeBtn.text = if (extremeMode) "\u26A1 EXTREME" else "\u26A1 Normal"
        extremeBtn.background = (extremeBtn.background as GradientDrawable).apply {
            setColor(if (extremeMode) Color.rgb(190, 40, 45) else Color.rgb(52, 58, 70))
        }
        if (liveAnalysis) startAnalysis() else updateStatus()
    }

    // --- analysis -------------------------------------------------------------

    private fun toggleAnalysis() {
        if (liveAnalysis) {
            liveAnalysis = false
            stopAnalysis()
            analyzeBtn.text = "Analyze"
            board.clearHint()
            updateStatus()
        } else {
            liveAnalysis = true
            analyzeBtn.text = "Stop Analyze"
            startAnalysis()
        }
    }

    private fun stopAnalysis() {
        engine.stop()
        worker?.let { if (it.isAlive) it.join(400) }
        worker = null
    }

    /** Analyses ONLY your own moves - the opponent's turn never gets an arrow. */
    private fun startAnalysis() {
        stopAnalysis()
        val snapshot = board.position.copy()
        if (Rules.legalMoves(snapshot).isEmpty()) { updateStatus(); return }
        if (snapshot.whiteToMove != playerWhite) {
            board.clearHint()
            status.text = "Opponent to move \u2022 no analysis"
            line.text = ""
            return
        }
        val mover = if (playerWhite) "White" else "Black"
        status.text = if (extremeMode) "\u26A1 EXTREME deep analysis\u2026" else "Analyzing for $mover\u2026"
        val depth = if (extremeMode) 8 else 6
        val budget = if (extremeMode) extremeMs else normalMs
        worker = Thread {
            engine.analyze(snapshot, maxDepth = depth, timeLimitMs = budget) { info ->
                ui.post {
                    if (!liveAnalysis) return@post
                    if (board.position.whiteToMove != playerWhite) { board.clearHint(); return@post }
                    board.setHint(info.best, info.pv)
                    showInfo(snapshot, info, mover)
                }
            }
        }.also { it.isDaemon = true; it.start() }
    }

    private fun showInfo(snapshot: Position, info: ChessEngine.Info, mover: String) {
        val best = info.best ?: return
        val bestSan = Rules.sanLike(snapshot, best)
        val head = info.mateIn?.let {
            if (it > 0) "\u2694 MATE IN $it" else "\u26A0 MATED IN ${-it}"
        } ?: "eval %+.2f".format(info.score / 100.0)
        val tag = if (extremeMode) "\u26A1 " else ""
        status.text = "$tag$head \u2022 play $bestSan \u2022 d${info.depth}"
        val pvText = buildString {
            var p = snapshot.copy()
            for (m in info.pv.take(8)) {
                append(Rules.sanLike(p, m)).append(' ')
                p = Rules.make(p, m)
            }
        }.trim()
        line.text = if (pvText.isEmpty()) "" else "Best line: $pvText"
    }

    // --- board callbacks -----------------------------------------------------

    override fun onPositionChanged(position: Position) {
        updateStatus()
        undoBtn.isEnabled = board.canUndo
        if (liveAnalysis) startAnalysis() else line.text = ""
    }

    private fun updateStatus() {
        val p = board.position
        val mover = if (p.whiteToMove) "White" else "Black"
        status.text = when {
            Rules.isCheckmate(p) -> "Checkmate \u2014 ${if (p.whiteToMove) "Black" else "White"} wins"
            Rules.isStalemate(p) -> "Draw by stalemate"
            Rules.inCheck(p, p.whiteToMove) -> "$mover to move \u2022 check!"
            else -> "$mover to move \u2022 tap or drag a piece"
        }
        if (Rules.isCheckmate(p) || Rules.isStalemate(p)) {
            liveAnalysis = false
            analyzeBtn.text = "Analyze"
            stopAnalysis()
            board.clearHint()
        }
    }

    override fun onDestroy() {
        liveAnalysis = false
        stopAnalysis()
        super.onDestroy()
    }
}
