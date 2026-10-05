package com.example.spendsync.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.R
import com.example.spendsync.ui.components.AppDialog
import com.example.spendsync.ui.components.AppTextField
import com.example.spendsync.ui.components.DialogAction
import com.example.spendsync.ui.components.Text
import com.example.spendsync.ui.i18n.tr

private val Quick = listOf(
    0xFFFFFFFF, 0xFFF5F5F5, 0xFF111111, 0xFF0B1020, 0xFF2563EB, 0xFF059669, 0xFFDC2626, 0xFFF97316,
    0xFF7C3AED, 0xFFDB2777, 0xFF0EA5E9, 0xFFFACC15,
).map { it.toInt() }

private fun hex(argb: Int) = "#%06X".format(0xFFFFFF and argb)

private fun parseHex(text: String): Int? {
    val t = text.trim().removePrefix("#")
    if (t.length != 6 || t.any { !it.isDigit() && it.lowercaseChar() !in 'a'..'f' }) return null
    return (0xFF000000 or t.toLong(16)).toInt()
}

/**
 * Pick any colour: three sliders (hue, strength, brightness), a hex box, and a row of quick swatches. The preview
 * and the hex update together; "Done" applies it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(title: String, initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    var hexText by remember { mutableStateOf(hex(initial)) }
    val current = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))

    fun setFrom(argb: Int) {
        val a = FloatArray(3); android.graphics.Color.colorToHSV(argb, a)
        hue = a[0]; sat = a[1]; value = a[2]; hexText = hex(argb)
    }

    AppDialog(
        onDismiss = onDismiss,
        title = title,
        primary = DialogAction(tr(R.string.cc_done), { onPick(current) }),
        secondary = DialogAction(tr(R.string.cancel), onDismiss),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(Color(current)).border(0.5.dp, scheme.outlineVariant, RoundedCornerShape(14.dp)))
            LabeledSlider(tr(R.string.cc_hue), hue, 0f..360f) { hue = it; hexText = hex(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))) }
            LabeledSlider(tr(R.string.cc_sat), sat, 0f..1f) { sat = it; hexText = hex(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))) }
            LabeledSlider(tr(R.string.cc_bright), value, 0f..1f) { value = it; hexText = hex(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))) }
            AppTextField(hexText, { hexText = it.take(7); parseHex(it)?.let { c -> val a = FloatArray(3); android.graphics.Color.colorToHSV(c, a); hue = a[0]; sat = a[1]; value = a[2] } }, label = tr(R.string.cc_hex), isError = parseHex(hexText) == null)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Quick.forEach { c ->
                    Box(Modifier.size(30.dp).clip(CircleShape).background(Color(c)).border(0.5.dp, scheme.outlineVariant, CircleShape).clickable { setFrom(c) })
                }
            }
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Text(label, fontSize = 12.sp, color = scheme.onSurfaceVariant)
        Slider(value = value, onValueChange = onChange, valueRange = range, colors = SliderDefaults.colors(thumbColor = scheme.primary, activeTrackColor = scheme.primary))
    }
}
