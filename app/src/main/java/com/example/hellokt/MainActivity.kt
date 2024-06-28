package com.example.hellokt

//import com.google.zxing.BarcodeFormat
//import com.google.zxing.MultiFormatWriter
//import com.journeyapps.barcodescanner.BarcodeEncoder


import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ListView
import android.widget.SimpleAdapter
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.hellokt.databinding.ActivityMainBinding
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.UUID


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var btPermissions = false
    var bluetoothAdapter: BluetoothAdapter? = null
    private var socket: BluetoothSocket? = null
    private var bluetoothDevice: BluetoothDevice? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var workerThread: Thread? = null
    private lateinit var readBuffer: ByteArray
    private var readBufferPosition = 0
    private val barcodeText = "123456789012"

    private val scanDuration = 5 * 60 * 1000L // 5 minutes in milliseconds

    @Volatile
    var stopWorker = false
    private var value = ""
    private var connection: Connection = Connection()


    @SuppressLint("WrongThread")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        setContentView(view)
    }
    
    fun scanBt(view: View) {
        checkPermission()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun print(view: View) {
        if (btPermissions) {
            print_inv()
        } else {
            checkPermission()
        }
    }


    @SuppressLint("MissingPermission")
    private fun startBluetoothDiscovery() {
        bluetoothAdapter?.takeIf { it.isEnabled }?.apply {
            if (isDiscovering) {
                cancelDiscovery()
            }
            startDiscovery()
            Log.d("MainActivity", "Bluetooth discovery started")

            // Schedule stopping the discovery after 5 minutes
            GlobalScope.launch {
                delay(scanDuration)
                stopBluetoothDiscovery()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBluetoothDiscovery() {
        bluetoothAdapter?.takeIf { it.isDiscovering }?.apply {
            cancelDiscovery()
            Log.d("MainActivity", "Bluetooth discovery stopped")
        }
    }


    fun checkPermission() {
        val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Device dont help suppoted bluetooth", Toast.LENGTH_LONG).show()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                bluetoothPermissionLaucher.launch(Manifest.permission.BLUETOOTH_CONNECT)

            } else {
                bluetoothPermissionLaucher.launch(Manifest.permission.BLUETOOTH_ADMIN)
            }
        }
    }

    private val bluetoothPermissionLaucher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
            val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
            btPermissions = true
            if (bluetoothAdapter?.isEnabled == false) {
                var enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                btActivityResultLaucher.launch(enableIntent)
            } else {
                btScan()
                startBluetoothDiscovery()
            }
        }

    }


    private val btActivityResultLaucher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == RESULT_OK) {
            btScan()
            startBluetoothDiscovery()
        }

    }

    @SuppressLint("MissingPermission")
    private fun btScan() {
        val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
        var builder = AlertDialog.Builder(this@MainActivity)
        var inflater = layoutInflater
        var dialogView: View = inflater.inflate(R.layout.scan_bt, null)
        builder.setCancelable(false)
        builder.setView(dialogView)
        val btLst = dialogView.findViewById<ListView>(R.id.bt_lst)
        var dialog = builder.create()
        val pairedDevices: Set<BluetoothDevice>? =
            bluetoothAdapter?.bondedDevices as Set<BluetoothDevice>
        val ADAhere: SimpleAdapter
        var data: MutableList<Map<String?, Any?>?>? = null
        data = ArrayList()

        if (pairedDevices != null) {
            if (pairedDevices.isNotEmpty()) {
                val datanum1: MutableMap<String?, Any?> = HashMap()
                datanum1["A"] = ""
                datanum1["B"] = ""
                data.add(datanum1)
                for (device in pairedDevices) {
                    val datanum: MutableMap<String?, Any?> = HashMap()
                    datanum["A"] = device.name
                    datanum["B"] = device.address
                    data.add(datanum)
                }
            }
            val fromWhere = arrayOf("A")
            val viewsWhere = intArrayOf(R.id.item_name)

            ADAhere =
                SimpleAdapter(this@MainActivity, data, R.layout.item_list, fromWhere, viewsWhere)
            btLst.adapter = ADAhere
            ADAhere.notifyDataSetChanged()
            btLst.onItemClickListener =
                AdapterView.OnItemClickListener { adaterView, view, position, l ->
                    val string = ADAhere.getItem(position) as HashMap<String, String>
                    val prnName = string["A"]
                    binding.deviceName.setText(prnName)
                    connection.printer_name = prnName.toString()
                    dialog.dismiss()
                }

        } else {
            val value = "No devices found"
            Toast.makeText(this, value, Toast.LENGTH_LONG).show()
            return
        }
        dialog.show()
    }


    fun beginListenForData() {
        try {
            val handler = Handler()
            val delimiter: Byte = 10
            stopWorker = false
            readBufferPosition = 0
            readBuffer = ByteArray(1024)
            workerThread = Thread {
                while (!Thread.currentThread().isInterrupted && !stopWorker) {
                    try {
                        val byteAvailable = inputStream!!.available()
                        if (byteAvailable > 0) {
                            val packetBytes = ByteArray(byteAvailable)
                            inputStream!!.read(packetBytes)
                            for (i in 0 until byteAvailable) {
                                val b = packetBytes[i]
                                if (b == delimiter) {
                                    val encodeBytes = ByteArray(readBufferPosition)
                                    System.arraycopy(
                                        readBuffer,
                                        0,
                                        encodeBytes,
                                        0,
                                        encodeBytes.size
                                    )

                                    val data = String(encodeBytes, Charset.forName("US-ASCII"))
                                    readBufferPosition = 0
                                    handler.post { Log.d("e", data) }
                                } else {
                                    readBuffer[readBufferPosition++] = b
                                }
                            }
                        }
                    } catch (ex: IOException) {
                        stopWorker = true
                    }
                }
            }
            workerThread!!.start()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("MissingPermission")
    fun InitPrinter() {
        var prnName: String = ""
        prnName = connection.printer_name.toString()
        val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

        try {
            if (bluetoothAdapter != null) {
                if (!bluetoothAdapter.isEnabled) {
                    val enableBluetooth = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    btActivityResultLaucher.launch(enableBluetooth)
                }
            }
            val pairedDevices = bluetoothAdapter?.bondedDevices
            if (pairedDevices != null) {
                if (pairedDevices.size > 0) {
                    if (pairedDevices != null) {
                        for (device in pairedDevices) {
                            if (device.name == prnName) {
                                bluetoothDevice = device
                                val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                                val m = bluetoothDevice!!.javaClass.getMethod(
                                    "createRfcommSocket", *arrayOf<Class<*>?>(
                                        Int::class.javaPrimitiveType
                                    )
                                )
                                socket = m.invoke(bluetoothDevice, 1) as BluetoothSocket
                                bluetoothAdapter?.cancelDiscovery()
                                socket!!.connect()
                                outputStream = socket!!.outputStream
                                inputStream = socket!!.inputStream
                                beginListenForData()
                                break
                            }
                        }
                    }
                } else {
                    value = "No devices found"
                    Toast.makeText(this, value, Toast.LENGTH_LONG).show()
                    return
                }
            }
        } catch (ex: java.lang.Exception) {
            Toast.makeText(this, "Bluetooth printer not connected", Toast.LENGTH_LONG).show()
            socket = null
        }
    }


    fun print_inv() {
        try {
            var str: String
            var invhdr: String = "MEGAV"
            var cmpname: String = "PRINTER"

            val textData = StringBuilder()
            val textData1 = StringBuilder()
            val textData2 = StringBuilder()
            val textData3 = StringBuilder()

            if (invhdr.isNotEmpty()) {
                textData.append("""$invhdr""".trimIndent())
            }
            textData.append(""" X """)
            textData.append("""$cmpname""".trimIndent())
            textData.append("\n--------------------------------\n")

            textData1.append("\n================================\n")
            textData1.append(
                """
                    Item Description
                """.trimIndent()
            )
            str = ""
            str = String.format("\n%20s %10s", "Ice cream", "220.000")
            textData1.append(
                """
                    $str
                """.trimIndent()
            )
            str = ""
            str = String.format("\n%20s %10s", "So luong", "2")
            textData1.append(
                """
                    $str
                """.trimIndent()
            )

            textData1.append("\n--------------------------------\n")
            str = ""
            str = String.format("\n%20s %10s", "Thanh tien", "440.000")
            textData1.append(
                """
                    $str
                """.trimIndent()
            )
            str = ""
            str = String.format("\n%20s %10s", "Giam gia", "0")
            textData1.append(
                """
                    $str
                """.trimIndent()
            )

            textData1.append(
                "\n================================\nNote:\nThis bill is printed from" +
                        "\nMegaV - Android app\nWith a Bluetooth printer\n"
            )
            textData2.append("\n1020041505\nLe Van Hung\nNgan hang VCB\n")

            textData3.append("\nWe can also print barcode\n")


            IntentPrint(
                textData.toString(), textData1.toString(),
                textData2.toString(), textData3.toString()
            )

        } catch (ex: java.lang.Exception) {
            value += "$ex\nExcep IntentPrint \n"
            Toast.makeText(this, value, Toast.LENGTH_LONG).show()
        }
    }


    private fun IntentPrint(
        txtValue: String,
        txtValue1: String,
        txtValue2: String,
        txtValue3: String
    ) {
        if (connection.printer_name.trim().isNotEmpty()) {
            val buffer = txtValue1.toByteArray()
            val PrintHeader = byteArrayOf(0xAA.toByte(), 0x55, 2, 0)
            PrintHeader[3] = buffer.size.toByte()
            InitPrinter()
            if (PrintHeader.size > 128) {
                value += "\nValue is more than 128 size \n"
                Toast.makeText(this, value, Toast.LENGTH_LONG).show()
            } else {
                try {
                    if (socket != null) {
                        try {
                            var SP = byteArrayOf(0x1B, 0x40)
                            outputStream!!.write(SP)
                            Thread.sleep(1000)
                        } catch (e: InterruptedException) {
                            e.printStackTrace()
                        }
                        val FONT_1X = byteArrayOf(0x1B, 0x21, 0x00)
                        val FONT_2X = byteArrayOf(0x1B, 0x21, 0x30)
                        val ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 1)
                        val ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0)
                        val BOLD = byteArrayOf(0x1B, 0x45, 0x01)
                        val NORMAL = byteArrayOf(0x1B, 0x45, 0x00)

                        outputStream!!.write(BOLD)
                        outputStream!!.write(FONT_2X)
                        outputStream!!.write(ALIGN_CENTER)
                        outputStream!!.write(txtValue.toByteArray())

                        outputStream!!.write(NORMAL)
                        outputStream!!.write(FONT_1X)
                        outputStream!!.write(ALIGN_LEFT)
                        outputStream!!.write(txtValue1.toByteArray())
//qr code image
                        val bmp: Bitmap =
                            BitmapFactory.decodeResource(resources, R.drawable.ol_bmp)
                        val resizedBitmap = createBitmapWithSize(bmp, 300, 350)
                        printQrCode(resizedBitmap)
//ten chu tai khoan
                        outputStream!!.write(FONT_1X)
                        outputStream!!.write(txtValue2.toByteArray())
                        outputStream!!.write(ALIGN_CENTER)

                        outputStream!!.write(FONT_1X)
                        outputStream!!.write(ALIGN_CENTER)
                        outputStream!!.write(txtValue3.toByteArray())
//  bar code ean13
                        generateBarcode()
                        val bitmap = (binding.imgQr.drawable as BitmapDrawable).bitmap
                        val resizedBitmap2 = createBitmapWithSize(bitmap, 300, 100)
                        printQrCode(resizedBitmap2)


                        val FEED_PAPER_AND_CUT = byteArrayOf(0x1D, 0x56, 66, 0x00)
                        outputStream!!.write(FEED_PAPER_AND_CUT)
                        outputStream!!.flush()
                        outputStream!!.close()
                        socket!!.close()
                    }
                } catch (ex: java.lang.Exception) {
                    Toast.makeText(this, "intent print " + ex.message.toString(), Toast.LENGTH_LONG)
                        .show()
                    Log.d("intent print", "IntentPrint: " + ex.message.toString())
                }

            }
        }
    }

    private fun generateBarcode() {
        val multiFormatWriter = MultiFormatWriter()
        try {
            val hints =
                mapOf(EncodeHintType.MARGIN to 0) // Removing white margin around the barcode
            val bitMatrix: BitMatrix =
                multiFormatWriter.encode(barcodeText, BarcodeFormat.EAN_13, 300, 100, hints)
            val barcodeEncoder = BarcodeEncoder()
            val bitmap: Bitmap = barcodeEncoder.createBitmap(bitMatrix)
            binding.imgQr.setImageBitmap(bitmap)
        } catch (e: WriterException) {
            e.printStackTrace()
            Toast.makeText(this, "Error generating barcode: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun printQrCode(qRBit: Bitmap?) {
        try {
            val printPic1 = PrintPic.getInstance()
            printPic1.init(qRBit)
            val bitmapdata2 = printPic1.printDraw()
            val ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 1)
            outputStream!!.write(ALIGN_CENTER)
            outputStream!!.write(bitmapdata2)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun printQRCode(textToQR: String): Bitmap? {
        if (textToQR.length > 4296) {
            // Chuỗi quá dài, xử lý theo cách thích hợp
            Log.d("check_length_data", "Chuỗi quá dài để tạo mã QR code")
        }

        val multiFormatWriter = MultiFormatWriter()
        try {
            val bitMatrix = multiFormatWriter.encode(textToQR, BarcodeFormat.QR_CODE, 300, 100)
            val barcodeEncoder = BarcodeEncoder()
            val bitmap = barcodeEncoder.createBitmap(bitMatrix)
            return bitmap
        } catch (e: WriterException) {
            e.printStackTrace()
            return null
        }
    }

    private fun createBitmapWithSize(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        val newBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(newBitmap)
        val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
        val dstRect = Rect(0, 0, width, height)
        canvas.drawBitmap(bitmap, srcRect, dstRect, null)
        return newBitmap
    }

}