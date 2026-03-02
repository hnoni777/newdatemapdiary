package io.github.hnoni777.newdatemapdiary

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class ManualActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual)

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            onBackPressed()
        }
    }
}
