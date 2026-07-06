/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package walkie.wifidirect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION
import android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION
import android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION
import android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION
import android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION
import kotlinx.coroutines.CoroutineScope
import walkie.talkie.api.wtModule.PipeId
import walkie.talkie.api.wtModule.PipeMessageType
import walkie.util.api.PipeMuxInt
import walkie.util.generic.PipeMessage
import walkie.util.generic.PipeMux
import walkie.util.getDeclaredSimpleName
import walkie.util.logd
import walkie.util.logging

/**
 * A BroadcastReceiver that notifies of important wifi p2p events.
 */
class WiFiDirectBroadcastReceiver (
    val scope: CoroutineScope,
    private val _channelMux: PipeMuxInt<PipeMessageType, Any> = PipeMux<PipeMessageType, Any>()
) : BroadcastReceiver(),
    PipeMuxInt<PipeMessageType, Any> by _channelMux
{
    companion object {
        const val TAG = "WiFiDirectBroadcastReceiver"
    }

    val tag = getDeclaredSimpleName()

    private fun extraLog(tag: String, message: String) {

    }

    init {
        logging(true) { tag, message ->
            extraLog(tag, message)
        }
    }

    override fun onReceive(cntxt: Context, intent: Intent) {
        val action = intent.action

        logd(tag, "onReceive: intent.action: ${action.toString()}")
        when (action) {
            WIFI_P2P_DISCOVERY_CHANGED_ACTION,
            WIFI_P2P_STATE_CHANGED_ACTION,
            WIFI_P2P_PEERS_CHANGED_ACTION,
            WIFI_P2P_CONNECTION_CHANGED_ACTION,
            WIFI_P2P_THIS_DEVICE_CHANGED_ACTION,
            -> {
                pipeSend(
                    pipeId = PipeId.ToWifi,
                    scope = scope,
                    msg = PipeMessage(
                        type = PipeMessageType.WifiBroadcastReceiver,
                        data = intent)
                )
            }

            else -> {
                logd(tag, "P2P changed to Not Addressed ${action.toString()}")
                throw (NotImplementedError("$tag: $tag: P2P changed to Not Addressed ${action.toString()}"))
            }
        }
    }
}
