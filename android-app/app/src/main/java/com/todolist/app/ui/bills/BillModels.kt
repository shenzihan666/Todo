package com.todolist.app.ui.bills

import java.time.LocalDate

/** One row in the bills list UI. */
data class BillListRow(
    val id: String,
    val title: String,
    val amountLabel: String,
    /** Localized short label for income / expense. */
    val typeLabel: String,
    /** ``income`` or ``expense`` for styling. */
    val kind: String,
    val categoryLabel: String?,
    val sortDate: LocalDate,
)

fun formatBillAmount(amount: Double): String = String.format(java.util.Locale.US, "%.2f", amount)
