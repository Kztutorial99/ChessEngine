package com.kztutorial99.chessengine

data class Move(val from: Int, val to: Int, val promotion: Char? = null, val score: Int = 0)

class ChessEngine {
    private val values = mapOf('P' to 100, 'N' to 320, 'B' to 330, 'R' to 500, 'Q' to 900, 'K' to 20000)

    fun bestMove(board: CharArray, depth: Int = 3): Move? {
        val moves = legalMoves(board)
        if (moves.isEmpty()) return null
        var best = moves.first()
        var bestScore = Int.MIN_VALUE
        for (move in moves) {
            val next = board.copyOf(); next[move.to] = next[move.from]; next[move.from] = '.'
            val score = -search(next, depth - 1, false, -Int.MAX_VALUE, Int.MAX_VALUE)
            if (score > bestScore) { bestScore = score; best = move.copy(score = score) }
        }
        return best
    }

    private fun search(board: CharArray, depth: Int, white: Boolean, alpha0: Int, beta0: Int): Int {
        if (depth <= 0) return evaluate(board)
        val moves = legalMoves(board)
        if (moves.isEmpty()) return evaluate(board)
        var alpha = alpha0; val beta = beta0
        var best = Int.MIN_VALUE
        for (move in moves) {
            val next = board.copyOf(); next[move.to] = next[move.from]; next[move.from] = '.'
            val score = -search(next, depth - 1, !white, -beta, -alpha)
            if (score > best) best = score
            if (score > alpha) alpha = score
            if (alpha >= beta) break
        }
        return best
    }

    fun legalMoves(board: CharArray): List<Move> {
        val out = ArrayList<Move>()
        for (i in 0..63) {
            val p = board[i]; if (p == '.') continue
            val white = p.isUpperCase()
            when (p.uppercaseChar()) {
                'P' -> pawn(board, i, white, out)
                'N' -> jumps(board, i, white, intArrayOf(17,15,10,6,-6,-10,-15,-17), out)
                'K' -> jumps(board, i, white, intArrayOf(1,-1,8,-8,9,-9,7,-7), out)
                'B' -> rays(board, i, white, intArrayOf(9,-9,7,-7), out)
                'R' -> rays(board, i, white, intArrayOf(1,-1,8,-8), out)
                'Q' -> rays(board, i, white, intArrayOf(1,-1,8,-8,9,-9,7,-7), out)
            }
        }
        return out
    }

    private fun pawn(b: CharArray, i: Int, w: Boolean, out: MutableList<Move>) {
        val r=i/8; val c=i%8; val d=if(w) -1 else 1; val one=(r+d)*8+c
        if (r+d in 0..7 && b[one]=='.') { out.add(Move(i,one)); if ((w&&r==6)||(!w&&r==1)) { val two=(r+2*d)*8+c; if(b[two]=='.') out.add(Move(i,two)) } }
        for(dc in intArrayOf(-1,1)){ val nc=c+dc; val nr=r+d; if(nc in 0..7&&nr in 0..7){ val t=nr*8+nc; if(b[t]!='.'&&b[t].isUpperCase()!=w) out.add(Move(i,t)) } }
    }
    private fun jumps(b: CharArray,i:Int,w:Boolean,ds:IntArray,out:MutableList<Move>){
        val r=i/8;c@ for(d in ds){ val t=i+d; if(t !in 0..63) continue; val tr=t/8;val tc=t%8; val dr=kotlin.math.abs(tr-r); val dc=kotlin.math.abs(tc-i%8); if(d==17||d==15||d==-15||d==-17){if(dr!=2&&dc!=1||dr!=1&&dc!=2)continue}else if(d in intArrayOf(1,-1)){if(dr!=0)continue}else if(d in intArrayOf(8,-8)){if(dc!=0)continue}; val p=b[t];if(p=='.'||p.isUpperCase()!=w)out.add(Move(i,t)) }
    }
    private fun rays(b:CharArray,i:Int,w:Boolean,ds:IntArray,out:MutableList<Move>){ val r=i/8;val c=i%8; for(d in ds){ var t=i; while(true){ val n=t+d;if(n !in 0..63)break; val nr=n/8;val nc=n%8;if(kotlin.math.abs(nr-r)>kotlin.math.abs((n-i)/8)+1&&d in intArrayOf(1,-1))break;if((d==1||d==-1)&&nr!=r)break;if((d==9||d==-9||d==7||d==-7)&&kotlin.math.abs(nc-c)!=kotlin.math.abs(nr-r))break;t=n;val p=b[t];if(p=='.')out.add(Move(i,t))else{if(p.isUpperCase()!=w)out.add(Move(i,t));break}} } }
    fun evaluate(b: CharArray): Int { var s=0; for(p in b) if(p!='.') s += if(p.isUpperCase()) values[p.uppercaseChar()]!! else -values[p.uppercaseChar()]!!; return s }
}
