package com.kztutorial99.chessengine

import kotlin.math.abs

/** flag: 0 normal, 1 en-passant capture, 2 castle, 3 double pawn push */
data class Move(
    val from: Int,
    val to: Int,
    val promotion: Char? = null,
    val flag: Int = 0,
    val score: Int = 0,
)

class Position {
    val sq = CharArray(64) { '.' }
    var whiteToMove = true
    var castleWK = true
    var castleWQ = true
    var castleBK = true
    var castleBQ = true
    var epTarget = -1
    var halfmove = 0
    var fullmove = 1

    fun copy(): Position {
        val p = Position()
        sq.copyInto(p.sq)
        p.whiteToMove = whiteToMove
        p.castleWK = castleWK; p.castleWQ = castleWQ
        p.castleBK = castleBK; p.castleBQ = castleBQ
        p.epTarget = epTarget
        p.halfmove = halfmove
        p.fullmove = fullmove
        return p
    }

    fun setStart() {
        for (i in 0..63) sq[i] = '.'
        val back = "rnbqkbnr"
        for (c in 0..7) {
            sq[c] = back[c]
            sq[8 + c] = 'p'
            sq[48 + c] = 'P'
            sq[56 + c] = back[c].uppercaseChar()
        }
        whiteToMove = true
        castleWK = true; castleWQ = true; castleBK = true; castleBQ = true
        epTarget = -1; halfmove = 0; fullmove = 1
    }

    fun toFen(): String {
        val sb = StringBuilder()
        for (r in 0..7) {
            var empty = 0
            for (c in 0..7) {
                val p = sq[r * 8 + c]
                if (p == '.') empty++ else {
                    if (empty > 0) { sb.append(empty); empty = 0 }
                    sb.append(p)
                }
            }
            if (empty > 0) sb.append(empty)
            if (r < 7) sb.append('/')
        }
        sb.append(if (whiteToMove) " w " else " b ")
        val cast = StringBuilder()
        if (castleWK) cast.append('K'); if (castleWQ) cast.append('Q')
        if (castleBK) cast.append('k'); if (castleBQ) cast.append('q')
        sb.append(if (cast.isEmpty()) "-" else cast.toString())
        sb.append(' ').append(if (epTarget in 0..63) squareName(epTarget) else "-")
        sb.append(' ').append(halfmove).append(' ').append(fullmove)
        return sb.toString()
    }

    companion object {
        fun squareName(i: Int): String = "${('a'.code + i % 8).toChar()}${8 - i / 8}"
    }
}

object Rules {
    private fun isWhite(p: Char) = p.isUpperCase()
    private fun onBoard(r: Int, c: Int) = r in 0..7 && c in 0..7

    fun kingSquare(p: Position, white: Boolean): Int {
        val k = if (white) 'K' else 'k'
        for (i in 0..63) if (p.sq[i] == k) return i
        return -1
    }

