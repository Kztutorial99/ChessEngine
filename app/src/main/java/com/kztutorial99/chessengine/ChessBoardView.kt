package com.kztutorial99.chessengine

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

class ChessBoardView(context: Context) : View(context) {

    interface Listener {
        /** Fired after every legal move made by the user. */
        fun onPositionChanged(position: Position)
    }

    var listener: Listener? = null

    var position = Position().apply { setStart() }
        private set

    /** true = black at the bottom (you are playing black). */
    var flipped = false
        set(value) { field = value; invalidate() }

    private val undoStack = ArrayList<Pair<Position, Move?>>()

    /** board index -> on-screen index (and back: the mapping is its own inverse). */
    private fun dsp(i: Int) = if (flipped) 63 - i else i

    val canUndo: Boolean get() = undoStack.isNotEmpty()

    /** Takes back one ply. Returns false when there is nothing to take back. */
    fun undo(): Boolean {
        val prev = undoStack.removeLastOrNull() ?: return false
        position = prev.first
        lastMove = prev.second
        selected = -1
        legalForSelected = emptyList()
        dragging = false
        hint = null
        hintPv = emptyList()
        pendingPromotion = null
        invalidate()
        listener?.onPositionChanged(position)
        return true
    }

    /** Plays a move programmatically (used by the engine when it is its turn). */
    fun playMove(m: Move) = applyMove(m)

    private var selected = -1
    private var legalForSelected: List<Move> = emptyList()
    private var lastMove: Move? = null
    private var hint: Move? = null
    private var hintPv: List<Move> = emptyList()
    private var dragX = 0f
    private var dragY = 0f
    private var dragging = false
    private var pendingPromotion: Move? = null

    private val glyph = mapOf(
        'K' to "\u2654", 'Q' to "\u2655", 'R' to "\u2656", 'B' to "\u2657", 'N' to "\u2658", 'P' to "\u2659",
        'k' to "\u265A", 'q' to "\u265B", 'r' to "\u265C", 'b' to "\u265D", 'n' to "\u265E", 'p' to "\u265F",
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Always square, and never bigger than the space we were given: this is what
        // stops the board from being cut off on tall/narrow phone screens.
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val size = when {
            h <= 0 -> w
            else -> min(w, h)
        }
        setMeasuredDimension(size, size)
    }

    fun reset() {
        position = Position().apply { setStart() }
        selected = -1
        legalForSelected = emptyList()
        lastMove = null
        hint = null
        hintPv = emptyList()
        pendingPromotion = null
        undoStack.clear()
        invalidate()
        listener?.onPositionChanged(position)
    }

    fun setHint(best: Move?, pv: List<Move>) {
        hint = best
        hintPv = pv
        invalidate()
    }

    fun clearHint() = setHint(null, emptyList())

