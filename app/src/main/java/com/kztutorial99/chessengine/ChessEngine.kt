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
     * When on, the engine stops playing "classical" positional chess: every weight
     * that has to do with hunting the enemy king is multiplied, the forced-mate
     * solver digs far deeper, and moves that only create a mate THREAT are still
     * rewarded. Material is treated as ammunition - sacrifices that shrink the
     * enemy king's box are preferred over safe, slow moves.
     */
    @Volatile
    var extreme = false

    private val aggro: Int get() = if (extreme) 4 else 1


    private var nodes = 0L

    private val values = mapOf('P' to 100, 'N' to 320, 'B' to 335, 'R' to 500, 'Q' to 950, 'K' to 0)

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

    private val MATE = 100_000

    /** Positive = good for the side to move. */
    fun evaluate(p: Position): Int {
        var score = 0
        var whiteMaterial = 0
        var blackMaterial = 0
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
                'R' -> 0
                'Q' -> 0
                else -> kingPst[mirrored]
            }
            val v = base + pst
            if (white) { score += v; whiteMaterial += base } else { score -= v; blackMaterial += base }
        }
        // mate-hunting: reward crowding the enemy king (x4 in EXTREME mode)
        score += kingPressure(p, true, blackMaterial) * aggro
        score -= kingPressure(p, false, whiteMaterial) * aggro
        if (Rules.inCheck(p, false)) score += 60 * aggro
        if (Rules.inCheck(p, true)) score -= 60 * aggro
        // mating net: the fewer squares the enemy king has, the better
        score += (8 - kingEscapeSquares(p, false)) * 22 * aggro
        score -= (8 - kingEscapeSquares(p, true)) * 22 * aggro
        if (extreme) {
            // shield-busting: every missing pawn in front of the enemy king is blood in the water
            score += kingShieldDamage(p, false) * 25
            score -= kingShieldDamage(p, true) * 25
        }

        return if (p.whiteToMove) score else -score
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
        for (i in 0..63) {
            val piece = p.sq[i]
            if (piece == '.' || piece.isUpperCase() != attackerWhite) continue
            val t = piece.uppercaseChar()
            if (t == 'P' || t == 'K') continue
            val dist = maxOf(abs(i / 8 - kr), abs(i % 8 - kc))
            bonus += (7 - dist) * 3
        }
        // push the lone enemy king to the edge in endgames
        if (defenderMaterial < 500) {
            bonus += (abs(3.5 - kc) + abs(3.5 - kr)).toInt() * 8
        }
        return bonus
    }

    private val killers = Array(96) { arrayOfNulls<Move>(2) }
    private val history = HashMap<Int, Int>()

    private fun keyOf(m: Move) = m.from * 64 + m.to

    private fun moveScore(p: Position, m: Move, ply: Int = 0): Int {
        var s = 0
        val victim = p.sq[m.to]
        val attacker = p.sq[m.from]
        if (victim != '.') s += 4000 + (values[victim.uppercaseChar()] ?: 0) - (values[attacker.uppercaseChar()] ?: 0) / 10
        if (m.promotion != null) s += 3500
        if (m.flag == 1) s += 4000
        // forcing moves first: this is what makes short mates pop out of the search
        val n = Rules.make(p, m)
        if (Rules.inCheck(n, n.whiteToMove)) {
            s += 9000
            val replies = Rules.legalMoves(n).size
            if (replies == 0) return 1_000_000            // mate now
            s += (24 - replies.coerceAtMost(24)) * 250    // the more forcing, the better
            if (extreme) {
                // EXTREME: a check that also cages the king outranks any capture
                s += (8 - kingEscapeSquares(n, !p.whiteToMove)) * 600
                if (replies <= 2) s += 12_000             // near-forced: hunt it first
            }
        } else {
            // quiet moves that still shrink the enemy king's box
            s += (8 - kingEscapeSquares(n, !p.whiteToMove)) * (if (extreme) 220 else 40)
            if (extreme) {
                // reward pieces stepping INTO the enemy king zone, even as a sacrifice
                val k = Rules.kingSquare(n, !p.whiteToMove)
                if (k >= 0) {
                    val dist = maxOf(abs(m.to / 8 - k / 8), abs(m.to % 8 - k % 8))
                    if (dist <= 2) s += (3 - dist) * 700
                }
            }
        }

        if (ply in killers.indices) {
            if (killers[ply][0] == m) s += 2500
            else if (killers[ply][1] == m) s += 1800
        }
        s += history[keyOf(m)] ?: 0
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

    /**
     * Dedicated forced-mate solver. The attacker is allowed ONLY checks (or an
     * immediate mate); the defender may answer with everything. That makes the
     * tree tiny, so mates far deeper than the normal search can find are proven
     * in milliseconds - and the line returned is always forced.
     */
    fun findForcedMate(p: Position, maxPlies: Int): List<Move>? {
        for (plies in 1..maxPlies step 2) {
            val line = ArrayList<Move>()
            if (mateAttack(p, plies, line)) return line
            if (stopRequested) return null
        }
        return null
    }

    private fun mateAttack(p: Position, plies: Int, out: MutableList<Move>): Boolean {
        if (stopRequested || plies <= 0) return false
        nodes++
        val moves = Rules.legalMoves(p).sortedByDescending { moveScore(p, it) }
        for (m in moves) {
            if (stopRequested) return false
            val n = Rules.make(p, m)
            val givesCheck = Rules.inCheck(n, n.whiteToMove)
            if (!givesCheck) continue                       // only forcing tries
            if (Rules.legalMoves(n).isEmpty()) {            // checkmate
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
        val replies = Rules.legalMoves(p)
        if (replies.isEmpty()) return Rules.inCheck(p, p.whiteToMove)
        var longest: List<Move> = emptyList()
        for (m in replies) {
            if (stopRequested) return false
            val sub = ArrayList<Move>()
            if (!mateAttack(Rules.make(p, m), plies - 1, sub)) return false
            if (sub.size + 1 > longest.size) longest = listOf(m) + sub
        }
        out.clear(); out.addAll(longest)
        return true
    }

    private fun quiesce(p: Position, alpha0: Int, beta: Int, ply: Int): Int {
        nodes++
        var alpha = alpha0
        val stand = evaluate(p)
        if (stand >= beta) return beta
        if (stand > alpha) alpha = stand
        val caps = Rules.legalMoves(p).filter { p.sq[it.to] != '.' || it.promotion != null || it.flag == 1 }
            .sortedByDescending { moveScore(p, it) }
        for (m in caps) {
            if (stopRequested) return alpha
            val score = -quiesce(Rules.make(p, m), -beta, -alpha, ply + 1)
            if (score >= beta) return beta
            if (score > alpha) alpha = score
        }
        return alpha
    }

    private fun search(p: Position, depth: Int, alpha0: Int, beta: Int, ply: Int, pvOut: MutableList<Move>): Int {
        if (stopRequested) return 0
        nodes++
        val moves = Rules.legalMoves(p)
        if (moves.isEmpty()) {
            return if (Rules.inCheck(p, p.whiteToMove)) -MATE + ply else 0
        }
        val inCheckHere = Rules.inCheck(p, p.whiteToMove)
        // check extension: never stop searching in the middle of a forcing sequence
        val d = if (inCheckHere) depth + 1 else depth
        if (d <= 0) return quiesce(p, alpha0, beta, ply)

        // mate-distance pruning: always prefer the FASTEST mate
        var alpha = maxOf(alpha0, -MATE + ply)
        val hiBeta = minOf(beta, MATE - ply - 1)
        if (alpha >= hiBeta) return alpha

        val ordered = moves.sortedByDescending { moveScore(p, it, ply) }
        var localPv: List<Move> = emptyList()
        for ((idx, m) in ordered.withIndex()) {
            val childPv = ArrayList<Move>()
            val n = Rules.make(p, m)
            val forcing = Rules.inCheck(n, n.whiteToMove) || p.sq[m.to] != '.' || m.promotion != null
            // late-move reduction on quiet moves keeps the search laser-focused on forcing play
            val nextDepth = if (!forcing && !inCheckHere && idx >= 6 && d >= 3) d - 2 else d - 1
            var score = -search(n, nextDepth, -hiBeta, -alpha, ply + 1, childPv)
            if (score > alpha && nextDepth < d - 1) {
                childPv.clear()
                score = -search(n, d - 1, -hiBeta, -alpha, ply + 1, childPv)
            }
            if (stopRequested) break
            if (score >= hiBeta) {
                if (p.sq[m.to] == '.' && m.promotion == null) storeKiller(ply, m, d)
                return hiBeta
            }
            if (score > alpha) {
                alpha = score
                localPv = listOf(m) + childPv
            }
        }
        if (localPv.isNotEmpty()) { pvOut.clear(); pvOut.addAll(localPv) }
        return alpha
    }

    /**
     * How dangerous a move is as a *mate threat*: the share of enemy replies that
     * still run into a short forced mate. 100 = every defence loses by force.
     */
    private fun mateThreat(p: Position, m: Move, plies: Int): Int {
        val n = Rules.make(p, m)
        val replies = Rules.legalMoves(n)
        if (replies.isEmpty()) return if (Rules.inCheck(n, n.whiteToMove)) 1000 else 0
        var losing = 0
        for (r in replies) {
            if (stopRequested) return 0
            if (findForcedMate(Rules.make(n, r), plies) != null) losing++
        }
        return losing * 100 / replies.size
    }

    /**
     * Iterative deepening. [onInfo] fires after every completed depth so the UI can
     * show live analysis; the search aborts when [stopRequested] flips to true.
     *
     * In EXTREME mode the mate solver runs much deeper, the search goes two plies
     * further, and quiet moves are re-ranked by how strong a mate threat they create.
     */
    fun analyze(position: Position, maxDepth: Int = 6, timeLimitMs: Long = 8000, onInfo: ((Info) -> Unit)? = null): Info {
        stopRequested = false
        nodes = 0
        history.clear()
        for (k in killers.indices) { killers[k][0] = null; killers[k][1] = null }
        val start = System.currentTimeMillis()
        var bestInfo = Info(null, emptyList(), 0, 0, null, 0)
        val root = position.copy()
        if (Rules.legalMoves(root).isEmpty()) return bestInfo

        // Phase 1 - prove a forced mate with the checks-only solver.
        // Classic: mate in 1..4. EXTREME: mate in 1..7, proven and forced.
        findForcedMate(root, if (extreme) 13 else 7)?.let { line ->
            val mateIn = (line.size + 1) / 2
            bestInfo = Info(line.firstOrNull(), line, MATE - line.size, line.size, mateIn, nodes)
            onInfo?.invoke(bestInfo)
            return bestInfo
        }
        if (stopRequested) return bestInfo

        val topDepth = if (extreme) maxDepth + 2 else maxDepth
        for (depth in 1..topDepth) {
            val pv = ArrayList<Move>()
            val score = search(root, depth, -MATE * 2, MATE * 2, 0, pv)
            if (stopRequested) break
            val mateIn = if (abs(score) > MATE - 200) {
                val plies = MATE - abs(score)
                val moves = (plies + 1) / 2
                if (score > 0) moves else -moves
            } else null
            bestInfo = Info(pv.firstOrNull(), pv.toList(), score, depth, mateIn, nodes)
            onInfo?.invoke(bestInfo)
            if (mateIn != null && mateIn > 0) break
            if (System.currentTimeMillis() - start > timeLimitMs) break
        }

        // Phase 3 (EXTREME only) - no mate yet, so pick the move that puts the
        // enemy king under the heaviest unavoidable mating pressure.
        if (extreme && !stopRequested && bestInfo.mateIn == null) {
            val candidates = Rules.legalMoves(root)
                .sortedByDescending { moveScore(root, it) }
                .take(6)
            var bestMove = bestInfo.best
            var bestThreat = bestInfo.best?.let { mateThreat(root, it, 5) } ?: -1
            for (m in candidates) {
                if (stopRequested) break
                if (System.currentTimeMillis() - start > timeLimitMs * 2) break
                val t = mateThreat(root, m, 5)
                if (t > bestThreat) { bestThreat = t; bestMove = m }
            }
            if (bestThreat > 0 && bestMove != null && bestMove != bestInfo.best) {
                bestInfo = Info(bestMove, listOf(bestMove), bestInfo.score + bestThreat * 10, bestInfo.depth, null, nodes)
                onInfo?.invoke(bestInfo)
            }
        }
        return bestInfo
    }


    fun stop() { stopRequested = true }
}
