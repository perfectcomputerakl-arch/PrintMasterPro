package com.perfectcomputer.printmasterpro

import android.Manifest
import android.app.AlertDialog
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.graphics.BitmapFactory
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.print.PrintManager
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
    private val usbPermissionRequest = 104
    private val sppUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val usbPermissionAction = "com.perfectcomputer.printmasterpro.USB_PERMISSION"

    private lateinit var status: TextView
    private lateinit var usbManager: UsbManager
    private lateinit var nsdManager: NsdManager

    private var usbReceiverRegistered = false
    private var nsdDiscoveryListener: NsdManager.DiscoveryListener? = null
    private val shownPrinters = mutableSetOf<String>()
    private val discoveredWifiPrinters = linkedMapOf<String, NsdServiceInfo>()

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device = getUsbDevice(intent) ?: return
                    status.text = "USB device connected"
                    inspectUsbDevice(device)
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = getUsbDevice(intent)
                    status.text = "USB device disconnected"
                    if (device != null) shownPrinters.remove("usb:${device.deviceId}")
                    toast("USB device disconnected")
                }
                usbPermissionAction -> {
                    val device = getUsbDevice(intent)
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (device != null && granted) {
                        status.text = "USB access granted"
                        showInstallationChoice(
                            "USB printer/device",
                            "USB • VID ${device.vendorId} • PID ${device.productId}",
                            "USB access granted. Android still needs a compatible print service or supported in-app printer protocol."
                        )
                    } else {
                        toast("USB permission was not granted")
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        nsdManager = getSystemService(Context.NSD_SERVICE) as NsdManager
        buildHome()
        registerUsbReceiver()
    }

    override fun onResume() {
        super.onResume()
        detectConnectedUsbDevices()
    }

    override fun onDestroy() {
        stopWifiDiscovery()
        if (usbReceiverRegistered) {
            try { unregisterReceiver(usbReceiver) } catch (_: Exception) {}
            usbReceiverRegistered = false
        }
        super.onDestroy()
    }

    private fun registerUsbReceiver() {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(usbPermissionAction)
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(usbReceiver, filter)
            }
            usbReceiverRegistered = true
        } catch (_: Exception) {
            status.text = "USB detection receiver could not start"
        }
    }

    private fun getUsbDevice(intent: Intent): UsbDevice? {
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
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
            text = "Connect a printer. PRINT MASTER PRO will search and guide you through setup."
            textSize = 15f
            setTextColor(0xFF26344D.toInt())
            setPadding(4, 22, 4, 16)
        }
        root.addView(intro)

        addButton(root, "🔍  SEARCH & CONNECT PRINTER") { searchAllPrinters() }
        addButton(root, "🔌  Detect USB / OTG Printer") { detectConnectedUsbDevices() }
        addButton(root, "📶  Search Wi-Fi Printers") { startWifiDiscovery() }
        addButton(root, "🔵  Bluetooth Printers") { chooseBluetoothPrinter() }
        addButton(root, "📄  Print PDF / Documents") { openDocument() }
        addButton(root, "🖼️  Print Photos") { openPhoto() }
        addButton(root, "🖨️  Android / Office Printer Setup") { showSystemPrintHelp() }
        addButton(root, "🌐  Wi-Fi Thermal Printer (IP)") { showWifiThermalDialog() }
        addButton(root, "⚙️  Printer Setup Help") { showSetupHelp() }

        status = TextView(this).apply {
            text = "Ready • Connect USB, Wi-Fi or Bluetooth printer"
            textSize = 13f
            setTextColor(0xFF52627A.toInt())
            setPadding(6, 18, 6, 0)
        }
        root.addView(status)
        setContentView(ScrollView(this).apply { addView(root) })
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
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 10 }
        parent.addView(button, params)
    }

    private fun searchAllPrinters() {
        detectConnectedUsbDevices()
        searchPairedBluetoothPrinters(showEmptyMessage = false)
        startWifiDiscovery()
        status.text = "Searching USB, paired Bluetooth and Wi-Fi printers…"
        toast("Printer search started")
    }

    // USB / OTG detection: Android reports USB IDs, not necessarily the printer's marketing model.
    private fun detectConnectedUsbDevices() {
        val devices = usbManager.deviceList.values.toList()
        if (devices.isEmpty()) {
            status.text = "No USB device detected • Connect printer using a supported OTG cable"
            return
        }
        devices.forEach { inspectUsbDevice(it) }
    }

    private fun inspectUsbDevice(device: UsbDevice) {
        val key = "usb:${device.deviceId}"
        if (!shownPrinters.add(key)) return

        val isPrinterClass = device.deviceClass == 7 ||
            (0 until device.interfaceCount).any { device.getInterface(it).interfaceClass == 7 }

        val label = if (isPrinterClass) "USB printer detected" else "USB device detected"
        val details = buildString {
            append("Connection: USB OTG\n")
            append("Device: ${device.productName ?: "Unknown USB device"}\n")
            append("Manufacturer: ${device.manufacturerName ?: "Not reported by device"}\n")
            append("Vendor ID: ${device.vendorId}\nProduct ID: ${device.productId}\n")
            if (!isPrinterClass) append("\nThe USB device does not identify itself as a standard USB printer. It may still need manual checking.")
        }

        status.text = "$label • VID ${device.vendorId} / PID ${device.productId}"
        val permissionIntent = Intent(usbPermissionAction).setPackage(packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val pendingIntent = PendingIntent.getBroadcast(this, device.deviceId, permissionIntent, flags)

        if (usbManager.hasPermission(device)) {
            showInstallationChoice(label, details, "USB permission is already available.")
        } else {
            AlertDialog.Builder(this)
                .setTitle("PRINTER / USB DEVICE DETECTED")
                .setMessage("$details\n\nAllow PRINT MASTER PRO to access this USB device?")
                .setPositiveButton("Allow & Continue") { _, _ ->
                    try { usbManager.requestPermission(device, pendingIntent) }
                    catch (e: Exception) { toast("USB permission request failed: ${e.localizedMessage}") }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showInstallationChoice(name: String, details: String, extra: String) {
        AlertDialog.Builder(this)
            .setTitle("PRINTER DETECTED")
            .setMessage("$name\n\n$details\n\n$extra\n\nChoose setup method:")
            .setPositiveButton("AUTOMATIC SETUP") { _, _ -> automaticSetup(name) }
            .setNegativeButton("MANUAL SETUP") { _, _ -> manualSetup(name, details) }
            .setNeutralButton("Later", null)
            .show()
    }

    private fun automaticSetup(printerName: String) {
        AlertDialog.Builder(this)
            .setTitle("Automatic printer setup")
            .setMessage(
                "PRINT MASTER PRO will use Android's available printing services. " +
                "Android does not allow this app to silently install arbitrary manufacturer drivers. " +
                "If a compatible print service is installed, select the printer there. " +
                "Otherwise install the printer maker's official Android print service or a compatible Mopria/IPP service."
            )
            .setPositiveButton("OPEN PRINT SETTINGS") { _, _ -> openPrintSettings() }
            .setNegativeButton("Cancel", null)
            .show()
        status.text = "Automatic setup selected for $printerName"
    }

    private fun manualSetup(printerName: String, details: String) {
        AlertDialog.Builder(this)
            .setTitle("Manual printer setup")
            .setMessage(
                "$printerName\n\n$details\n\n" +
                "Choose the connection method and model in the printer's compatible service. " +
                "For network thermal printers, use the IP address and port supplied by the printer."
            )
            .setPositiveButton("OPEN PRINT SETTINGS") { _, _ -> openPrintSettings() }
            .setNeutralButton("Wi-Fi thermal IP") { _, _ -> showWifiThermalDialog() }
            .setNegativeButton("Bluetooth list") { _, _ -> chooseBluetoothPrinter() }
            .show()
    }

    private fun openPrintSettings() {
        try {
            startActivity(Intent("android.settings.ACTION_PRINT_SETTINGS"))
        } catch (_: Exception) {
            toast("Open Android Settings → Connected devices → Printing")
        }
    }

    // Wi-Fi discovery through standard DNS-SD printer services (IPP).
    private fun startWifiDiscovery() {
        stopWifiDiscovery()
        discoveredWifiPrinters.clear()
        val serviceTypes = listOf("_ipp._tcp.", "_ipps._tcp.", "_printer._tcp.")
        var started = false

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                runOnUiThread { status.text = "Searching Wi-Fi printers ($serviceType)…" }
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                val type = serviceInfo.serviceType
                if (serviceTypes.none { type.contains(it.removeSuffix("."), ignoreCase = true) }) return
                val key = "${serviceInfo.serviceName}|$type"
                if (discoveredWifiPrinters.containsKey(key)) return
                discoveredWifiPrinters[key] = serviceInfo
                runOnUiThread {
                    status.text = "Wi-Fi printer/service found: ${serviceInfo.serviceName}"
                    resolveWifiService(serviceInfo)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                discoveredWifiPrinters.remove("${serviceInfo.serviceName}|${serviceInfo.serviceType}")
            }

            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                runOnUiThread { status.text = "Wi-Fi discovery unavailable (error $errorCode)" }
                try { nsdManager.stopServiceDiscovery(this) } catch (_: Exception) {}
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                try { nsdManager.stopServiceDiscovery(this) } catch (_: Exception) {}
            }
        }

        nsdDiscoveryListener = listener
        serviceTypes.forEach { type ->
            try {
                nsdManager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
                started = true
            } catch (_: Exception) { }
        }
        if (!started) {
            status.text = "Could not start Wi-Fi printer discovery"
            toast("Wi-Fi printer discovery could not start")
        } else {
            Toast.makeText(this, "Searching network printers for 12 seconds…", Toast.LENGTH_SHORT).show()
            status.postDelayed({ stopWifiDiscovery() }, 12_000)
        }
    }

    private fun resolveWifiService(service: NsdServiceInfo) {
        try {
            nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    runOnUiThread {
                        showInstallationChoice(
                            serviceInfo.serviceName,
                            "Wi-Fi network service detected\nType: ${serviceInfo.serviceType}",
                            "Could not resolve its IP address automatically."
                        )
                    }
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val host = serviceInfo.host?.hostAddress ?: "IP unavailable"
                    val details = "Connection: Wi-Fi / IPP\nName: ${serviceInfo.serviceName}\nIP: $host\nPort: ${serviceInfo.port}\nService: ${serviceInfo.serviceType}"
                    runOnUiThread {
                        status.text = "Wi-Fi printer found: ${serviceInfo.serviceName} • $host"
                        showInstallationChoice(serviceInfo.serviceName, details, "Network printer service detected.")
                    }
                }
            })
        } catch (_: Exception) {
            status.text = "Wi-Fi printer found: ${service.serviceName}"
        }
    }

    private fun stopWifiDiscovery() {
        val listener = nsdDiscoveryListener ?: return
        try { nsdManager.stopServiceDiscovery(listener) } catch (_: Exception) {}
        nsdDiscoveryListener = null
    }

    private fun openDocument() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "application/pdf",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "text/plain"
                )
            )
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
                Toast.makeText(this, "Unable to read image", Toast.LENGTH_SHORT).show()
                return
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
        AlertDialog.Builder(this)
            .setTitle("Office printer setup")
            .setMessage(
                "For HP, Canon, Epson, Brother, Samsung, Pantum, Xerox and other compatible printers, " +
                "enable the manufacturer's Android print service or a compatible Mopria/IPP service in Android Print Settings. " +
                "Available printers depend on the installed service and printer model."
            )
            .setPositiveButton("Open Print Settings") { _, _ -> openPrintSettings() }
            .setNegativeButton("OK", null)
            .show()
    }

    private fun chooseBluetoothPrinter() {
        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                bluetoothPermissionRequest
            )
            return
        }
        searchPairedBluetoothPrinters(showEmptyMessage = true)
    }

    private fun searchPairedBluetoothPrinters(showEmptyMessage: Boolean) {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            if (showEmptyMessage) toast("Bluetooth is not available on this device")
            return
        }
        try {
            if (!adapter.isEnabled) {
                if (showEmptyMessage) toast("Turn on Bluetooth and pair your printer first")
                return
            }
            val devices = adapter.bondedDevices.toList()
            val printerDevices = devices.filter {
                val n = (it.name ?: "").lowercase()
                n.contains("printer") || n.contains("print") || n.contains("xprinter") ||
                    n.contains("rongta") || n.contains("pos") || n.contains("thermal") ||
                    n.contains("mpt") || n.contains("zjiang") || n.contains("hprt")
            }
            val list = if (printerDevices.isNotEmpty()) printerDevices else devices
            if (list.isEmpty()) {
                if (showEmptyMessage) toast("No paired Bluetooth devices. Pair your printer in Android Bluetooth settings first.")
                return
            }
            if (showEmptyMessage) {
                AlertDialog.Builder(this)
                    .setTitle("Bluetooth printer/device detected")
                    .setItems(list.map { "${it.name ?: "Bluetooth device"}\n${it.address}" }.toTypedArray()) { _, which ->
                        val device = list[which]
                        showInstallationChoice(
                            device.name ?: "Bluetooth printer",
                            "Connection: Bluetooth\nName: ${device.name ?: "Unknown"}\nAddress: ${device.address}",
                            "Paired Bluetooth device selected. Many receipt printers use ESC/POS; compatibility varies by model."
                        )
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        } catch (_: SecurityException) {
            if (showEmptyMessage) toast("Bluetooth permission is required")
        }
    }

    private fun showThermalTextDialog(onPrint: (String) -> Unit) {
        val input = EditText(this).apply {
            hint = "Type receipt text"
            setText("PRINT MASTER PRO\nTest receipt\n\nThank you!\n\n\n")
        }
        AlertDialog.Builder(this)
            .setTitle("Thermal receipt")
            .setView(input)
            .setPositiveButton("Print") { _, _ -> onPrint(input.text.toString()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendBluetooth(device: BluetoothDevice, text: String) {
        Thread {
            var socket: BluetoothSocket? = null
            try {
                socket = device.createRfcommSocketToServiceRecord(sppUuid)
                socket.connect()
                socket.outputStream.use { out ->
                    out.write(byteArrayOf(0x1B, 0x40))
                    out.write(text.toByteArray(Charsets.UTF_8))
                    out.write(byteArrayOf(0x0A, 0x0A, 0x0A))
                    out.flush()
                }
                runOnUiThread {
                    status.text = "Thermal data sent to ${device.name ?: "printer"}"
                    toast("Print data sent")
                }
            } catch (e: Exception) {
                runOnUiThread { toast("Bluetooth print failed: ${e.localizedMessage}") }
            } finally {
                try { socket?.close() } catch (_: IOException) {}
            }
        }.start()
    }

    private fun showWifiThermalDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 4, 20, 4)
        }
        val ip = EditText(this).apply { hint = "Printer IP address"; setSingleLine(true) }
        val port = EditText(this).apply { hint = "Port (usually 9100)"; setText("9100"); inputType = 2 }
        box.addView(ip)
        box.addView(port)
        AlertDialog.Builder(this)
            .setTitle("Wi-Fi thermal printer")
            .setView(box)
            .setPositiveButton("Next") { _, _ ->
                val host = ip.text.toString().trim()
                val p = port.text.toString().toIntOrNull() ?: 9100
                if (host.isBlank()) {
                    toast("Enter printer IP address")
                } else {
                    showThermalTextDialog { text -> sendWifi(host, p, text) }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendWifi(host: String, port: Int, text: String) {
        Thread {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 5000)
                    socket.getOutputStream().use { out ->
                        out.write(byteArrayOf(0x1B, 0x40))
                        out.write(text.toByteArray(Charsets.UTF_8))
                        out.write(byteArrayOf(10, 10, 10))
                        out.flush()
                    }
                }
                runOnUiThread {
                    status.text = "Thermal data sent to $host:$port"
                    toast("Print data sent")
                }
            } catch (e: Exception) {
                runOnUiThread { toast("Wi-Fi print failed: ${e.localizedMessage}") }
            }
        }.start()
    }

    private fun showSetupHelp() {
        AlertDialog.Builder(this)
            .setTitle("Printer setup")
            .setMessage(
                "1. USB/OTG: connect the printer and allow USB access when asked.\n" +
                "2. Wi-Fi: connect phone and printer to the same network; search for IPP printers or enter the IP for a thermal printer.\n" +
                "3. Bluetooth: pair the thermal printer in Android Bluetooth settings first.\n" +
                "4. Automatic setup opens Android Print Settings to use compatible installed print services.\n\n" +
                "Important: Android apps cannot silently install arbitrary Windows-style printer drivers. " +
                "This version detects connection details and guides setup; exact model support depends on Android print services and printer protocols."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == bluetoothPermissionRequest &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            chooseBluetoothPrinter()
        } else if (requestCode == bluetoothPermissionRequest) {
            toast("Bluetooth permission denied")
        }
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
