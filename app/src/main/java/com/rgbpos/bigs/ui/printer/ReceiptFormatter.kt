package com.rgbpos.bigs.ui.printer

import com.rgbpos.bigs.data.model.OrderResponse
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Formats order data into a Receipt for 58mm thermal printer (32 chars/line).
 */
object ReceiptFormatter {

    private val pesoFmt = NumberFormat.getNumberInstance(Locale("en", "PH")).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    private fun peso(amount: Double) = pesoFmt.format(amount)

    fun format(order: OrderResponse): Receipt {
        val lines = mutableListOf<ReceiptLine>()

        // Header
        lines += ReceiptLine.Text("Big's Crispy Lechon Belly", Align.CENTER, bold = true, doubleHeight = true)
        lines += ReceiptLine.Blank
        lines += ReceiptLine.Separator

        // Order info
        lines += ReceiptLine.TwoColumn("Order:", order.orderNumber)
        lines += ReceiptLine.TwoColumn("Type:", order.orderType.replaceFirstChar { it.uppercase() })
        lines += ReceiptLine.TwoColumn("Customer:", order.customer)

        // Date
        val dateStr = try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val date = sdf.parse(order.createdAt)
            val outFmt = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.US)
            outFmt.format(date ?: Date())
        } catch (_: Exception) {
            order.createdAt
        }
        lines += ReceiptLine.TwoColumn("Date:", dateStr)
        lines += ReceiptLine.Separator

        // Column headers
        lines += ReceiptLine.TwoColumn("Item", "Amount", bold = true)
        lines += ReceiptLine.Separator

        // Items
        for (item in order.items) {
            val name = item.productName ?: "Item #${item.productId}"
            val amount = peso(item.subtotal)
            // First line: product name
            lines += ReceiptLine.Text(name)
            // Second line: qty x price = subtotal
            val detail = "  ${item.quantity} x ${peso(item.unitPrice)}"
            lines += ReceiptLine.TwoColumn(detail, amount)
        }

        lines += ReceiptLine.Separator

        // Totals
        lines += ReceiptLine.TwoColumn("Subtotal:", peso(order.subtotal))
        if (order.discount > 0) {
            lines += ReceiptLine.TwoColumn("Discount:", "-${peso(order.discount)}")
        }
        lines += ReceiptLine.Separator
        lines += ReceiptLine.TwoColumn("TOTAL:", peso(order.total), bold = true)
        lines += ReceiptLine.Separator

        // Footer
        lines += ReceiptLine.Blank
        lines += ReceiptLine.Text("Thank you for dining with us!", Align.CENTER)
        lines += ReceiptLine.Text("Please come again!", Align.CENTER)
        lines += ReceiptLine.Blank

        return Receipt(lines)
    }
}
