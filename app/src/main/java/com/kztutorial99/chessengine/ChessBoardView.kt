package com.kztutorial99.chessengine

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

class ChessBoardView(context: Context) : View(context) {
    var statusView: android.widget.TextView? = null
    private val engine = ChessEngine()
    private val board = CharArray(64) { '.' }
    private var selected = -1
    private var best: Move? = null
    private var dragX = 0f
    private var dragY = 0f
    private var dragging = false
    private val pieceGlyph = mapOf(
        'K' to "♔", 'Q' to "♕", 'R' to "♖", 'B' to "♗", 'N' to "♘", 'P' to "♙",
        'k' to "♚", 'q' to "♛", 'r' to "♜", 'b' to "♝", 'n' to "♞", 'p' to "♟"
    )

    init { reset() }

    fun reset() {
        for(i in 0..63) board[i]='.'
        val back="RNBQKBNR"; for(c in 0..7){board[c]=back[c].lowercaseChar(); board[8+c]='p'; board[48+c]='P'; board[56+c]=back[c]}
        selected=-1; best=null; invalidate()
    }

    fun analyze() {
        best = engine.bestMove(board, 3)
        val m=best
        if(m==null) statusView?.text="No legal moves" else statusView?.text="Engine: ${name(m.from)} → ${name(m.to)}   eval ${"%.2f".format(m.score/100.0)}"
        invalidate()
    }

    private fun name(i:Int):String = "${('a'.code+i%8).toChar()}${8-i/8}"

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size=min(width,height); val left=(width-size)/2f; val top=(height-size)/2f; val sq=size/8f
        val p=Paint(Paint.ANTI_ALIAS_FLAG)
        for(r in 0..7) for(c in 0..7){
            p.color=if((r+c)%2==0) Color.rgb(240,217,181) else Color.rgb(181,136,99)
            canvas.drawRect(left+c*sq,top+r*sq,left+(c+1)*sq,top+(r+1)*sq,p)
        }
        if(selected>=0){p.color=Color.argb(110,255,220,60);canvas.drawRect(left+(selected%8)*sq,top+(selected/8)*sq,left+(selected%8+1)*sq,top+(selected/8+1)*sq,p)}
        val moves=if(selected>=0) engine.legalMoves(board).filter{it.from==selected}else emptyList()
        p.color=Color.argb(150,30,100,30); for(m in moves){val cx=left+(m.to%8+.5f)*sq;val cy=top+(m.to/8+.5f)*sq;canvas.drawCircle(cx,cy,sq*.12f,p)}
        best?.let{drawArrow(canvas,left,top,sq,it.from,it.to,Color.argb(220,50,190,70))}
        if(dragging && selected>=0) drawPiece(canvas,board[selected],dragX,dragY,sq,p) else for(i in 0..63) if(board[i]!='.') drawPiece(canvas,board[i],left+(i%8+.5f)*sq,top+(i/8+.55f)*sq,sq,p)
    }

    private fun drawPiece(canvas:Canvas,ch:Char,x:Float,y:Float,sq:Float,p:Paint){p.textAlign=Paint.Align.CENTER;p.textSize=sq*.78f;p.typeface=Typeface.create("sans",Typeface.NORMAL);p.color=if(ch.isUpperCase())Color.WHITE else Color.rgb(25,25,25);p.setShadowLayer(3f,1f,2f,Color.BLACK);canvas.drawText(pieceGlyph[ch]?:"?",x,y,p);p.clearShadowLayer()}
    private fun drawArrow(canvas:Canvas,l:Float,t:Float,s:Float,a:Int,b:Int,color:Int){val p=Paint(Paint.ANTI_ALIAS_FLAG);p.color=color;p.strokeWidth=s*.09f;p.style=Paint.Style.STROKE;p.strokeCap=Paint.Cap.ROUND;val x1=l+(a%8+.5f)*s;val y1=t+(a/8+.5f)*s;val x2=l+(b%8+.5f)*s;val y2=t+(b/8+.5f)*s;canvas.drawLine(x1,y1,x2,y2,p);p.style=Paint.Style.FILL;val ang=Math.atan2((y2-y1).toDouble(),(x2-x1).toDouble());val h=s*.18f;val x3=x2-h.toFloat()*kotlin.math.cos(ang-.55);val y3=y2-h.toFloat()*kotlin.math.sin(ang-.55);val x4=x2-h.toFloat()*kotlin.math.cos(ang+.55);val y4=y2-h.toFloat()*kotlin.math.sin(ang+.55);canvas.drawPath(Path().apply{moveTo(x2,y2);lineTo(x3,y3);lineTo(x4,y4);close()},p)}

    override fun onTouchEvent(e:MotionEvent):Boolean{
        val size=min(width,height);val l=(width-size)/2f;val t=(height-size)/2f;val s=size/8f
        if(e.x<l||e.x>l+size||e.y<t||e.y>t+size)return true
        val c=((e.x-l)/s).toInt();val r=((e.y-t)/s).toInt();val idx=r*8+c
        when(e.action){
            MotionEvent.ACTION_DOWN->{ if(board[idx]!='.'){selected=idx;dragging=true;dragX=e.x;dragY=e.y;best=null;invalidate()} }
            MotionEvent.ACTION_MOVE->{if(dragging){dragX=e.x;dragY=e.y;invalidate()}}
            MotionEvent.ACTION_UP->{
                if(selected>=0){val legal=engine.legalMoves(board).any{it.from==selected&&it.to==idx};if(legal){board[idx]=board[selected];board[selected]='.';statusView?.text="Moved ${name(selected)} → ${name(idx)} • tap Analyze"};selected=-1;dragging=false;invalidate()}
            }
        }
        return true
    }
}
