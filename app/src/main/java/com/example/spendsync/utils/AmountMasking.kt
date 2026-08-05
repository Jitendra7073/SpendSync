package com.example.spendsync.utils

internal const val AMOUNT_MASK_THRESHOLD = 1000.0

internal fun shouldMaskAmount(amount: Double, isVisible: Boolean): Boolean =
    amount > AMOUNT_MASK_THRESHOLD && !isVisible
