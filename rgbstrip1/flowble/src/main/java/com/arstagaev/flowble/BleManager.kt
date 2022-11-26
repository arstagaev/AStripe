package com.arstagaev.flowble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.getSystemService
import com.arstagaev.flowble.BLEStarter.Companion.outputBytesNotifyIndicate
import com.arstagaev.flowble.BLEStarter.Companion.outputBytesRead
import com.arstagaev.flowble.BleParameters.ACTION_GATT_CONNECTED
import com.arstagaev.flowble.BleParameters.ACTION_GATT_DISCONNECTED
import com.arstagaev.flowble.extensions.printGattTable
import com.arstagaev.flowble.gentelman_kit.logInfo
import com.arstagaev.flowble.gentelman_kit.logWarning
import com.arstagaev.flowble.models.CharacterCarrier
import com.arstagaev.flowble.models.StateBle
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

open class BleManager(
    ctx : Context? = null
)  {
    var jobBleActionsLifecycleListener = Job()
    var jobBleActionsLifecycle = Job()
    var jobBleActionsLifecycle2 = Job()
    var jobBleActionsLifecycle3 = Job()

    private val TAG = BleManager::class.qualifiedName
    internal var internalContext : Context? = null

    init {
        internalContext = ctx
    }

    val bluetoothManager = internalContext?.getSystemService<BluetoothManager>()
        //?: throw IllegalStateException("BluetoothManager not found")


    // From the previous section:
    val btAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = internalContext?.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    @SuppressLint("MissingPermission")
    var alreadyBondedDevices = btAdapter.bondedDevices
    var bluetoothLeScanner =   btAdapter.bluetoothLeScanner



    var receivingRawData : ByteArray?     = null
    var bluetoothGatt    : BluetoothGatt? = null


    /**
     *  CALLBACKS
     */
    var bluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onPhyRead(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int, status: Int) {
            super.onPhyRead(gatt, txPhy, rxPhy, status)
        }
        override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
            super.onReadRemoteRssi(gatt, rssi, status)

        }
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            BleParameters.BLE_STATUS = status
            Log.w("ble","gatt status:${BleParameters.BLE_STATUS} \n${gatt?.printGattTable()} <<|")
            val deviceAddress = gatt?.device?.address

            if (status == BluetoothGatt.GATT_SUCCESS) {
                when(newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        logWarning("onConnectionStateChange: connected to $deviceAddress")
                        //deviceGattMap[gatt.device] = gatt
                        Handler(Looper.getMainLooper()).post {
                            gatt?.discoverServices()
                        }
                    }
                    BluetoothProfile.STATE_CONNECTING -> {
                        logWarning("NOWWWW STATE_CONNECTING STATE_CONNECTING")
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        logWarning("onConnectionStateChange: disconnected from $deviceAddress")
                        BleParameters.STATE_BLE = StateBle.NO_CONNECTED
                        BleParameters.CONNECTED_DEVICE = null
                    }

                }
                if (newState == BluetoothProfile.STATE_CONNECTED) {

                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {

                    //teardownConnection(gatt.device)
                }
            } else {
                logWarning("onConnectionStateChange: status $status encountered for $deviceAddress!")
//                if (pendingOperation is Connect) {
//                    signalEndOfOperation()
//                }
                //teardownConnection(gatt.device)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            with(gatt) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    logWarning("Discovered ${this?.services?.size} services for ${this?.device?.address}.")
                    this?.printGattTable()
                    //this?.requestMtu(device, GATT_MAX_MTU_SIZE)
                    //listeners.forEach { it.get()?.onConnectionSetupComplete?.invoke(this) }
                } else {
                    logWarning("Service discovery failed due to status $status")
                    //teardownConnection(gatt.device)
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            CoroutineScope(CoroutineName("onCharacteristicChanged")+jobBleActionsLifecycleListener).launch {

                outputBytesNotifyIndicate.emit(
                    CharacterCarrier(
                        macAddress = gatt?.device?.address ?: "",
                        uuidCharacteristic = characteristic?.uuid,
                        value = characteristic?.value
                    )
                )

                println("charact: ${characteristic.uuid}")
            }

            BleParameters.STATE_BLE = StateBle.NOTIFYING_OR_INDICATING
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)

            CoroutineScope(CoroutineName("onCharacteristicRead")+jobBleActionsLifecycleListener).launch {

                outputBytesRead.emit(
                    CharacterCarrier(
                        macAddress = gatt?.device?.address ?: "",
                        uuidCharacteristic = characteristic?.uuid,
                        value = characteristic?.value,
                        codeStatus = status
                    )
                )

            }

        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)

        }
    }

    /**
     * UTILITY INTERNAL METHODS
     */
    fun getSupportedGattServices(): List<BluetoothGattService?>? {

        return bluetoothGatt?.services
    }


    private fun makeGattUpdateIntentFilter(): IntentFilter? {
        return IntentFilter().apply {
            addAction(ACTION_GATT_CONNECTED)
            addAction(ACTION_GATT_DISCONNECTED)

         }
    }
}