    private fun boardMetrics(): Triple<Float, Float, Float> {
        val size = min(width, height).toFloat()
        return Triple((width - size) / 2f, (height - size) / 2f, size / 8f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val (left, top, sq) = boardMetrics()

        for (r in 0..7) for (c in 0..7) {
            paint.color = if ((r + c) % 2 == 0) Color.rgb(237, 214, 176) else Color.rgb(170, 126, 92)
            canvas.drawRect(left + c * sq, top + r * sq, left + (c + 1) * sq, top + (r + 1) * sq, paint)
        }

        lastMove?.let {
            paint.color = Color.argb(70, 255, 235, 90)
            for (raw in intArrayOf(it.from, it.to)) {
                val idx = dsp(raw)
                canvas.drawRect(left + (idx % 8) * sq, top + (idx / 8) * sq, left + (idx % 8 + 1) * sq, top + (idx / 8 + 1) * sq, paint)
            }
        }

        // The king that is IN CHECK gets a loud red square so you can see it instantly.
        if (Rules.inCheck(position, position.whiteToMove)) {
            val k = Rules.kingSquare(position, position.whiteToMove).let { if (it >= 0) dsp(it) else it }
            if (k >= 0) {
                val x0 = left + (k % 8) * sq
                val y0 = top + (k / 8) * sq
                paint.style = Paint.Style.FILL
                paint.color = Color.rgb(215, 38, 38)
                canvas.drawRect(x0, y0, x0 + sq, y0 + sq, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = sq * .08f
                paint.color = Color.rgb(255, 120, 120)
                canvas.drawRect(x0 + sq * .04f, y0 + sq * .04f, x0 + sq * .96f, y0 + sq * .96f, paint)
                paint.style = Paint.Style.FILL
            }
        }

        if (selected >= 0) {
            val d = dsp(selected)
            paint.color = Color.argb(110, 255, 220, 60)
            canvas.drawRect(left + (d % 8) * sq, top + (d / 8) * sq, left + (d % 8 + 1) * sq, top + (d / 8 + 1) * sq, paint)
        }

        for (m in legalForSelected.distinctBy { it.to }) {
            val dTo = dsp(m.to)
            val cx = left + (dTo % 8 + .5f) * sq
            val cy = top + (dTo / 8 + .5f) * sq
            if (position.sq[m.to] != '.' || m.flag == 1) {
                paint.color = Color.argb(120, 20, 90, 20)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = sq * .07f
                canvas.drawCircle(cx, cy, sq * .42f, paint)
                paint.style = Paint.Style.FILL
            } else {
                paint.color = Color.argb(140, 25, 95, 25)
                canvas.drawCircle(cx, cy, sq * .14f, paint)
            }
        }

        // pieces
        for (i in 0..63) {
            if (position.sq[i] == '.') continue
            if (dragging && i == selected) continue
            drawPiece(canvas, position.sq[i], left + (dsp(i) % 8 + .5f) * sq, top + (dsp(i) / 8 + .5f) * sq, sq)
        }
        if (dragging && selected >= 0) drawPiece(canvas, position.sq[selected], dragX, dragY, sq)

        // Exactly ONE engine arrow: the single best move. No second/faded arrow.
        hint?.let { drawArrow(canvas, left, top, sq, dsp(it.from), dsp(it.to), Color.argb(235, 40, 190, 90), sq * .12f) }

        pendingPromotion?.let { drawPromotionPicker(canvas, left, top, sq, it) }
    }

    private fun drawPiece(canvas: Canvas, ch: Char, x: Float, y: Float, sq: Float) {
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = sq * .74f
        paint.typeface = Typeface.DEFAULT
        paint.color = if (ch.isUpperCase()) Color.WHITE else Color.rgb(22, 22, 22)
        paint.setShadowLayer(3f, 1f, 2f, Color.argb(160, 0, 0, 0))
        val metrics = paint.fontMetrics
        val baseline = y - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(glyph[ch] ?: "?", x, baseline, paint)
        paint.clearShadowLayer()
    }

    private fun drawArrow(canvas: Canvas, l: Float, t: Float, s: Float, a: Int, b: Int, color: Int, stroke: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = color
        p.strokeWidth = stroke
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        val x1 = l + (a % 8 + .5f) * s
        val y1 = t + (a / 8 + .5f) * s
        val x2 = l + (b % 8 + .5f) * s
        val y2 = t + (b / 8 + .5f) * s
        canvas.drawLine(x1, y1, x2, y2, p)
        p.style = Paint.Style.FILL
        val ang = kotlin.math.atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
        val h = s * .2f
        val x3 = (x2 - h * kotlin.math.cos(ang - .5).toFloat())
        val y3 = (y2 - h * kotlin.math.sin(ang - .5).toFloat())
        val x4 = (x2 - h * kotlin.math.cos(ang + .5).toFloat())
        val y4 = (y2 - h * kotlin.math.sin(ang + .5).toFloat())
        canvas.drawPath(Path().apply { moveTo(x2, y2); lineTo(x3, y3); lineTo(x4, y4); close() }, p)
    }

    private fun promotionChoices(white: Boolean) =
        if (white) charArrayOf('Q', 'R', 'B', 'N') else charArrayOf('q', 'r', 'b', 'n')

    private fun drawPromotionPicker(canvas: Canvas, l: Float, t: Float, s: Float, m: Move) {
        val white = position.sq[m.from].isUpperCase()
        val col = dsp(m.to) % 8
        val topRow = if (dsp(m.to) / 8 == 0) 0 else 4
        paint.color = Color.argb(235, 30, 33, 40)
        canvas.drawRect(l + col * s, t + topRow * s, l + (col + 1) * s, t + (topRow + 4) * s, paint)
        promotionChoices(white).forEachIndexed { i, ch ->
            drawPiece(canvas, ch, l + (col + .5f) * s, t + (topRow + i + .5f) * s, s)
        }
    }

    private fun handlePromotionTap(screenIdx: Int): Boolean {
        val m = pendingPromotion ?: return false
        val white = position.sq[m.from].isUpperCase()
        val idx = screenIdx
        val col = dsp(m.to) % 8
        val topRow = if (dsp(m.to) / 8 == 0) 0 else 4
        if (idx % 8 != col || idx / 8 !in topRow..(topRow + 3)) {
            pendingPromotion = null
            invalidate()
            return true
        }
        val choice = promotionChoices(white)[idx / 8 - topRow]
        pendingPromotion = null
        applyMove(m.copy(promotion = choice))
        return true
    }

    private fun applyMove(m: Move) {
        undoStack.add(position.copy() to lastMove)
        position = Rules.make(position, m)
        lastMove = m
        selected = -1
        legalForSelected = emptyList()
        dragging = false
        hint = null
        hintPv = emptyList()
        invalidate()
        listener?.onPositionChanged(position)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val (l, t, s) = boardMetrics()
        val size = s * 8
        if (e.x < l || e.x > l + size || e.y < t || e.y > t + size) return true
        val c = ((e.x - l) / s).toInt().coerceIn(0, 7)
        val r = ((e.y - t) / s).toInt().coerceIn(0, 7)
        val screenIdx = r * 8 + c
        val idx = dsp(screenIdx)

        if (pendingPromotion != null) {
            if (e.action == MotionEvent.ACTION_UP) handlePromotionTap(screenIdx)
            return true
        }

        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                val piece = position.sq[idx]
                // Only the side whose turn it is may be picked up.
                if (piece != '.' && piece.isUpperCase() == position.whiteToMove) {
                    selected = idx
                    legalForSelected = Rules.legalMoves(position).filter { it.from == idx }
                    dragging = true
                    dragX = e.x
                    dragY = e.y
                } else if (selected >= 0) {
                    tryPlay(idx)
                } else {
                    selected = -1
                    legalForSelected = emptyList()
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> if (dragging) { dragX = e.x; dragY = e.y; invalidate() }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (selected >= 0 && idx != selected) tryPlay(idx) else { dragging = false; invalidate() }
            }
        }
        return true
    }

    private fun tryPlay(target: Int) {
        val candidates = legalForSelected.filter { it.to == target }
        if (candidates.isEmpty()) {
            dragging = false
            invalidate()
            return
        }
        if (candidates.size > 1 && candidates.all { it.promotion != null }) {
            pendingPromotion = candidates.first()
            dragging = false
            invalidate()
            return
        }
        applyMove(candidates.first())
    }
}
