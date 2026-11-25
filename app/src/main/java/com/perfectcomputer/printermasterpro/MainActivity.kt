package com.perfectcomputer.printmasterpro

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.print.PrintManager
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.print.PrintHelper
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private val pickDocument = 101
    private val pickPhoto = 102
    private val bluetoothPermissionRequest = 103
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildHome()
    }

    private fun buildHome() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 28, 22, 22)
            setBackgroundColor(0xFFF3F6FC.toInt())
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 18, 16, 18)
            setBackgroundColor(0xFF071D4B.toInt())
        }
        val title = TextView(this).apply {
            text = "PRINT MASTER PRO\nUniversal Printing Solution"
            textSize = 22f
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        header.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(header)
        val intro = TextView(this).apply {
            text = "Print documents, photos and receipts from one app."
            textSize = 15f
            setTextColor(0xFF26344D.toInt())
            setPadding(4, 22, 4, 16)
        }
        root.addView(intro)

        addButton(root, "📄  Print PDF / Documents") { openDocument() }
        addButton(root, "🖼️  Print Photos") { openPhoto() }
        addButton(root, "🖨️  Android / Wi-Fi Office Printer") { showSystemPrintHelp() }
        addButton(root, "🔵  Bluetooth Thermal Printer") { chooseBluetoothPrinter() }
        addButton(root, "🌐  Wi-Fi Thermal Printer") { showWifiThermalDialog() }
        addButton(root, "⚙️  Printer Setup Help") { showSetupHelp() }

        status = TextView(this).apply {
            text = "Ready • Connect a printer to start"
            textSize = 13f
            setTextColor(0xFF52627A.toInt())
            setPadding(6, 18, 6, 0)
        }
        root.addView(status)
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
    }

    private fun addButton(parent: LinearLayout, label: String, action: () -> Unit) {
        val button = Button(this).apply {
            text = label
            textSize = 15f
            isAllCaps = false
            setTextColor(0xFFFFFFFF.toInt())
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF0B347C.toInt())
            setPadding(8, 14, 8, 14)
            setOnClickListener { action() }
        }
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.bottomMargin = 10
        parent.addView(button, params)
    }

    private fun openDocument() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/plain"))
        }
        startActivityForResult(intent, pickDocument)
    }

    private fun openPhoto() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(intent, pickPhoto)
    }

    @Deprecated("Using activity result API for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            pickDocument -> openWithDocumentViewer(uri)
            pickPhoto -> printPhoto(uri)
        }
    }

    private fun openWithDocumentViewer(uri: Uri) {
        val mime = contentResolver.getType(uri) ?: "application/pdf"
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(view, "Open document to print"))
            status.text = "Document opened • Choose Print in your document viewer"
        } catch (_: Exception) {
            Toast.makeText(this, "No compatible document viewer found", Toast.LENGTH_LONG).show()
        }
    }

    private fun printPhoto(uri: Uri) {
        try {
            val bitmap = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            if (bitmap == null) {
                Toast.makeText(this, "Unable to read image", Toast.LENGTH_SHORT).show(); return
            }
            PrintHelper(this).apply {
                scaleMode = PrintHelper.SCALE_MODE_FIT
                colorMode = PrintHelper.COLOR_MODE_COLOR
            }.printBitmap("Print Master Pro Photo", bitmap)
            status.text = "Photo sent to Android print service"
        } catch (e: Exception) {
            Toast.makeText(this, "Photo print failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showSystemPrintHelp() {
        val pm = getSystemService(PRINT_SERVICE) as PrintManager
        AlertDialog.Builder(this)
            .setTitle("Office printer printing")
            .setMessage("For HP, Canon, Epson, Brother, Samsung, Pantum, Xerox and other compatible printers, install the printer manufacturer's Android print service or Mopria/IPP service. Then open a PDF or photo and select Print. Available printers depend on the installed service and printer model.")
            .setPositiveButton("Open Print Settings") { _, _ ->
                try { startActivity(Intent("android.settings.ACTION_PRINT_SETTINGS")) }
                catch (_: Exception) { Toast.makeText(this, "Open Android Settings → Connected devices → Printing", Toast.LENGTH_LONG).show() }
            }
            .setNegativeButton("OK", null).show()
    }

    private fun chooseBluetoothPrinter() {
        if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), bluetoothPermissionRequest)
            return
        }
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) { toast("Bluetooth is not available on this device"); return }
        if (!adapter.isEnabled) { toast("Turn on Bluetooth and pair your thermal printer first"); return }
        try {
            val devices = adapter.bondedDevices.toList()
            if (devices.isEmpty()) { toast("No paired printers. Pair the printer in Android Bluetooth settings first."); return }
            AlertDialog.Builder(this).setTitle("Select paired printer")
                .setItems(devices.map { "${it.name ?: "Bluetooth device"}\n${it.address}" }.toTypedArray()) { _, which ->
                    showThermalTextDialog { text -> sendBluetooth(devices[which], text) }
                }.setNegativeButton("Cancel", null).show()
        } catch (e: SecurityException) { toast("Bluetooth permission is required") }
    }

    private fun showThermalTextDialog(onPrint: (String) -> Unit) {
        val input = EditText(this).apply { hint = "Type receipt text"; setText("PRINT MASTER PRO\nTest receipt\n\nThank you!\n\n\n") }
        AlertDialog.Builder(this).setTitle("Thermal receipt")
            .setView(input).setPositiveButton("Print") { _, _ -> onPrint(input.text.toString()) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun sendBluetooth(device: BluetoothDevice, text: String) {
        Thread {
            var socket: BluetoothSocket? = null
            try {
                socket = device.createRfcommSocketToServiceRecord(sppUuid)
                socket.connect()
                socket.outputStream.use { out ->
                    out.write(byteArrayOf(0x1B, 0x40)) // ESC @ initialize
                    out.write(text.toByteArray(Charsets.UTF_8))
                    out.write(byteArrayOf(0x0A, 0x0A, 0x0A))
                    out.flush()
                }
                runOnUiThread { status.text = "Thermal data sent to ${device.name ?: "printer"}"; toast("Print data sent") }
            } catch (e: Exception) {
                runOnUiThread { toast("Bluetooth print failed: ${e.localizedMessage}") }
            } finally { try { socket?.close() } catch (_: IOException) {} }
        }.start()
    }

    private fun showWifiThermalDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 4, 20, 4) }
        val ip = EditText(this).apply { hint = "Printer IP address"; setSingleLine(true) }
        val port = EditText(this).apply { hint = "Port (usually 9100)"; setText("9100"); inputType = 2 }
        box.addView(ip); box.addView(port)
        AlertDialog.Builder(this).setTitle("Wi-Fi thermal printer").setView(box)
            .setPositiveButton("Next") { _, _ ->
                val host = ip.text.toString().trim(); val p = port.text.toString().toIntOrNull() ?: 9100
                if (host.isBlank()) { toast("Enter printer IP address") } else showThermalTextDialog { text -> sendWifi(host, p, text) }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun sendWifi(host: String, port: Int, text: String) {
        Thread {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 5000)
                    socket.getOutputStream().use { out ->
                        out.write(byteArrayOf(0x1B, 0x40)); out.write(text.toByteArray(Charsets.UTF_8)); out.write(byteArrayOf(10, 10, 10)); out.flush()
                    }
                }
                runOnUiThread { status.text = "Thermal data sent to $host:$port"; toast("Print data sent") }
            } catch (e: Exception) { runOnUiThread { toast("Wi-Fi print failed: ${e.localizedMessage}") } }
        }.start()
    }

    private fun showSetupHelp() {
        AlertDialog.Builder(this).setTitle("Printer setup")
            .setMessage("1. Pair Bluetooth thermal printers in Android Bluetooth settings.\n2. For Wi-Fi thermal printers, connect phone and printer to the same network and enter the printer IP.\n3. For normal inkjet/laser printers, install a compatible Android print service.\n\nNote: Thermal models use different command sets and character encodings. This starter supports basic ESC/POS text output; advanced images, barcodes, cutters and model-specific commands need testing per printer.")
            .setPositiveButton("OK", null).show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == bluetoothPermissionRequest && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) chooseBluetoothPrinter()
        else if (requestCode == bluetoothPermissionRequest) toast("Bluetooth permission denied")
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
