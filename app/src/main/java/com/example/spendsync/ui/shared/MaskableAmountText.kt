package com.example.spendsync.ui.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.example.spendsync.utils.formatInr
import com.example.spendsync.utils.shouldMaskAmount

/**
 * Drop-in replacement for `Text(text = formatInr(amount), ...)` — renders
 * the real amount when unmasked, or "★★★★★" plus a tap-to-reveal eye icon
 * when [amount] exceeds the masking threshold and [visibility] isn't
 * currently unlocked. [prefix] carries a "+"/"-" sign some call sites
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
        Row(
            modifier = modifier.clickable { visibility.requestUnlock() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$prefix★★★★★",
                color = color,
                fontSize = fontSize,
                fontWeight = fontWeight,
                textAlign = textAlign,
                maxLines = maxLines,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.Visibility,
                contentDescription = "Show amount",
                tint = color,
                modifier = Modifier.size(14.dp),
            )
        }
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
