package com.countdown.widgets.viewer

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.widget.ImageView
import com.countdown.widgets.data.PhotoStorage
import com.countdown.widgets.data.WidgetStore

/** Full screen photo. Left third = previous, right third = next, middle = close. */
class PhotoViewerActivity : Activity() {
    private lateinit var img: ImageView
    private var paths: List<String> = emptyList()
    private var idx = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cfg = WidgetStore.get(this, intent.getIntExtra("widgetId", 0))
        paths = cfg?.photos?.map { it.path }?.filter { it.isNotEmpty() } ?: emptyList()
        idx = (cfg?.int("idx", 0) ?: 0).coerceIn(0, maxOf(0, paths.size - 1))
        img = ImageView(this)
        img.setBackgroundColor(Color.BLACK)
        img.scaleType = ImageView.ScaleType.FIT_CENTER
        img.setOnTouchListener { v, e ->
            if (e.action == MotionEvent.ACTION_UP) {
                val f = e.x / v.width
                when {
                    f < 1f / 3f -> show(idx - 1)
                    f > 2f / 3f -> show(idx + 1)
                    else -> finish()
                }
            }
            true
        }
        setContentView(img)
        if (paths.isEmpty()) finish() else show(idx)
    }

    private fun show(i: Int) {
        if (paths.isEmpty()) return
        idx = (i + paths.size) % paths.size
        val dm = resources.displayMetrics
        val bmp = PhotoStorage.load(paths[idx], dm.widthPixels, dm.heightPixels)
        if (bmp != null) img.setImageBitmap(bmp)
    }

}
