package com.arstagaev.rgbstrip1

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.arstagaev.flowble.BLEStarter
import com.arstagaev.flowble.enums.*
import com.arstagaev.flowble.extentions.hasPermission
import com.arstagaev.flowble.extentions.requestPermission
import com.arstagaev.rgbstrip1.ui.theme.Rgbstrip1Theme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.*

class MainActivity : ComponentActivity() {

    var bleStarter : BLEStarter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bleStarter = BLEStarter(this).also {
            it.showOperationToasts = true // show logs in Toast
        }

        setContent {
            Rgbstrip1Theme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    Box(modifier = Modifier
                        .fillMaxSize()) {
                        Column(modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween) {
                            Button(
                                modifier = Modifier
                                    .width(200.dp)
                                    .height(90.dp)
                                    .padding(vertical = 10.dp)
                                    .clickable { },colors = ButtonDefaults.buttonColors(backgroundColor = Color.Blue)
                                , onClick = {

                                    if (isAllPermissionsEnabled()) {
                                        launchLib()
                                    }

                                }
                            ) {
                                Text(modifier = Modifier, text = "Start Chill Out",textAlign = TextAlign.Center, color = Color.White)
                            }
                            Button(
                                modifier = Modifier
                                    .width(200.dp)
                                    .height(90.dp)
                                    .padding(vertical = 10.dp)
                                    .clickable { },colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red)
                                , onClick = {

                                    if (isAllPermissionsEnabled()) {
                                        //stopLib()
                                    }

                                }
                            ) {
                                Text(modifier = Modifier, text = "Stop Chill Out",textAlign = TextAlign.Center, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
    // How to use library:
    private fun launchLib() {
        CoroutineScope(lifecycleScope.coroutineContext).launch {
            BLEStarter.bleCommandTrain.emit(mutableListOf(
                StartScan(),
                Retard(4000L),
                Connect("44:44:44:44:44:0C"),
                Retard(2000L),
                StopScan(),
//                WriteToCharacteristic("44:44:44:44:44:0C", characteristicUuid = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8"),
//                    payload = "01".toByteArray()+"02".toByteArray()+"255".toByteArray()+"255".toByteArray()+"255".toByteArray()//byteArrayOf(0x02, 0xFF.toByte())
//                ),
                WriteToCharacteristic("44:44:44:44:44:0C", characteristicUuid = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8"),
                    payload = byteArrayOf(0x00, 0xFF.toByte())
                ),
//                WriteToCharacteristic("44:44:44:44:44:0C", characteristicUuid = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8"),
//                    payload = byteArrayOf(0x02, 0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte())
//                ),
//                Retard(1000L),
//                WriteToCharacteristic("44:44:44:44:44:0C", characteristicUuid = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8"),
//                    payload = byteArrayOf(0x01.toByte(), 0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte())
//                ),
                Retard(1000L),
                WriteToCharacteristic("44:44:44:44:44:0C", characteristicUuid = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8"),
                    payload = byteArrayOf(0x00, 0x00.toByte())
                ),
                Retard(1000L),
            ))
            bleStarter
        }
    }
    private fun isAllPermissionsEnabled(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                requestPermission(Manifest.permission.BLUETOOTH_CONNECT,1)
                return false
            }
            if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
                requestPermission(Manifest.permission.BLUETOOTH_SCAN,2)
                return false
            }
        }

        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            requestPermission(Manifest.permission.ACCESS_FINE_LOCATION,3)
            return false
        }
        return true
    }

}

@Composable
fun Greeting(name: String) {
    Text(text = "Hello $name!")
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    Rgbstrip1Theme {
        Greeting("Android")
    }
}