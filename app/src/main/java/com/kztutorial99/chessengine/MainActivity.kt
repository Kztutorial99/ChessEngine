package com.kztutorial99.chessengine

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(16,19,24)) }
        val title = TextView(this).apply { text="♟  ChessEngine Analyzer"; textSize=20f; setTextColor(Color.WHITE); gravity=Gravity.CENTER_VERTICAL; setPadding(20,14,20,14) }
        val board = ChessBoardView(this)
        val status = TextView(this).apply { text="White to move • Drag or tap a piece"; textSize=15f; setTextColor(Color.LTGRAY); setPadding(20,12,20,12) }
        board.statusView = status
        val controls = LinearLayout(this).apply { gravity=Gravity.CENTER; setPadding(10,6,10,12) }
        val reset = Button(this).apply { text="Reset"; setOnClickListener { board.reset(); status.text="White to move • Drag or tap a piece" } }
        val analyze = Button(this).apply { text="Analyze"; setOnClickListener { board.analyze() } }
        controls.addView(reset, LinearLayout.LayoutParams(0,52,1f).apply { setMargins(6,0,6,0) })
        controls.addView(analyze, LinearLayout.LayoutParams(0,52,1f).apply { setMargins(6,0,6,0) })
        root.addView(title, LinearLayout.LayoutParams(-1,64))
        root.addView(board, LinearLayout.LayoutParams(-1,0,1f))
        root.addView(status, LinearLayout.LayoutParams(-1,60))
        root.addView(controls, LinearLayout.LayoutParams(-1,70))
        setContentView(root)
    }
}
