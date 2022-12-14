package com.arstagaev.flowble.enums

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.le.ScanFilter
import java.util.*

abstract class BleOperation() {
    abstract val isImportant: Boolean
    abstract val name : String
}

/** Abstract sealed class representing a type of BLE operation */
sealed class BleTarget : BleOperation() {
    abstract val address: String
}

data class StartScan(
    val scanFilters: MutableList<ScanFilter>? = null,
    override val isImportant: Boolean = false,
    override val name : String = "StartScan"
) : BleOperation()

data class StopScan(override val isImportant: Boolean = false, override val name : String = "StopScan") : BleOperation()
/** Now work in progress: */
data class Advertise(val isActive: Boolean? = null, override val isImportant: Boolean = true, override val name : String = "Advertise") : BleOperation()

/** Connect to [device] and perform service discovery */
data class Connect(override val address: String, val withBondingRequest: Boolean = false, override val isImportant: Boolean = true,
                   override val name : String = "Connect") : BleTarget()

/** Disconnect from [device] and release all connection resources */
data class Disconnect(override val address: String, override val isImportant: Boolean = true, override val name : String = "Disconnect") : BleTarget()

data class DiscoveryServices(override val address: String, override val isImportant: Boolean = true, override val name : String = "DiscoveryServices") : BleTarget()

/** Write [payload] as the value of a characteristic represented by [characteristicUuid] */
data class WriteToCharacteristic(
    override val address: String,
    val characteristicUuid: UUID,
    val writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT, // ?
    val payload: ByteArray,
    override val isImportant: Boolean = true,
    override val name : String = "WriteToCharacteristic"
) : BleTarget() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WriteToCharacteristic

        if (address != other.address) return false
        if (characteristicUuid != other.characteristicUuid) return false
        if (writeType != other.writeType) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = address.hashCode()
        result = 31 * result + characteristicUuid.hashCode()
        result = 31 * result + writeType
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

/** Read the value of a characteristic represented by [characteristicUuid] */
data class ReadFromCharacteristic(
    override val address: String,
    val characteristicUuid: UUID,
    override val isImportant: Boolean = true,
    override val name : String = "ReadFromCharacteristic"
) : BleTarget()

/** Enable notifications/indications on a characteristic represented by [characteristicUuid] */
data class EnableNotifications(
    override val address: String,
    val characteristicUuid: UUID,
    val isSuccess: (Boolean) -> Unit,
    override val isImportant: Boolean = true,
    override val name : String = "EnableNotifications"
) : BleTarget()

/** Disable notifications/indications on a characteristic represented by [characteristicUuid] */
data class DisableNotifications(
    override val address: String,
    val characteristicUuid: UUID,
    override val isImportant: Boolean = true,
    override val name : String = "DisableNotifications"
) : BleTarget()

/** Request for an MTU of [mtu] */
data class MtuRequest(
    override val address: String,
    val mtu: Int,
    override val isImportant: Boolean = true,
    override val name : String = "MtuRequest"
) : BleTarget()

/** */
data class GetBatteryLevel(
    override val address: String,
    override val isImportant: Boolean = true,
    override val name : String = "GetBatteryLevel"
) : BleTarget()

/** */
data class UnBondDeviceFromPhone(
    override val address: String,
    override val isImportant: Boolean = true,
    override val name : String = "UnBondDeviceFromPhone"
) : BleTarget()

/** retard is delay in French */
data class Retard(
    val duration: Long, override val isImportant: Boolean = true,
    override val name : String = "Retard"
) : BleOperation()

/** Disable work of ble module */
class DisableBleManager(
    override val isImportant: Boolean = true,
    override val name : String = "DisableBleManager"
) : BleOperation()
