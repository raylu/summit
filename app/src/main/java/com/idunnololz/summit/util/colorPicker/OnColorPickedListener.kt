package com.idunnololz.summit.util.colorPicker

import androidx.annotation.ColorInt
import com.idunnololz.summit.util.colorPicker.view.ColorPicker

interface OnColorPickedListener {
    fun onColorPicked(pickerView: ColorPicker?, @ColorInt color: Int)
}