    /** Is square [idx] attacked by [byWhite]? */
    fun isAttacked(p: Position, idx: Int, byWhite: Boolean): Boolean {
        if (idx < 0) return false
        val r = idx / 8
        val c = idx % 8

        // pawns
        val pd = if (byWhite) 1 else -1 // attacker sits one row "behind" in screen coords
        for (dc in intArrayOf(-1, 1)) {
            val ar = r + pd
            val ac = c + dc
            if (onBoard(ar, ac)) {
                val q = p.sq[ar * 8 + ac]
                if (q == (if (byWhite) 'P' else 'p')) return true
            }
        }
        // knights
        val kn = arrayOf(2 to 1, 2 to -1, -2 to 1, -2 to -1, 1 to 2, 1 to -2, -1 to 2, -1 to -2)
        for ((dr, dc) in kn) {
            val ar = r + dr; val ac = c + dc
            if (onBoard(ar, ac)) {
                val q = p.sq[ar * 8 + ac]
                if (q != '.' && q.uppercaseChar() == 'N' && isWhite(q) == byWhite) return true
            }
        }
        // king
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val ar = r + dr; val ac = c + dc
            if (onBoard(ar, ac)) {
                val q = p.sq[ar * 8 + ac]
                if (q != '.' && q.uppercaseChar() == 'K' && isWhite(q) == byWhite) return true
            }
        }
        // sliders
        val diag = arrayOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
        val orth = arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        for ((dirs, pieces) in listOf(diag to "BQ", orth to "RQ")) {
            for ((dr, dc) in dirs) {
                var ar = r + dr; var ac = c + dc
                while (onBoard(ar, ac)) {
                    val q = p.sq[ar * 8 + ac]
                    if (q != '.') {
                        if (isWhite(q) == byWhite && pieces.contains(q.uppercaseChar())) return true
                        break
                    }
                    ar += dr; ac += dc
                }
            }
        }
        return false
    }

    fun inCheck(p: Position, white: Boolean): Boolean =
        isAttacked(p, kingSquare(p, white), !white)

    private fun addPawn(p: Position, from: Int, to: Int, white: Boolean, flag: Int, out: MutableList<Move>) {
        val lastRow = if (white) 0 else 7
        if (to / 8 == lastRow) {
            for (pr in charArrayOf('Q', 'R', 'B', 'N')) {
                out.add(Move(from, to, if (white) pr else pr.lowercaseChar(), flag))
            }
        } else out.add(Move(from, to, null, flag))
    }

    fun pseudoMoves(p: Position, forWhite: Boolean = p.whiteToMove): List<Move> {
        val out = ArrayList<Move>(64)
        for (i in 0..63) {
            val piece = p.sq[i]
            if (piece == '.' || isWhite(piece) != forWhite) continue
            val r = i / 8
            val c = i % 8
            when (piece.uppercaseChar()) {
                'P' -> {
                    val d = if (forWhite) -1 else 1
                    val one = (r + d) * 8 + c
                    if (onBoard(r + d, c) && p.sq[one] == '.') {
                        addPawn(p, i, one, forWhite, 0, out)
                        val startRow = if (forWhite) 6 else 1
                        if (r == startRow) {
                            val two = (r + 2 * d) * 8 + c
                            if (p.sq[two] == '.') out.add(Move(i, two, null, 3))
                        }
                    }
                    for (dc in intArrayOf(-1, 1)) {
                        val nr = r + d; val nc = c + dc
                        if (!onBoard(nr, nc)) continue
                        val t = nr * 8 + nc
                        val q = p.sq[t]
                        if (q != '.' && isWhite(q) != forWhite) addPawn(p, i, t, forWhite, 0, out)
                        else if (q == '.' && t == p.epTarget) out.add(Move(i, t, null, 1))
                    }
                }
                'N' -> {
                    for ((dr, dc) in arrayOf(2 to 1, 2 to -1, -2 to 1, -2 to -1, 1 to 2, 1 to -2, -1 to 2, -1 to -2)) {
                        val nr = r + dr; val nc = c + dc
                        if (!onBoard(nr, nc)) continue
                        val t = nr * 8 + nc
                        val q = p.sq[t]
                        if (q == '.' || isWhite(q) != forWhite) out.add(Move(i, t))
                    }
                }
                'K' -> {
                    for (dr in -1..1) for (dc in -1..1) {
                        if (dr == 0 && dc == 0) continue
                        val nr = r + dr; val nc = c + dc
                        if (!onBoard(nr, nc)) continue
                        val t = nr * 8 + nc
                        val q = p.sq[t]
                        if (q == '.' || isWhite(q) != forWhite) out.add(Move(i, t))
                    }
                    // castling
                    if (forWhite && i == 60) {
                        if (p.castleWK && p.sq[61] == '.' && p.sq[62] == '.' && p.sq[63] == 'R' &&
                            !isAttacked(p, 60, false) && !isAttacked(p, 61, false) && !isAttacked(p, 62, false)
                        ) out.add(Move(60, 62, null, 2))
                        if (p.castleWQ && p.sq[59] == '.' && p.sq[58] == '.' && p.sq[57] == '.' && p.sq[56] == 'R' &&
                            !isAttacked(p, 60, false) && !isAttacked(p, 59, false) && !isAttacked(p, 58, false)
                        ) out.add(Move(60, 58, null, 2))
                    }
                    if (!forWhite && i == 4) {
                        if (p.castleBK && p.sq[5] == '.' && p.sq[6] == '.' && p.sq[7] == 'r' &&
                            !isAttacked(p, 4, true) && !isAttacked(p, 5, true) && !isAttacked(p, 6, true)
                        ) out.add(Move(4, 6, null, 2))
                        if (p.castleBQ && p.sq[3] == '.' && p.sq[2] == '.' && p.sq[1] == '.' && p.sq[0] == 'r' &&
                            !isAttacked(p, 4, true) && !isAttacked(p, 3, true) && !isAttacked(p, 2, true)
                        ) out.add(Move(4, 2, null, 2))
                    }
                }
                else -> {
                    val dirs = when (piece.uppercaseChar()) {
                        'B' -> arrayOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
                        'R' -> arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
                        else -> arrayOf(1 to 1, 1 to -1, -1 to 1, -1 to -1, 1 to 0, -1 to 0, 0 to 1, 0 to -1)
                    }
                    for ((dr, dc) in dirs) {
                        var nr = r + dr; var nc = c + dc
                        while (onBoard(nr, nc)) {
                            val t = nr * 8 + nc
                            val q = p.sq[t]
                            if (q == '.') out.add(Move(i, t))
                            else {
                                if (isWhite(q) != forWhite) out.add(Move(i, t))
                                break
                            }
                            nr += dr; nc += dc
                        }
                    }
                }
            }
        }
        return out
    }

    fun make(p: Position, m: Move): Position {
        val n = p.copy()
        val piece = n.sq[m.from]
        val white = isWhite(piece)
        val captured = n.sq[m.to]

        n.sq[m.to] = m.promotion ?: piece
        n.sq[m.from] = '.'

        if (m.flag == 1) { // en passant capture
            val capIdx = if (white) m.to + 8 else m.to - 8
            n.sq[capIdx] = '.'
        }
        if (m.flag == 2) { // castle: move the rook
            when (m.to) {
                62 -> { n.sq[61] = 'R'; n.sq[63] = '.' }
                58 -> { n.sq[59] = 'R'; n.sq[56] = '.' }
                6 -> { n.sq[5] = 'r'; n.sq[7] = '.' }
                2 -> { n.sq[3] = 'r'; n.sq[0] = '.' }
            }
        }

        n.epTarget = if (m.flag == 3) (if (white) m.to + 8 else m.to - 8) else -1

        if (piece == 'K') { n.castleWK = false; n.castleWQ = false }
        if (piece == 'k') { n.castleBK = false; n.castleBQ = false }
        if (m.from == 63 || m.to == 63) n.castleWK = false
        if (m.from == 56 || m.to == 56) n.castleWQ = false
        if (m.from == 7 || m.to == 7) n.castleBK = false
        if (m.from == 0 || m.to == 0) n.castleBQ = false

        n.halfmove = if (piece.uppercaseChar() == 'P' || captured != '.') 0 else n.halfmove + 1
        if (!white) n.fullmove++
        n.whiteToMove = !white
        return n
    }

    /** Fully legal moves for the side to move. */
    fun legalMoves(p: Position): List<Move> {
        val side = p.whiteToMove
        return pseudoMoves(p, side).filter { m ->
            val n = make(p, m)
            !inCheck(n, side)
        }
    }

    fun isCheckmate(p: Position) = legalMoves(p).isEmpty() && inCheck(p, p.whiteToMove)
    fun isStalemate(p: Position) = legalMoves(p).isEmpty() && !inCheck(p, p.whiteToMove)

    fun sanLike(p: Position, m: Move): String {
        val piece = p.sq[m.from].uppercaseChar()
        val cap = p.sq[m.to] != '.' || m.flag == 1
        val prefix = if (piece == 'P') "" else piece.toString()
        val promo = m.promotion?.let { "=${it.uppercaseChar()}" } ?: ""
        if (m.flag == 2) return if (m.to == 62 || m.to == 6) "O-O" else "O-O-O"
        return "$prefix${Position.squareName(m.from)}${if (cap) "x" else "-"}${Position.squareName(m.to)}$promo"
    }
}

