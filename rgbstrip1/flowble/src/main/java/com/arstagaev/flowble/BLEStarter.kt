package com.arstagaev.flowble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.os.Build
import android.widget.Toast
import com.arstagaev.flowble.BleParameters.CONNECTED_DEVICE
import com.arstagaev.flowble.BleParameters.STATE_BLE
import com.arstagaev.flowble.enums.*
import com.arstagaev.flowble.extensions.hasPermission
import com.arstagaev.flowble.gentelman_kit.logAction
import com.arstagaev.flowble.gentelman_kit.logError
import com.arstagaev.flowble.gentelman_kit.logWarning
import com.arstagaev.flowble.gentelman_kit.toast
import com.arstagaev.flowble.models.CharacterCarrier
import com.arstagaev.flowble.models.ScannedDevice
import com.arstagaev.flowble.utils.ActionLog
import com.arstagaev.flowble.utils.separator
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

private var countInitClass = 0

fun initer(context: Context,showOperationLog: Boolean = false): Boolean {

    if (countInitClass >1) {
        CoroutineScope(Dispatchers.Main).launch {

            context.toast("WARNING: Overflow Instance !!!",Toast.LENGTH_LONG)

        }
        println("initer(): false")
        return false
    }

    BLEStarter(context).also {
        it.showOperationToasts = showOperationLog // show logs in Toast
    }.apply {
        CoroutineScope(Dispatchers.Main).launch {
            if (countInitClass >1) {
                context.toast("WARNING: Overflow Instance",Toast.LENGTH_LONG)
            }
        }
    }
    println("initer(): true")
    return true
}

class BLEStarter(ctx : Context) {

    private val TAG = "BLEStarter"
    private var bleActions: BleActions? = null
    private var lastSuccess = false
    private var internalContext: Context? = ctx
    private var jobBleLifecycle = Job()

    var btAdapter: BluetoothAdapter? = null

    //setups:
    var showOperationToasts = false
    var multiConnect = false

    init {
        checkPermissions()
        bookingMachine()
        bleActions = BleActions(internalContext).also {
            it.multiConnect = multiConnect
        }


        countInitClass++
        checkNumberOfInstanceThisClass()
    }

    private fun bookingMachine() {
        logAction("START!!")
        CoroutineScope(jobBleLifecycle + CoroutineName("Ble Starter: bookingMachine()")).async {
            delay(2000)
            bleCommandTrain.collectIndexed { index, operation ->

                async {

                    operation.forEachIndexed { index, bleOperations ->

                        async {
                            logAction("Operation ${bleOperations.name} has been started")
                            // First try to run operation:
                            lastSuccess = selector(bleOperations) ?: let {
                                logWarning("Operation ${bleOperations.name.toString()} is Failed !!!")
                                false
                            }
                            //BIG_SHARED_STR.value += "\n${bleOperations.toString()} ${if(lastSuccess) {
                            //    ActionLog.DONE.sign} else ActionLog.FAIL.sign}| st:${STATE_BLE.name}" + separator()

                            ////////////////////////////////////////////////////////////////////////
                            // routerTerminalDefiner = OperationResult("First try:${bleOperations?.toString()} result:${lastSuccess}",lastSuccess)
                            // Another try to run operation:
                            if (!lastSuccess && bleOperations.isImportant) {
                                // force waiting finish of operation
                                while (!lastSuccess) {

                                    if (isBluetoothEnabled()) {
                                        logAction("repeat: ${bleOperations}")
                                        lastSuccess = selector(bleOperations) ?: let {
                                            logWarning("Operation ${bleOperations.toString()} is Failed Again !!!!")
                                            false
                                        }
                                        //BIG_SHARED_STR.value += "\n${bleOperations.toString()} ${if(lastSuccess) {
                                        //    ActionLog.REPEAT.sign + ActionLog.DONE.sign} else ActionLog.REPEAT.sign + ActionLog.FAIL.sign}| [${CONNECTED_DEVICE?.address}]| st:${STATE_BLE.name}" + separator()
                                    }
                                    //logAction("well ${BIG_SHARED_STR.value}")
                                    delay(3000)
                                }
                            }
                        }.await()
                        logAction("End of Operation: ${operation.toString()} <<<<")

                    }


                }.await()
            }
        }
    }

    private fun isBluetoothEnabled(): Boolean {
        return if ( bleActions?.btAdapter?.isEnabled == true ) {
            true
        }else {
            logError("Bluetooth is NOT enabled !!")
            logError("Bluetooth is NOT enabled !!")
            logError("Bluetooth is NOT enabled !!")
            logError("Bluetooth is NOT enabled !!")


            false
        }
    }



