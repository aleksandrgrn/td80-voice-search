package com.voicesearch.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * Карточка результата: высоту задаёт ряд сетки, ширина всегда 3:4 от неё.
 * Считается при каждом измерении, поэтому переиспользованная карточка не держит
 * ширину от прежней высоты списка (поиск и история отличаются по высоте).
 */
class AspectCardLayout(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = MeasureSpec.getSize(heightMeasureSpec)
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(height * 3 / 4, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        )
    }
}
