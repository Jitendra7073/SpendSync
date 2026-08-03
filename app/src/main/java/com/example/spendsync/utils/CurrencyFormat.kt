package com.example.spendsync.utils

/** App only ever deals in INR — formats a raw amount as "₹1,234.56". */
fun formatInr(amount: Double): String = "₹${"%,.2f".format(amount)}"