class ChessEngine {

    data class Info(
        val best: Move?,
        val pv: List<Move>,
        val score: Int,
        val depth: Int,
        val mateIn: Int?,
        val nodes: Long,
    )

    @Volatile
    var stopRequested = false

    /**
     * EXTREME ANALYZE THINK.
     * Not "random aggression": the engine still verifies every idea with a real
     * search + static exchange evaluation, so it never throws material away for
     * free. What EXTREME changes is the *priority*: deeper search, deeper forced
     * mate proving, and bigger bonuses for sound attacking play (king box,
     * shattered pawn shield, pieces aimed at the enemy king).
     */
    @Volatile
    var extreme = false

    /** Attack weight multiplier. Kept modest so material stays real. */
    private val aggro: Int get() = if (extreme) 2 else 1

    private var nodes = 0L

    /** Hard wall-clock deadline; every search phase respects it so the UI never freezes. */
    @Volatile
    var deadlineMs: Long = Long.MAX_VALUE
    private fun timeUp() = System.currentTimeMillis() >= deadlineMs

    private val values = mapOf('P' to 100, 'N' to 325, 'B' to 340, 'R' to 500, 'Q' to 960, 'K' to 0)
    private fun pieceValue(c: Char): Int = if (c == '.') 0 else (values[c.uppercaseChar()] ?: 0)

