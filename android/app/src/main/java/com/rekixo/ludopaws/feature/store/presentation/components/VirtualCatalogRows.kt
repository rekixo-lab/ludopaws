package com.rekixo.ludopaws.feature.store.presentation.components

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver

/** A full-height scroll child that creates only visible rows and one row of overscan. */
internal class VirtualCatalogRows(
    context: Context,
    private val rowCount: Int,
    private val rowHeight: Int,
    private val rowGap: Int,
    private val createRow: (Int) -> View,
) : ViewGroup(context), ViewTreeObserver.OnScrollChangedListener {
    private val rows = linkedMapOf<Int, View>()
    private val visibleBounds = Rect()
    private var firstRow = 0
    private var lastRow = minOf(2, rowCount - 1)
    private val stride get() = rowHeight + rowGap

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnScrollChangedListener(this)
        post { updateWindow() }
    }
    override fun onDetachedFromWindow() {
        if (viewTreeObserver.isAlive) viewTreeObserver.removeOnScrollChangedListener(this)
        super.onDetachedFromWindow()
    }
    override fun onScrollChanged() = updateWindow()
    private fun updateWindow() {
        if (!getLocalVisibleRect(visibleBounds)) return
        val first = (visibleBounds.top / stride - 1).coerceAtLeast(0)
        val last = (visibleBounds.bottom / stride + 1).coerceAtMost(rowCount - 1)
        if (first != firstRow || last != lastRow) {
            firstRow = first
            lastRow = last
            requestLayout()
        }
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val wanted = if (lastRow >= firstRow) firstRow..lastRow else IntRange.EMPTY
        val iterator = rows.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in wanted) { removeView(entry.value); iterator.remove() }
        }
        for (index in wanted) {
            val row = rows.getOrPut(index) { createRow(index).also { addView(it) } }
            row.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(rowHeight, MeasureSpec.EXACTLY))
        }
        setMeasuredDimension(width, (rowCount * stride - rowGap).coerceAtLeast(0))
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        rows.forEach { (index, row) -> row.layout(0, index * stride, r - l, index * stride + rowHeight) }
        if (changed) post { updateWindow() }
    }
}
