package com.rgbpos.bigs.ui.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

/**
 * ESC/POS Bluetooth thermal printer driver for 58mm (32 chars/line).
 */
class BluetoothPrinter(private val device: BluetoothDevice) {

    companion object {
        // Standard SPP UUID
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        // ESC/POS commands
        private val ESC_INIT = byteArrayOf(0x1B, 0x40)               // Initialize printer
        private val ESC_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)   // Left align
        private val ESC_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01) // Center align
        private val ESC_ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02)  // Right align
        private val ESC_BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)      // Bold on
        private val ESC_BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)     // Bold off
        private val ESC_DOUBLE_HEIGHT = byteArrayOf(0x1B, 0x21, 0x10) // Double height
        private val ESC_NORMAL_SIZE = byteArrayOf(0x1B, 0x21, 0x00)   // Normal size
        private val ESC_FEED_CUT = byteArrayOf(0x1D, 0x56, 0x42, 0x03) // Feed and partial cut
        private val ESC_FEED_3 = byteArrayOf(0x1B, 0x64, 0x03)      // Feed 3 lines
    }

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    val name: String @SuppressLint("MissingPermission") get() = device.name ?: device.address

    @SuppressLint("MissingPermission")
    @Throws(IOException::class)
    fun connect() {
        socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
        socket?.connect()
        outputStream = socket?.outputStream
    }

    fun isConnected(): Boolean = socket?.isConnected == true

    fun disconnect() {
        try {
            outputStream?.close()
            socket?.close()
        } catch (_: IOException) {}
        outputStream = null
        socket = null
    }

    @Throws(IOException::class)
    fun printReceipt(receipt: Receipt) {
        val os = outputStream ?: throw IOException("Not connected")

        os.write(ESC_INIT)

        for (line in receipt.lines) {
            when (line) {
                is ReceiptLine.Text -> {
                    os.write(alignCommand(line.align))
                    if (line.bold) os.write(ESC_BOLD_ON)
                    if (line.doubleHeight) os.write(ESC_DOUBLE_HEIGHT)
                    os.write("${line.text}\n".toByteArray(Charsets.UTF_8))
                    if (line.doubleHeight) os.write(ESC_NORMAL_SIZE)
                    if (line.bold) os.write(ESC_BOLD_OFF)
                }
                is ReceiptLine.TwoColumn -> {
                    os.write(ESC_ALIGN_LEFT)
                    if (line.bold) os.write(ESC_BOLD_ON)
                    val formatted = twoCol(line.left, line.right, 32)
                    os.write("$formatted\n".toByteArray(Charsets.UTF_8))
                    if (line.bold) os.write(ESC_BOLD_OFF)
                }
                is ReceiptLine.Separator -> {
                    os.write(ESC_ALIGN_LEFT)
                    os.write("${"-".repeat(32)}\n".toByteArray(Charsets.UTF_8))
                }
                is ReceiptLine.Blank -> {
                    os.write("\n".toByteArray(Charsets.UTF_8))
                }
            }
        }

        // Feed and cut
        os.write(ESC_FEED_3)
        os.write(ESC_FEED_CUT)
        os.flush()
    }

    private fun alignCommand(align: Align): ByteArray = when (align) {
        Align.LEFT -> ESC_ALIGN_LEFT
        Align.CENTER -> ESC_ALIGN_CENTER
        Align.RIGHT -> ESC_ALIGN_RIGHT
    }

    private fun twoCol(left: String, right: String, width: Int): String {
        val space = width - left.length - right.length
        return if (space > 0) {
            left + " ".repeat(space) + right
        } else {
            // Truncate left side if needed
            val maxLeft = width - right.length - 1
            if (maxLeft > 0) {
                left.take(maxLeft) + " " + right
            } else {
                left.take(width)
            }
        }
    }
}

enum class Align { LEFT, CENTER, RIGHT }

sealed class ReceiptLine {
    data class Text(
        val text: String,
        val align: Align = Align.LEFT,
        val bold: Boolean = false,
        val doubleHeight: Boolean = false,
    ) : ReceiptLine()

    data class TwoColumn(
        val left: String,
        val right: String,
        val bold: Boolean = false,
    ) : ReceiptLine()

    data object Separator : ReceiptLine()
    data object Blank : ReceiptLine()
}

data class Receipt(val lines: List<ReceiptLine>)
