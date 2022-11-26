package com.arstagaev.flowble

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import com.arstagaev.flowble.BLEStarter.Companion._scanDevices
import com.arstagaev.flowble.BleParameters.BLE_BATTERY_LEVEL_CHARACTERISTIC
import com.arstagaev.flowble.BleParameters.BLE_BATTERY_VALUE
import com.arstagaev.flowble.BleParameters.CONNECTED_DEVICE
import com.arstagaev.flowble.BleParameters.SCAN_FILTERS
import com.arstagaev.flowble.BleParameters.STATE_BLE
import com.arstagaev.flowble.BleParameters.TARGET_CHARACTERISTIC_NOTIFY
import com.arstagaev.flowble.constants.AllGattDescriptors.ClientCharacteristicConfiguration
import com.arstagaev.flowble.extensions.*
import com.arstagaev.flowble.gentelman_kit.*
import com.arstagaev.flowble.models.StateBle
import com.arstagaev.flowble.models.ScannedDevice
import kotlinx.coroutines.*
import java.util.*

class BleActions(
    ctx: Context? = null,
) : BleManager(ctx) {

    private val TAG = this::class.qualifiedName
    private var scanning = false

    var activity: Activity? = null
    var REVERT_WORK_CAUSE_PERMISSION = false
    var scanResultsNewFoundedINTERNAL = arrayListOf<ScannedDevice>()
    var multiConnect = false

    init {
        internalContext = ctx
        checkPermissions()

    }

    private fun checkPermissions() {
        //check permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (internalContext?.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)?: false
                && internalContext?.hasPermission(Manifest.permission.BLUETOOTH_SCAN)?: false
                && internalContext?.hasPermission(Manifest.permission.BLUETOOTH_CONNECT)?: false) {

                REVERT_WORK_CAUSE_PERMISSION = false

            }else {

                Log.e(TAG,"########################################")
                Log.e(TAG,"# Error: Don`t have permission for BLE #")
                Log.e(TAG,"# Error: Don`t have permission for BLE #")
                Log.e(TAG,"# Error: Don`t have permission for BLE #")
                Log.e(TAG,"# ACCESS_FINE_LOCATION:${internalContext?.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)?: false} #")
                Log.e(TAG,"# BLUETOOTH_SCAN:${internalContext?.hasPermission(Manifest.permission.BLUETOOTH_SCAN)?: false} #")
                Log.e(TAG,"# BLUETOOTH_CONNECT:${internalContext?.hasPermission(Manifest.permission.BLUETOOTH_CONNECT)?: false} #")
                Log.e(TAG,"########################################")

                REVERT_WORK_CAUSE_PERMISSION = true
            }
        }else {
            if (internalContext?.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)?: false) {

                REVERT_WORK_CAUSE_PERMISSION = false

            }else {

                Log.e(TAG,"########################################")
                Log.e(TAG,"# Error: Don`t have permission for BLE #")
                Log.e(TAG,"# Error: Don`t have permission for BLE #")
                Log.e(TAG,"# Error: Don`t have permission for BLE #")
                Log.e(TAG,"# ACCESS_FINE_LOCATION:${internalContext?.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)?: false} #")
                Log.e(TAG,"########################################")

                REVERT_WORK_CAUSE_PERMISSION = true
            }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connectTo(address: String, withBondingRequest: Boolean) : Boolean  {


        var a = CoroutineScope(jobBleActionsLifecycle3).async {
            if (address == null || address.isEmpty()){

                logError("address is null or Empty<")
                return@async false
            }

            bluetoothGatt?.connectedDevices?.forEachIndexed { index, bluetoothDevice ->

                if (bluetoothDevice?.address == address) {
                    return@async true
                }

            }

            if (!multiConnect) {
                // check if not connected and have multi connect
                if (bluetoothGatt?.connectedDevices?.isNotEmpty() == true) {
                    disconnectFromDevice()
                }
            }


            //TODO: check if we don`t use demo
            if (address == "44:44:44:44:44:0C") {

                repeat(10) {
                    logError("!! ChillOutBLE: YOU USING DEMO MAC-ADDRESS, change to real one !!")
                }

            }
            logAction("connect to ${address} <<<<<<<<<<<<<<<<<<<<")




            btAdapter.let { adapter ->
                try {
                    val device = adapter.getRemoteDevice(address)
                    // connect to the GATT server on the device
                    if (ActivityCompat.checkSelfPermission(
                            internalContext!!,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        logWarning("ble permission BLUETOOTH_CONNECT is not granted")
                        return@async false
                    }

                    if (device == null) {
                        logWarning("ble device is null!!!")
                        return@async false
                    }


                    bluetoothGatt = device.connectGatt(internalContext, false, bluetoothGattCallback)
                    delay(100)

                    if (withBondingRequest) {

                        var weAlreadyBonded = false

                        alreadyBondedDevices.forEach {

                            if(it.address == device.address) {
                                weAlreadyBonded = true
                            }

                        }

                        if (!weAlreadyBonded) {
                            device.createBond()
                        }

                    }
                    //delay(100)
                    // check we connected or not
                    var a = bluetoothManager?.getConnectedDevices(BluetoothProfile.GATT)

                    logWarning(" ${a?.joinToString() ?: "null"}  isNull:${bluetoothGatt?.services?.size ?: null}    disk${bluetoothGatt?.discoverServices()}")

                    // if device don't found
                    val connectedDevice = a?.find { it.address == address } ?: return@async false


                    return@async true
                } catch (exception: IllegalArgumentException) {
                    Log.w(TAG, "Device not found with provided address.  Unable to connect.")
                    return@async false
                }
            } ?: run {
                Log.w(TAG, "BluetoothAdapter not initialized")
                return@async false
            }
        }.await()
        return a
    }

    @SuppressLint("MissingPermission")
    fun enableNotifications(uuid: UUID): Boolean {

        logAction("$TAG enableNotifications ")

        bluetoothGatt?.findCharacteristic(uuid)?.let { characteristic ->

            val cccdUuid = getUUID(ClientCharacteristicConfiguration)

            val payload = when {
                characteristic.isIndicatable() ->
                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                characteristic.isNotifiable() ->
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                else -> {
                    error("${characteristic.uuid} doesn't support notifications/indications")
                    return false
                }
            }

            characteristic.getDescriptor(cccdUuid)?.let { cccDescriptor ->
                logInfo("Notification is already ${cccDescriptor.isEnabled()} ")

                if (cccDescriptor.isEnabled()) {
                    logInfo("Notification is already ENABLED ")
                    return true
                }

                if (!bluetoothGatt?.setCharacteristicNotification(characteristic, true)!!) {
                    logError("$TAG setCharacteristicNotification failed for ${characteristic.uuid}")
                    return false
                }


                cccDescriptor.value = payload
                bluetoothGatt?.writeDescriptor(cccDescriptor)
                TARGET_CHARACTERISTIC_NOTIFY = uuid
                logAction("Success Enable Notification !! ")

                return true
            } ?: internalContext.run {
                Log.e(TAG,"${characteristic.uuid} doesn't contain the CCC descriptor!")
            }
        } ?: internalContext.run {
            Log.e(TAG,"Cannot find $uuid! Failed to enable notifications.")
        }
        return false
    }

    @SuppressLint("MissingPermission")
    fun disableNotifications(uuid: UUID): Boolean {
        if (uuid == null) return false
        val characteristicTarget = bluetoothGatt?.findCharacteristic(uuid = uuid)//getCharacteristic(uuid) ?: return false

        if (characteristicTarget == null) {
            logError("characteristic == null !!!")
            return false
        }

        val cccdUuid = getUUID(ClientCharacteristicConfiguration)

        characteristicTarget.getDescriptor(cccdUuid)?.let { cccDescriptor ->

            if (!cccDescriptor.isEnabled()) {
                logInfo("Notification is already DISABLED ")
                return true
            }


            if (!bluetoothGatt!!.setCharacteristicNotification(characteristicTarget, false)) {
                logError("setCharacteristicNotification failed for ${characteristicTarget.uuid}")

                return false
            }

            cccDescriptor.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            logAction("Success Disable Notification !! ")

            return bluetoothGatt!!.writeDescriptor(cccDescriptor)
        } ?: internalContext.run {
            logError("${characteristicTarget.uuid} doesn't contain the CCC descriptor!")
            return false
        }
    }

    @SuppressLint("MissingPermission")
    fun writeDescriptor(descriptor: BluetoothGattDescriptor, payload: ByteArray) {
        logInfo(TAG+"writeDescriptor starts  >> ${(bluetoothManager?.getConnectedDevices(
            BluetoothProfile.GATT)?.size ?: 0)}")

        if ((bluetoothManager?.getConnectedDevices(BluetoothProfile.GATT)?.size ?: 0) > 0) {
            Log.i(TAG,"writeDescriptor already<<")
            bluetoothGatt?.let { gatt ->
                descriptor.value = payload
                gatt.writeDescriptor(descriptor)
            } ?: error("Not connected to a BLE device!")

        } else {
            logError(TAG + "// // Cant enable|disable notification !! ")
            logError(TAG + "// // Cant enable|disable notification !! ")
            logError(TAG + "// // Cant enable|disable notification !! ")
            STATE_BLE = StateBle.NO_CONNECTED
        }

    }

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    @SuppressLint("MissingPermission")
    fun startScan(scanFilters: MutableList<ScanFilter>?): Boolean {
        if (scanning)
            return true

        logWarning("$TAG SCAN_FILTERS: ${SCAN_FILTERS.joinToString()}   Filters isEmpty: ${SCAN_FILTERS.isEmpty()}")

        if (btAdapter == null) {
            logError("Bluetooth Adapter is NULL!!!")
            return false
        }

        logWarning(TAG+" bluetoothLeScanner>>> ${bluetoothLeScanner.toString()}")

        if (scanFilters?.isNotEmpty() == true) {
            //SCAN_FILTERS.add(scanFilter)

            bluetoothLeScanner?.startScan(
                scanFilters,
                scanSettings,
                leScanCallback
            )
        }else {
            bluetoothLeScanner?.startScan(
                leScanCallback
            )
        }


        scanning = true

        return scanning
    }



    @SuppressLint("MissingPermission")
    suspend fun stopScan(): Boolean {
        if (!scanning)
            return true
        Log.i(TAG,"Stop scan")
        bluetoothLeScanner?.stopScan(leScanCallback)
        scanning = false

        return true
    }


    @SuppressLint("MissingPermission")
    fun readCharacteristic(
        characteristicUuid: UUID,
    ) : Boolean {
        if (characteristicUuid == null) return false
//        val characteristicTarget = bluetoothGatt?.findCharacteristic(uuid = characteristicUuid)//getCharacteristic(uuid) ?: return false
//        logWarning("res value: ${characteristicTarget?.value}")

        bluetoothGatt?.findCharacteristic(characteristicUuid)?.let { characteristic ->

            return bluetoothGatt?.readCharacteristic(characteristic) ?: false

        } ?: run {
            logError("Cannot find $characteristicUuid to read from")
            return false
        }
    }

    @SuppressLint("MissingPermission")
    fun writeCharacteristic(
        uuid: UUID,
        payload: ByteArray
    ): Boolean {
        if (uuid == null) return false

        val characteristicTarget = bluetoothGatt?.findCharacteristic(uuid = uuid)//getCharacteristic(uuid) ?: return false

        if (CONNECTED_DEVICE == null && characteristicTarget == null) { return false }

        val writeType = when {
            characteristicTarget!!.isWritable() -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristicTarget.isWritableWithoutResponse() -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            else -> {
                Log.e("ccc","Characteristic ${characteristicTarget?.uuid} cannot be written to")
                return false
            }
        }

        characteristicTarget.let { characteristic ->
            characteristic.writeType = writeType
            characteristic.value = payload
            bluetoothGatt?.writeCharacteristic(characteristic)
        } ?: run {
            Log.e("","Cannot find  to write to")
            return false
        }
        return true
    }

    fun getBatteryLevel() : Boolean {
        val batteryLevelCharacteristic = bluetoothGatt?.findCharacteristic(UUID.fromString(BLE_BATTERY_LEVEL_CHARACTERISTIC))
        if (batteryLevelCharacteristic?.isReadable() == true) {

            try {
                try {
                    BLE_BATTERY_VALUE = Integer.parseInt(bytesToHex(batteryLevelCharacteristic.value), 16).toString()
                }catch (e: Exception) {
                    Log.e("ERROR","NULL BLE_BATTERY")
                }
                logInfo("Ble Battery Characteristic: ${bytesToHex(batteryLevelCharacteristic.value)} ~ ${batteryLevelCharacteristic.value.toHexString()}")
                return true

            }catch (e:Exception) {
                logError("Ble Battery Characteristic: ${e.message} !!")
            }
        }
        return false
    }

    fun unBondDeviceFromPhone(address: String) : Boolean {
        if (bluetoothManager?.weHaveDevice(address) == true) {
            try {

                bluetoothGatt?.device?.removeBond()
                return true
            } catch (e: Exception) {
                logError("Error in unbonding: ${e.message} ")
            }
        }
        return false
    }


    //////////////////////////////////////////////////////////
    // Callbacks                                            //
    //////////////////////////////////////////////////////////
    // Device scan callback.
    private var leScanCallback: ScanCallback = object : ScanCallback() {

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            println("scan~ error >> ${errorCode}")
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            super.onBatchScanResults(results)
        }
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            //var curTime =0L// System.currentTimeMillis() / 1000L

            val indexQuery = scanResultsNewFoundedINTERNAL.indexOfFirst {
                it.scanResult?.device?.address  == result.device.address
            }

            /** A scan result already exists with the same address */
            if (indexQuery != -1) {
                // for closest connect
                // refreshing is about 20-60 sec rssi

                //println("update ${result.device?.name}")
                CoroutineScope(jobBleActionsLifecycle2).launch {

                    _scanDevices.emit(
                        ScannedDevice(
                            result,
                            isActiveOrNearby = true,
                            timeActive = (System.currentTimeMillis()/1000L)
                        )
                    )
                }
                //println(" >>>>>> ${result.device.address}")
            } else { /** founded new device */
                scanResultsNewFoundedINTERNAL.add(
                    ScannedDevice(
                        scanResult = result,
                        isActiveOrNearby = true,
                        timeActive = System.currentTimeMillis()/1000L
                    )
                )

                CoroutineScope(jobBleActionsLifecycle).launch {
                    _scanDevices.emit(
                        ScannedDevice(result,isActiveOrNearby = true,
                        timeActive = (System.currentTimeMillis()/1000L))
                    )
                }

            }
        }
    }
    fun requestMtu(device: BluetoothDevice, mtu: Int) {
        TODO("Not Yet imlp MTU")
        //if (device.isConnected()) {
        //    enqueueOperation(MtuRequest(device, mtu.coerceIn(GATT_MIN_MTU_SIZE, GATT_MAX_MTU_SIZE)))
        //} else {
        //    Timber.e("Not connected to ${device.address}, cannot request MTU update!")
        //}
    }


    @SuppressLint("MissingPermission")
    fun disconnectFromDevice(): Boolean {
        logWarning("disconnect From Device !!!")

        if (bluetoothGatt != null){
            bluetoothGatt?.disconnect()

            Log.w(TAG," disconnected FromDevice !!! ")
            return true
        } else {
            Log.w(TAG," bluetoothGatt is NULL ")
        }

        return true
    }

    @SuppressLint("MissingPermission")
    suspend fun disableBLEManager(): Boolean {
        stopScan()
        delay(10)
        disconnectFromDevice()
        delay(100)
        bluetoothGatt?.close()
        delay(100)

        bluetoothGatt = null

        jobBleActionsLifecycle?.cancel()
        jobBleActionsLifecycle2?.cancel()
        jobBleActionsLifecycleListener?.cancel()

        logInfo(">>>${bluetoothGatt}  $")
        Log.w(TAG," disableBLEManager !!! ")
        Log.w(TAG," disableBLEManager !!! ")
        Log.w(TAG," disableBLEManager !!! ")
        return true
    }

    companion object {
        private const val REQUEST_ENABLE_BT = 1
    }

}
