package com.todolist.app.ui.bills

import java.text.NumberFormat
import java.time.LocalDate
import java.util.Currency
import java.util.Locale

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

fun formatBillAmount(amount: Double): String {
    val nf = NumberFormat.getCurrencyInstance(Locale.getDefault())
    runCatching { nf.currency = Currency.getInstance("CNY") }
    return nf.format(amount)
}