    private suspend fun selector(operation: BleOperation) : Boolean? {
        logAction("New Operation: ${operation} >>>>")

        if (!isBluetoothEnabled()) {
            delay(200)
            return false
        }

        when(operation) {
            is StartScan -> with(operation) {
                return bleActions?.startScan(scanFilters)
            }
            is StopScan -> with(operation) {
                return bleActions?.stopScan()
            }

            is Connect -> with(operation) {
                logAction("StartConnect ${operation.address}")
                val result = bleActions?.connectTo(address ?: "",withBondingRequest = true)
                println("CALLBACK SUCC ${result}")

                //isSuccess(result ?: false)

//                if (showOperationToasts) {
//                    CoroutineScope(Dispatchers.Main).launch {
//                        internalContext?.toast("Connect: ${operation.address}, ${result}")
//                    }
//                }
                return result
            }
            is Disconnect -> with(operation) {
                return bleActions?.disconnectFromDevice()
            }
            is DiscoveryServices -> with(operation) {
                //TODO not implemented
            }

            is WriteToCharacteristic -> with(operation) {
                val result = bleActions?.writeCharacteristic(uuid = characteristicUuid, payload = payload)
                //isSuccess(result ?: false)
                return result
            }
            is ReadFromCharacteristic -> with(operation) {
                return bleActions?.readCharacteristic(characteristicUuid = characteristicUuid)
            }

            is EnableNotifications -> with(operation) {
                val result = bleActions?.enableNotifications(uuid = characteristicUuid)
                isSuccess(result ?: false)
                return result
            }
            is DisableNotifications -> with(operation) {
                return bleActions?.disableNotifications(uuid = characteristicUuid)
            }
            // Experimental:
            is GetBatteryLevel -> with(operation) {
                return bleActions?.getBatteryLevel()
            }
            // Experimental:
            is UnBondDeviceFromPhone -> with(operation) {
                return bleActions?.unBondDeviceFromPhone(address)
            }


            is Retard -> with(operation) {
                delay(duration ?: 0)
                return true
            }

            is DisableBleManager -> with(operation) {
                return bleActions?.disableBLEManager()
            }
        }
        return false
    }

    fun checkPermissions(): Boolean {
        //check permissions

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (internalContext?.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)?: false
                && internalContext?.hasPermission(Manifest.permission.BLUETOOTH_SCAN)?: false
                && internalContext?.hasPermission(Manifest.permission.BLUETOOTH_CONNECT)?: false) {
                return true
            }else {

                logError("$TAG ########################################")
                logError("$TAG # Error: Don`t have permission for BLE #")
                logError("$TAG # Error: Don`t have permission for BLE #")
                logError("$TAG # Error: Don`t have permission for BLE #")
                logError(TAG+" # ACCESS_FINE_LOCATION:${internalContext?.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)?: false} #")
                logError(TAG+" # BLUETOOTH_SCAN:${internalContext?.hasPermission(Manifest.permission.BLUETOOTH_SCAN)?: false} #")
                logError(TAG+" # BLUETOOTH_CONNECT:${internalContext?.hasPermission(Manifest.permission.BLUETOOTH_CONNECT)?: false} #")
                logError("$TAG ########################################")
                return false
            }
        }else {
            if (internalContext?.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)?: false) {

                return true
            }else {
                logError("$TAG ########################################")
                logError("$TAG # Error: Don`t have permission for BLE #")
                logError("$TAG # Error: Don`t have permission for BLE #")
                logError("$TAG # Error: Don`t have permission for BLE #")
                logError(TAG+ " # ACCESS_FINE_LOCATION:${internalContext?.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)?: false} #")
                logError("$TAG ########################################")
                return false
            }
        }
    }

    suspend fun forceStop() {
        jobBleLifecycle.cancel()
        bleActions?.jobBleActionsLifecycle?.cancel()

        bleActions?.disableBLEManager()
    }

    /**
     * Must work only one instance of BLEStarter - for stability of work
     */
    private fun checkNumberOfInstanceThisClass() {
        if (countInitClass > 1) {
            CoroutineScope(jobBleLifecycle).launch {
                // set delay for to attract developer`s attention
                delay(2000)
                repeat(10) {
                    logError("WARNING ! Many times ($countInitClass) initializing of BLEStarter class !! [Possible wrong work of BLE module]. Especially double/triple and etc., request to connect and so on")
                }
            }
        }
    }


    companion object {
        var bleCommandTrain = MutableSharedFlow<MutableList<BleOperation>>(10,10, BufferOverflow.SUSPEND)

        var _scanDevices = MutableSharedFlow<ScannedDevice>(extraBufferCapacity =  100,onBufferOverflow = BufferOverflow.DROP_OLDEST) //.onEmpty { emit(arrayListOf<ScannedDevice>()) }
        val sharedScanDev = _scanDevices.asSharedFlow()

        //val _scanDevices : MutableStateFlow<ArrayList<ScannedDevice>> = MutableStateFlow(arrayListOf<ScannedDevice>())
        //val scanDevices = _scanDevices.asStateFlow()

        ///
        //private val _uiState: MutableStateFlow<ExampleViewState> = MutableStateFlow(initialState)
        //val uiState = _uiState.asStateFlow()
        //val channel = Channel<arrayListOf<ScannedDevice>()>

        var outputBytesNotifyIndicate = MutableSharedFlow<CharacterCarrier>(10,0, BufferOverflow.SUSPEND)
        var outputBytesRead           = MutableSharedFlow<CharacterCarrier>(10,0, BufferOverflow.SUSPEND)

        var servicesCharacteristics   = MutableSharedFlow<MutableList<CharacterCarrier>>(10,0, BufferOverflow.SUSPEND)
        //var BIG_SHARED_STR = mutableStateOf("")
        //    set(value) {
        //        println("field ${field}")
        //        field = value
//      //          if (field.value.length > 2000) {
//      //              field.value = ""
////
//      //              field = value
//      //          }
//
        //    }
    }
}
