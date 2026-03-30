package com.todolist.app.ui.bills

import com.todolist.app.data.network.dto.BillReadDto
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

internal fun BillReadDto.toBillListRow(
    incomeLabel: String,
    expenseLabel: String,
): BillListRow {
    val instant = billed_at?.takeIf { it.isNotBlank() } ?: created_at
    val date =
        try {
            val odt = OffsetDateTime.parse(instant)
            odt.atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
        } catch (_: DateTimeParseException) {
            LocalDate.now()
        }
    val lower = type.lowercase()
    val typeLabel =
        when (lower) {
            "income" -> incomeLabel
            "expense" -> expenseLabel
            else -> type
        }
    val kind = if (lower == "income" || lower == "expense") lower else "expense"
    return BillListRow(
        id = id.toString(),
        title = title,
        amountLabel = formatBillAmount(amount),
        typeLabel = typeLabel,
        kind = kind,
        categoryLabel = category?.takeIf { it.isNotBlank() },
        sortDate = date,
    )
}