    private val pawnPst = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0,
        50, 50, 50, 50, 50, 50, 50, 50,
        10, 10, 20, 30, 30, 20, 10, 10,
        5, 5, 10, 25, 25, 10, 5, 5,
        0, 0, 0, 20, 20, 0, 0, 0,
        5, -5, -10, 0, 0, -10, -5, 5,
        5, 10, 10, -20, -20, 10, 10, 5,
        0, 0, 0, 0, 0, 0, 0, 0,
    )
    private val knightPst = intArrayOf(
        -50, -40, -30, -30, -30, -30, -40, -50,
        -40, -20, 0, 0, 0, 0, -20, -40,
        -30, 0, 10, 15, 15, 10, 0, -30,
        -30, 5, 15, 20, 20, 15, 5, -30,
        -30, 0, 15, 20, 20, 15, 0, -30,
        -30, 5, 10, 15, 15, 10, 5, -30,
        -40, -20, 0, 5, 5, 0, -20, -40,
        -50, -40, -30, -30, -30, -30, -40, -50,
    )
    private val bishopPst = intArrayOf(
        -20, -10, -10, -10, -10, -10, -10, -20,
        -10, 0, 0, 0, 0, 0, 0, -10,
        -10, 0, 5, 10, 10, 5, 0, -10,
        -10, 5, 5, 10, 10, 5, 5, -10,
        -10, 0, 10, 10, 10, 10, 0, -10,
        -10, 10, 10, 10, 10, 10, 10, -10,
        -10, 5, 0, 0, 0, 0, 5, -10,
        -20, -10, -10, -10, -10, -10, -10, -20,
    )
    private val rookPst = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0,
        5, 10, 10, 10, 10, 10, 10, 5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        0, 0, 5, 10, 10, 5, 0, 0,
    )
    private val queenPst = intArrayOf(
        -20, -10, -10, -5, -5, -10, -10, -20,
        -10, 0, 0, 0, 0, 0, 0, -10,
        -10, 0, 5, 5, 5, 5, 0, -10,
        -5, 0, 5, 5, 5, 5, 0, -5,
        0, 0, 5, 5, 5, 5, 0, -5,
        -10, 5, 5, 5, 5, 5, 0, -10,
        -10, 0, 5, 0, 0, 0, 0, -10,
        -20, -10, -10, -5, -5, -10, -10, -20,
    )
    private val kingPst = intArrayOf(
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -20, -30, -30, -40, -40, -30, -30, -20,
        -10, -20, -20, -20, -20, -20, -20, -10,
        20, 20, 0, 0, 0, 0, 20, 20,
        20, 30, 10, 0, 0, 10, 30, 20,
    )
    private val kingEndPst = intArrayOf(
        -50, -40, -30, -20, -20, -30, -40, -50,
        -30, -20, -10, 0, 0, -10, -20, -30,
        -30, -10, 20, 30, 30, 20, -10, -30,
        -30, -10, 30, 40, 40, 30, -10, -30,
        -30, -10, 30, 40, 40, 30, -10, -30,
        -30, -10, 20, 30, 30, 20, -10, -30,
        -30, -30, 0, 0, 0, 0, -30, -30,
        -50, -30, -30, -30, -30, -30, -30, -50,
    )

    private val MATE = 100_000

    // ---------------------------------------------------------------- evaluation

    /** Positive = good for the side to move. */
    fun evaluate(p: Position): Int {
        var score = 0
        var whiteMaterial = 0
        var blackMaterial = 0
        var whiteBishops = 0
        var blackBishops = 0
        val whitePawnFiles = IntArray(8)
        val blackPawnFiles = IntArray(8)

        for (i in 0..63) {
            val piece = p.sq[i]
            if (piece == '.') continue
            val white = piece.isUpperCase()
            val type = piece.uppercaseChar()
            if (type == 'P') { if (white) whitePawnFiles[i % 8]++ else blackPawnFiles[i % 8]++ }
            if (type == 'B') { if (white) whiteBishops++ else blackBishops++ }
            val base = values[type] ?: 0
            if (white) whiteMaterial += base else blackMaterial += base
        }
        val endgame = (whiteMaterial + blackMaterial) < 2600

        for (i in 0..63) {
            val piece = p.sq[i]
            if (piece == '.') continue
            val white = piece.isUpperCase()
            val type = piece.uppercaseChar()
            val mirrored = if (white) i else (7 - i / 8) * 8 + i % 8
            val base = values[type] ?: 0
            val pst = when (type) {
                'P' -> pawnPst[mirrored]
                'N' -> knightPst[mirrored]
                'B' -> bishopPst[mirrored]
                'R' -> rookPst[mirrored]
                'Q' -> queenPst[mirrored]
                else -> if (endgame) kingEndPst[mirrored] else kingPst[mirrored]
            }
            var v = base + pst
            if (type == 'P') {
                val file = i % 8
                val own = if (white) whitePawnFiles else blackPawnFiles
                val foe = if (white) blackPawnFiles else whitePawnFiles
                if (own[file] > 1) v -= 14                                   // doubled
                val left = if (file > 0) own[file - 1] else 0
                val right = if (file < 7) own[file + 1] else 0
                if (left == 0 && right == 0) v -= 16                          // isolated
                val fl = if (file > 0) foe[file - 1] else 0
                val fr = if (file < 7) foe[file + 1] else 0
                if (foe[file] == 0 && fl == 0 && fr == 0) {                   // passed
                    val rank = if (white) 7 - i / 8 else i / 8
                    v += 12 + rank * rank * 4
                }
            }
            if (type == 'R') {
                val file = i % 8
                if (whitePawnFiles[file] == 0 && blackPawnFiles[file] == 0) v += 18
                else if ((if (white) whitePawnFiles else blackPawnFiles)[file] == 0) v += 9
            }
            if (white) score += v else score -= v
        }

        if (whiteBishops >= 2) score += 32
        if (blackBishops >= 2) score -= 32

        // mobility: a piece that cannot move is a piece that does not exist
        score += (Rules.pseudoMoves(p, true).size - Rules.pseudoMoves(p, false).size) * 3

        // king hunting (sound version: bonuses, never a licence to give material away)
        score += kingPressure(p, true, blackMaterial) * aggro
        score -= kingPressure(p, false, whiteMaterial) * aggro
        if (Rules.inCheck(p, false)) score += 25 * aggro
        if (Rules.inCheck(p, true)) score -= 25 * aggro
        score += (8 - kingEscapeSquares(p, false)) * 10 * aggro
        score -= (8 - kingEscapeSquares(p, true)) * 10 * aggro
        score += kingShieldDamage(p, false) * 12 * aggro
        score -= kingShieldDamage(p, true) * 12 * aggro

        // hanging material: this is what stops "free food" moves
        score += hangingPenalty(p, false) - hangingPenalty(p, true)

        return if (p.whiteToMove) score else -score
    }

    /** Sum of undefended / under-defended pieces of [white] that the enemy can win. */
    private fun hangingPenalty(p: Position, white: Boolean): Int {
        var worst = 0
        for (i in 0..63) {
            val piece = p.sq[i]
            if (piece == '.' || piece.isUpperCase() != white) continue
            if (piece.uppercaseChar() == 'K') continue
            if (!Rules.isAttacked(p, i, !white)) continue
            val loss = if (!Rules.isAttacked(p, i, white)) pieceValue(piece) else pieceValue(piece) / 6
            if (loss > worst) worst = loss
        }
        // only the biggest threat counts - the side to move can usually save one piece
        return worst / 2
    }

    /** Free squares around a king that are not attacked - the core of the mating net. */
    fun kingEscapeSquares(p: Position, white: Boolean): Int {
        val k = Rules.kingSquare(p, white)
        if (k < 0) return 8
        val kr = k / 8
        val kc = k % 8
        var free = 0
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val r = kr + dr
            val c = kc + dc
            if (r !in 0..7 || c !in 0..7) continue
            val t = r * 8 + c
            val q = p.sq[t]
            if (q != '.' && q.isUpperCase() == white) continue
            if (!Rules.isAttacked(p, t, !white)) free++
        }
        return free
    }

    /** How broken the pawn shield in front of a king is (0 = intact, 3 = fully stripped). */
    private fun kingShieldDamage(p: Position, white: Boolean): Int {
        val k = Rules.kingSquare(p, white)
        if (k < 0) return 0
        val kr = k / 8
        val kc = k % 8
        val d = if (white) -1 else 1
        val pawn = if (white) 'P' else 'p'
        var missing = 0
        for (dc in -1..1) {
            val c = kc + dc
            val r = kr + d
            if (c !in 0..7 || r !in 0..7) { missing++; continue }
            if (p.sq[r * 8 + c] != pawn) missing++
        }
        return missing
    }

    private fun kingPressure(p: Position, attackerWhite: Boolean, defenderMaterial: Int): Int {
        val k = Rules.kingSquare(p, !attackerWhite)
        if (k < 0) return 0
        val kr = k / 8
        val kc = k % 8
        var bonus = 0
        var attackers = 0
        for (i in 0..63) {
            val piece = p.sq[i]
            if (piece == '.' || piece.isUpperCase() != attackerWhite) continue
            val t = piece.uppercaseChar()
            if (t == 'P' || t == 'K') continue
            val dist = maxOf(abs(i / 8 - kr), abs(i % 8 - kc))
            if (dist <= 3) { attackers++; bonus += (4 - dist) * 6 }
        }
        // a real attack needs several attackers - one lonely queen is not an attack
        if (attackers >= 2) bonus += attackers * attackers * 8
        // push the lone enemy king to the edge in endgames
        if (defenderMaterial < 500) {
            bonus += ((abs(3.5 - kc) + abs(3.5 - kr)) * 8).toInt()
        }
        return bonus
    }

    // ------------------------------------------------- static exchange evaluation

    /** Cheapest attacker of [target] for [byWhite] on a raw board, or -1. */
    private fun leastValuableAttacker(b: CharArray, target: Int, byWhite: Boolean): Int {
        val r = target / 8
        val c = target % 8
        var best = -1
        var bestVal = Int.MAX_VALUE
        fun consider(i: Int) {
            val v = pieceValue(b[i])
            val w = if (b[i].uppercaseChar() == 'K') 10_000 else v
            if (w < bestVal) { bestVal = w; best = i }
        }
        val pd = if (byWhite) 1 else -1
        for (dc in intArrayOf(-1, 1)) {
            val ar = r + pd; val ac = c + dc
            if (ar in 0..7 && ac in 0..7 && b[ar * 8 + ac] == (if (byWhite) 'P' else 'p')) consider(ar * 8 + ac)
        }
        for ((dr, dc) in arrayOf(2 to 1, 2 to -1, -2 to 1, -2 to -1, 1 to 2, 1 to -2, -1 to 2, -1 to -2)) {
            val ar = r + dr; val ac = c + dc
            if (ar !in 0..7 || ac !in 0..7) continue
            val q = b[ar * 8 + ac]
            if (q != '.' && q.uppercaseChar() == 'N' && q.isUpperCase() == byWhite) consider(ar * 8 + ac)
        }
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val ar = r + dr; val ac = c + dc
            if (ar !in 0..7 || ac !in 0..7) continue
            val q = b[ar * 8 + ac]
            if (q != '.' && q.uppercaseChar() == 'K' && q.isUpperCase() == byWhite) consider(ar * 8 + ac)
        }
        val diag = arrayOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
        val orth = arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        for ((dirs, pieces) in listOf(diag to "BQ", orth to "RQ")) {
            for ((dr, dc) in dirs) {
                var ar = r + dr; var ac = c + dc
                while (ar in 0..7 && ac in 0..7) {
                    val q = b[ar * 8 + ac]
                    if (q != '.') {
                        if (q.isUpperCase() == byWhite && pieces.contains(q.uppercaseChar())) consider(ar * 8 + ac)
                        break
                    }
                    ar += dr; ac += dc
                }
            }
        }
        return best
    }

    /**
     * Static exchange evaluation: material won/lost if the whole capture sequence
     * on [m].to is played out. Negative = the move simply hands material over.
     * This single function is what stops the engine from feeding the opponent.
     */
    fun see(p: Position, m: Move): Int {
        val b = p.sq.copyOf()
        val mover = b[m.from]
        if (mover == '.') return 0
        var side = mover.isUpperCase()
        val gain = IntArray(34)
        gain[0] = if (m.flag == 1) 100 else pieceValue(b[m.to])
        if (m.promotion != null) gain[0] += pieceValue(m.promotion!!) - 100
        var attackerVal = if (m.promotion != null) pieceValue(m.promotion!!) else pieceValue(mover)
        b[m.to] = m.promotion ?: mover
        b[m.from] = '.'
        if (m.flag == 1) b[if (side) m.to + 8 else m.to - 8] = '.'
        side = !side
        var d = 0
        while (d < 30) {
            val from = leastValuableAttacker(b, m.to, side)
            if (from < 0) break
            d++
            gain[d] = attackerVal - gain[d - 1]
            attackerVal = pieceValue(b[from])
            b[m.to] = b[from]
            b[from] = '.'
            side = !side
            if (gain[d] < 0 && gain[d - 1] < 0) break
        }
        while (d > 0) {
            gain[d - 1] = -maxOf(-gain[d - 1], gain[d])
            d--
        }
        return gain[0]
    }

    // ------------------------------------------------------------ move ordering

    private val killers = Array(128) { arrayOfNulls<Move>(2) }
    private val history = HashMap<Int, Int>()

    private fun keyOf(m: Move) = m.from * 64 + m.to

    private fun moveScore(p: Position, m: Move, ply: Int = 0, ttMove: Move? = null): Int {
        if (ttMove != null && m.from == ttMove.from && m.to == ttMove.to && m.promotion == ttMove.promotion) {
            return 2_000_000
        }
        var s = 0
        val victim = p.sq[m.to]
        val capture = victim != '.' || m.flag == 1
        if (capture || m.promotion != null) {
            val exch = see(p, m)
            s += if (exch >= 0) 100_000 + exch * 10 else -60_000 + exch * 5
        }
        if (m.promotion != null) s += 8_000

        val n = Rules.make(p, m)
        val givesCheck = Rules.inCheck(n, n.whiteToMove)
        if (givesCheck) {
            val exch = see(p, m)
            val replies = Rules.legalMoves(n).size
            if (replies == 0) return 3_000_000                     // mate now
            // A check is only "forcing" if it does not just donate a piece.
            val sound = exch >= 0 || replies <= 2
            if (sound) {
                s += 40_000
                s += (24 - replies.coerceAtMost(24)) * 400
                if (extreme) s += (8 - kingEscapeSquares(n, !p.whiteToMove)) * 500
            } else {
                s += 2_000 + exch * 4                              // speculative, try late
            }
        } else if (!capture) {
            // quiet moves: box the enemy king in, but never above sound material
            s += (8 - kingEscapeSquares(n, !p.whiteToMove)) * (if (extreme) 60 else 20)
            if (extreme) {
                val k = Rules.kingSquare(n, !p.whiteToMove)
                if (k >= 0) {
                    val dist = maxOf(abs(m.to / 8 - k / 8), abs(m.to % 8 - k % 8))
                    if (dist <= 2 && see(p, m) >= 0) s += (3 - dist) * 120
                }
            }
            if (ply in killers.indices) {
                if (killers[ply][0] == m) s += 9_000
                else if (killers[ply][1] == m) s += 7_000
            }
            s += (history[keyOf(m)] ?: 0).coerceAtMost(6_000)
        }
        return s
    }

    private fun storeKiller(ply: Int, m: Move, depth: Int) {
        if (ply !in killers.indices) return
        if (killers[ply][0] != m) {
            killers[ply][1] = killers[ply][0]
            killers[ply][0] = m
        }
        history[keyOf(m)] = (history[keyOf(m)] ?: 0) + depth * depth
    }

    // ------------------------------------------------------------- mate solver

    /**
     * Dedicated forced-mate solver. The attacker is allowed ONLY checks (or an
     * immediate mate); the defender may answer with everything. The line returned
     * is always forced - never a guess.
     */
    fun findForcedMate(p: Position, maxPlies: Int): List<Move>? {
        for (plies in 1..maxPlies step 2) {
            val line = ArrayList<Move>()
            if (mateAttack(p, plies, line)) return line
            if (stopRequested || timeUp()) return null
        }
        return null
    }

    private fun mateAttack(p: Position, plies: Int, out: MutableList<Move>): Boolean {
        if (stopRequested || timeUp() || plies <= 0) return false
        nodes++
        val moves = Rules.legalMoves(p).sortedByDescending { moveScore(p, it) }
        for (m in moves) {
            if (stopRequested || timeUp()) return false
            val n = Rules.make(p, m)
            if (!Rules.inCheck(n, n.whiteToMove)) continue          // only forcing tries
            if (Rules.legalMoves(n).isEmpty()) {                    // checkmate
                out.clear(); out.add(m); return true
            }
            if (plies == 1) continue
            val sub = ArrayList<Move>()
            if (mateDefend(n, plies - 1, sub)) {
                out.clear(); out.add(m); out.addAll(sub); return true
            }
        }
        return false
    }

    private fun mateDefend(p: Position, plies: Int, out: MutableList<Move>): Boolean {
        nodes++
        if (stopRequested || timeUp()) return false
        val replies = Rules.legalMoves(p)
        if (replies.isEmpty()) return Rules.inCheck(p, p.whiteToMove)
        var longest: List<Move> = emptyList()
        for (m in replies) {
            if (stopRequested || timeUp()) return false
            val sub = ArrayList<Move>()
            if (!mateAttack(Rules.make(p, m), plies - 1, sub)) return false
            if (sub.size + 1 > longest.size) longest = listOf(m) + sub
        }
        out.clear(); out.addAll(longest)
        return true
    }

    // ------------------------------------------------------------------ search

    private class TTEntry(val depth: Int, val score: Int, val flag: Int, val move: Move?)

    private val tt = HashMap<String, TTEntry>()

    private fun quiesce(p: Position, alpha0: Int, beta: Int, ply: Int): Int {
        nodes++
        if (stopRequested || timeUp()) return evaluate(p)
        var alpha = alpha0
        val stand = evaluate(p)
        if (stand >= beta) return beta
        if (stand > alpha) alpha = stand
        if (ply > 32) return alpha

        val caps = Rules.legalMoves(p)
            .filter { (p.sq[it.to] != '.' || it.promotion != null || it.flag == 1) && see(p, it) >= 0 }
            .sortedByDescending { moveScore(p, it) }
        for (m in caps) {
            if (stopRequested || timeUp()) return alpha
            val score = -quiesce(Rules.make(p, m), -beta, -alpha, ply + 1)
            if (score >= beta) return beta
            if (score > alpha) alpha = score
        }
        return alpha
    }

    private fun search(p: Position, depth: Int, alpha0: Int, beta: Int, ply: Int, pvOut: MutableList<Move>): Int {
        if (stopRequested || timeUp()) return 0
        nodes++
        val moves = Rules.legalMoves(p)
        if (moves.isEmpty()) {
            return if (Rules.inCheck(p, p.whiteToMove)) -MATE + ply else 0
        }
        val inCheckHere = Rules.inCheck(p, p.whiteToMove)
        val d = if (inCheckHere) depth + 1 else depth          // check extension
        if (d <= 0) return quiesce(p, alpha0, beta, ply)

        // mate-distance pruning: always prefer the FASTEST mate
        var alpha = maxOf(alpha0, -MATE + ply)
        val hiBeta = minOf(beta, MATE - ply - 1)
        if (alpha >= hiBeta) return alpha

        val key = p.toFen()
        var ttMove: Move? = null
        tt[key]?.let { e ->
            ttMove = e.move
            if (e.depth >= d && ply > 0) {
                when (e.flag) {
                    0 -> return e.score
                    1 -> if (e.score >= hiBeta) return e.score
                    2 -> if (e.score <= alpha) return e.score
                }
            }
        }

        val ordered = moves.sortedByDescending { moveScore(p, it, ply, ttMove) }
        var localPv: List<Move> = emptyList()
        var bestMove: Move? = null
        var raisedAlpha = false

        for ((idx, m) in ordered.withIndex()) {
            val childPv = ArrayList<Move>()
            val n = Rules.make(p, m)
            val capture = p.sq[m.to] != '.' || m.flag == 1
            val givesCheck = Rules.inCheck(n, n.whiteToMove)
            val forcing = givesCheck || capture || m.promotion != null

            // never search a move that just gives material away at full depth
            val losing = (capture || givesCheck) && see(p, m) < 0
            var nextDepth = d - 1
            if (!forcing && !inCheckHere && idx >= 4 && d >= 3) nextDepth = d - 2
            if (losing && !inCheckHere && d >= 3) nextDepth = minOf(nextDepth, d - 2)

            var score = -search(n, nextDepth, -hiBeta, -alpha, ply + 1, childPv)
            if (score > alpha && nextDepth < d - 1 && !stopRequested && !timeUp()) {
                childPv.clear()
                score = -search(n, d - 1, -hiBeta, -alpha, ply + 1, childPv)
            }
            if (stopRequested || timeUp()) break
            if (score >= hiBeta) {
                if (!capture && m.promotion == null) storeKiller(ply, m, d)
                tt[key] = TTEntry(d, hiBeta, 1, m)
                return hiBeta
            }
            if (score > alpha) {
                alpha = score
                raisedAlpha = true
                bestMove = m
                localPv = listOf(m) + childPv
            }
        }
        if (localPv.isNotEmpty()) { pvOut.clear(); pvOut.addAll(localPv) }
        if (!stopRequested && !timeUp()) {
            tt[key] = TTEntry(d, alpha, if (raisedAlpha) 0 else 2, bestMove)
        }
        return alpha
    }

    /**
     * Iterative deepening. [onInfo] fires after every COMPLETED depth, so the arrow
     * on the board is always a fully analysed move - never a quick guess and never
     * a random move. Partial (timed-out) iterations are discarded.
     */
    fun analyze(position: Position, maxDepth: Int = 8, timeLimitMs: Long = 8000, onInfo: ((Info) -> Unit)? = null): Info {
        stopRequested = false
        nodes = 0
        history.clear()
        tt.clear()
        for (k in killers.indices) { killers[k][0] = null; killers[k][1] = null }
        val start = System.currentTimeMillis()
        deadlineMs = start + timeLimitMs
        var bestInfo = Info(null, emptyList(), 0, 0, null, 0)
        val root = position.copy()
        val rootMoves = Rules.legalMoves(root)
        if (rootMoves.isEmpty()) return bestInfo
        if (rootMoves.size == 1) {
            bestInfo = Info(rootMoves[0], rootMoves, 0, 1, null, nodes)
            onInfo?.invoke(bestInfo)
            deadlineMs = Long.MAX_VALUE
            return bestInfo
        }

        // Phase 1 - short forced-mate probe (cheap, always correct when it hits).
        val mateBudget = start + (timeLimitMs / 4).coerceAtLeast(150L)
        deadlineMs = mateBudget
        var mateLine = findForcedMate(root, if (extreme) 7 else 5)
        deadlineMs = start + timeLimitMs
        if (mateLine != null) {
            val mateIn = (mateLine.size + 1) / 2
            bestInfo = Info(mateLine.firstOrNull(), mateLine, MATE - mateLine.size, mateLine.size, mateIn, nodes)
            onInfo?.invoke(bestInfo)
            deadlineMs = Long.MAX_VALUE
            return bestInfo
        }
        if (stopRequested) return bestInfo

        // Phase 2 - full iterative deepening search. Only completed depths count.
        val topDepth = if (extreme) maxDepth + 4 else maxDepth
        for (depth in 1..topDepth) {
            if (stopRequested || timeUp()) break
            val pv = ArrayList<Move>()
            val score = search(root, depth, -MATE * 2, MATE * 2, 0, pv)
            if (stopRequested) break
            if (timeUp() && bestInfo.best != null) break      // discard partial iteration
            if (pv.isEmpty()) break
            val mateIn = if (abs(score) > MATE - 200) {
                val plies = MATE - abs(score)
                val movesToMate = (plies + 1) / 2
                if (score > 0) movesToMate else -movesToMate
            } else null
            bestInfo = Info(pv.firstOrNull(), pv.toList(), score, depth, mateIn, nodes)
            onInfo?.invoke(bestInfo)
            if (mateIn != null && mateIn > 0) break
        }

        // Phase 3 - deep forced-mate confirmation with whatever time is left.
        if (bestInfo.mateIn == null && !stopRequested && !timeUp()) {
            mateLine = findForcedMate(root, if (extreme) 11 else 7)
            if (mateLine != null && mateLine.isNotEmpty()) {
                val mateIn = (mateLine.size + 1) / 2
                bestInfo = Info(mateLine.first(), mateLine, MATE - mateLine.size, mateLine.size, mateIn, nodes)
                onInfo?.invoke(bestInfo)
            }
        }

        deadlineMs = Long.MAX_VALUE
        return bestInfo
    }

    fun stop() { stopRequested = true; deadlineMs = 0 }
}
