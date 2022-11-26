package com.arstagaev.flowble.models

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult

data class ScannedDevice(
    //var bt: BluetoothDevice?,
    var scanResult: ScanResult?,
    var isActiveOrNearby: Boolean? = false,
    var timeActive: Long? = 0
)
