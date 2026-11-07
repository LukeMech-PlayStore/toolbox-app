package org.lukemech.lmtoolbox.features.media.usb_writer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/** A USB drive's mass storage interface (SCSI over bulk transfers), as it's plugged in. */
class UsbDrive(val device: UsbDevice, val usbInterface: UsbInterface, val inEndpoint: UsbEndpoint, val outEndpoint: UsbEndpoint) {
    /** Its maker's and product's names, as it tells them (its path when it doesn't). */
    val name: String
        get() = listOfNotNull(device.manufacturerName?.trim(), device.productName?.trim())
            .filter { it.isNotEmpty() }.distinct().joinToString(" ").ifEmpty { device.deviceName }

    /** Which one it is while it stays plugged in. */
    val key get() = "${device.deviceName}#${usbInterface.id}"
}

/**
 * USB drives plugged in through OTG, written to directly (as EtchDroid does): the app claims the drive's interface
 * (Android lets go of it, unmounting it) and speaks SCSI to it itself ([UsbMassStorage]), so no root is needed and any file
 * system, or none, can be written. Android asks the user to allow each drive ([requestPermission]).
 */
object UsbDrives {
    /** Mass storage: the SCSI transparent command set, bulk-only transport. */
    private const val SUBCLASS_SCSI = 6
    private const val PROTOCOL_BULK_ONLY = 80

    /** Whether the device can be a USB host at all. */
    fun supported(context: Context) = context.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)

    private fun manager(context: Context) = context.getSystemService(Context.USB_SERVICE) as UsbManager

    /** The drives plugged in now. */
    fun list(context: Context): List<UsbDrive> =
        manager(context).deviceList.values.flatMap(::drivesOf).sortedBy { it.name }

    private fun drivesOf(device: UsbDevice): List<UsbDrive> = (0 until device.interfaceCount).map(device::getInterface)
        .filter {
            it.interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE &&
                it.interfaceSubclass == SUBCLASS_SCSI && it.interfaceProtocol == PROTOCOL_BULK_ONLY
        }
        .mapNotNull { usbInterface ->
            val endpoints = (0 until usbInterface.endpointCount).map(usbInterface::getEndpoint)
                .filter { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK }
            val inEp = endpoints.firstOrNull { it.direction == UsbConstants.USB_DIR_IN }
            val outEp = endpoints.firstOrNull { it.direction == UsbConstants.USB_DIR_OUT }
            if (inEp != null && outEp != null) UsbDrive(device, usbInterface, inEp, outEp) else null
        }

    /** Each time a USB device is plugged in or out. */
    fun changes(context: Context): Flow<Unit> = callbackFlow {
        // The app's: an activity's receivers are let go of as it's destroyed, before this flow is
        val app = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(Unit)
            }
        }
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        // The system's broadcasts: protected ones, which a receiver not exported still gets
        ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        awaitClose { runCatching { app.unregisterReceiver(receiver) } }
    }

    /** Whether the user lets the app use [drive]: asked (Android's dialog) unless it's allowed already. */
    suspend fun requestPermission(activityContext: Context, drive: UsbDrive): Boolean {
        val context = activityContext.applicationContext
        val manager = manager(context)
        if (manager.hasPermission(drive.device)) return true
        val action = "${context.packageName}.USB_PERMISSION"
        return suspendCancellableCoroutine { cont ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    if (intent.action != action) return
                    runCatching { context.unregisterReceiver(this) }
                    if (cont.isActive) cont.resume(manager.hasPermission(drive.device))
                }
            }
            ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
            cont.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
            // Mutable: the system adds the device and the answer to it; explicit (this package), as Android 14 wants
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val intent = PendingIntent.getBroadcast(context, 0, Intent(action).setPackage(context.packageName), flags)
            manager.requestPermission(drive.device, intent)
        }
    }

    /** [drive] opened for writing: its first logical unit with a medium in it. Close it to give the drive back. */
    fun open(context: Context, drive: UsbDrive): UsbMassStorage {
        val manager = manager(context)
        if (!manager.hasPermission(drive.device)) throw IOException("Not allowed to use ${drive.name}")
        val connection = manager.openDevice(drive.device) ?: throw IOException("Couldn't open ${drive.name}")
        return UsbMassStorage.open(connection, drive.usbInterface, drive.inEndpoint, drive.outEndpoint)
    }
}
