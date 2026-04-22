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
import android.os.Build
import walkie.glue_inc.ChannelId
import walkie.glue_inc.ChannelMessageType
import walkie.util.generic.ChannelMux
import walkie.util.generic.ChannelMuxInt
import walkie.util.getClassSimpleName
import walkie.util.logd
import walkie.util.logging

/**
 * A BroadcastReceiver that notifies of important wifi p2p events.
 */
class WiFiDirectBroadcastReceiver (
    private val _channelMux: ChannelMuxInt<Any, ChannelMessageType> = ChannelMux<Any, ChannelMessageType>()
) : BroadcastReceiver(),
    ChannelMuxInt<Any, ChannelMessageType> by _channelMux
{
    companion object {
        const val TAG = "WiFiDirectBroadcastReceiver"
    }

    val tag = getClassSimpleName()

    private fun extraLog(tag: String, message: String) {

    }

    init {
        logging(true) { tag, message ->
            extraLog(tag, message)
        }
    }

    override fun onReceive(cntxt: Context, intent: Intent) {
        val action = intent.action

        logd("$tag: onReceive: intent.action: ${action.toString()}")
        when (action) {
            WIFI_P2P_DISCOVERY_CHANGED_ACTION,
            WIFI_P2P_STATE_CHANGED_ACTION,
            WIFI_P2P_PEERS_CHANGED_ACTION,
            WIFI_P2P_CONNECTION_CHANGED_ACTION,
            WIFI_P2P_THIS_DEVICE_CHANGED_ACTION,
            -> {
                channelSend(
                    channelId = ChannelId.RCToWifi,
                    input = intent,
                    inputType = ChannelMessageType.RCWifiBroadcastReceiver)
            }
            /*
            WIFI_P2P_STATE_CHANGED_ACTION -> {
                channelSend(
                    channelId = WTChannelId.RCToWifi,
                    input = intent,
                    inputType = WTChannelMessageType.RCWifiBroadcastReceiver)
            }
            WIFI_P2P_PEERS_CHANGED_ACTION -> {
                /*
                 * Request available peers from the wifi p2p manager. This is an
                 * asynchronous call and the calling activity is notified with a
                 * callback on PeerListListener.onPeersAvailable()
                 */
                channelSend(
                    channelId = WTChannelId.RCToWifi,
                    input = intent,
                    inputType = WTChannelMessageType.RCWifiBroadcastReceiver)
            }
            WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                channelSend(
                    channelId = WTChannelId.RCToWifi,
                    input = intent,
                    inputType = WTChannelMessageType.RCWifiBroadcastReceiver)
            }
            WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                channelSend(
                    channelId = WTChannelId.RCToWifi,
                    input = intent,
                    inputType = WTChannelMessageType.RCWifiBroadcastReceiver)
            }
            */
            else -> {
                logd("$tag: P2P changed to Not Addressed ${action.toString()}")
                throw (NotImplementedError("$tag: $tag: P2P changed to Not Addressed ${action.toString()}"))
            }
        }
    }
}
