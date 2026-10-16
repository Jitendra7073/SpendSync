package com.example.spendsync.ui.shared

import com.example.spendsync.R
import com.example.spendsync.ui.i18n.tr
import androidx.compose.foundation.clickable
import com.example.spendsync.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.example.spendsync.utils.formatInr
import com.example.spendsync.utils.shouldMaskAmount

/**
 * Drop-in replacement for `Text(text = formatInr(amount), ...)` — renders
 * the real amount when unmasked, or a blurred amount when [amount] exceeds
 * the masking threshold and [visibility] isn't currently unlocked.
 * Tapping the blurred amount unlocks it. The blurred figure is always the same dummy ([HIDDEN_AMOUNT]),
 * never the real one, so screen readers, screenshots and its length give nothing away. Blur needs
 * Android 12+; older phones keep the stars. [prefix] carries a "+"/"-" sign some call sites
 * already prepend, so it stays part of the masked/unmasked text either way.
 */
@Composable
fun MaskableAmountText(
    amount: Double,
    visibility: AmountVisibilityState,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    prefix: String = "",
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    if (visibility.isMaskingEnabled && shouldMaskAmount(amount, visibility.isVisible)) {
        val blur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        Text(
            text = prefix + if (blur) HIDDEN_AMOUNT else "★★★★★",
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign,
            maxLines = maxLines,
            modifier = modifier
                .clickable { visibility.requestUnlock() }
                .clearAndSetSemantics { contentDescription = tr(R.string.show_amount) }
                .then(if (blur) Modifier.blur(7.dp, BlurredEdgeTreatment.Unbounded) else Modifier),
        )
    } else {
        Text(
            text = "$prefix${formatInr(amount)}",
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign,
            maxLines = maxLines,
            modifier = modifier,
        )
    }
}

/** What sits under the blur: a fixed dummy, so the blurred shape says nothing about the real amount. */
private const val HIDDEN_AMOUNT = "₹88,888.00"
