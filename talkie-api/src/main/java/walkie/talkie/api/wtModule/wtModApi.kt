package walkie.talkie.api.wtModule

import kotlinx.coroutines.CoroutineScope
import walkie.talkie.api.WTBusMessageApi
import walkie.talkie.api.WTBusMessageId
import walkie.talkie.api.WTMessageBusApi
import walkie.talkie.api.WTMessageBus
import walkie.talkie.api.WTRemoteCallApi
import walkie.talkie.api.WTRemoteCall
import walkie.talkie.api.WTRemoteCallId
import walkie.talkie.api.wtdebug.wtError
import walkie.util.api.ModuleOpInterface
import walkie.util.api.RemoteCallApi

import walkie.util.randomString

interface ModuleOpInt : ModuleOpInterface<WTModOpArg>

class ModuleOpImpl(
    private val _busMap: WTMessageBusApi = WTMessageBus(),
    private val _callMap: WTRemoteCallApi = WTRemoteCall()
):
    WTMessageBusApi by _busMap,
    RemoteCallApi<WTRemoteCallId> by _callMap,
    ModuleOpInt
{
    companion object {
        const val TAG = "WTWiFiDirectManager"
        val TAGKClass = ModuleOpImpl::class
    }

    override fun set(
        prop: WTModOpArg,
        value: WTModOpArg
    ): WTModOpArg {
        TODO("Not yet implemented")
    }

    override fun execute(
        cmd: WTModOpArg
    ) {
        remoteCall(
            remoteCallId =
                (cmd as? WTModOpArg.Cmd?)?.id ?: run {
                    wtError("Invalid input: $cmd")
                    return
                }
        )
    }

    override fun get(prop: WTModOpArg): WTModOpArg {
        return WTModOpArg.Data(remoteCall(
            remoteCallId =
                (prop as? WTModOpArg.Prop?)?.id ?: run {
                    wtError("Invalid input: $prop")
                    return WTModOpArg.None
                }
            )
        )
    }

    override fun subscribe(
        to: WTModOpArg,
        onEventInfo: WTModOpArg
    ) {
        val tag = "subscribe/${randomString(2U)}"

        val to = (to as? WTModOpArg.To)?.id ?: run {
            wtError(tag, "Invalid input: to -> $to")
            return
        }

        (onEventInfo as? WTModOpArg.OnEventInfo)?.let { eventInfo ->
            busSubscribe(
                busId = to,
                scope = eventInfo.scope,
                autoCreate = true,
                onReceive = eventInfo.onEvent
            )
        } ?: run {
            wtError(tag, "Invalid input: onEvent -> $onEventInfo")
            return
        }
    }

    override fun send(
        to: WTModOpArg,
        msg: WTModOpArg
    ) {
        val tag = "sendEvent/${randomString(2U)}"

        val to = (to as? WTModOpArg.To)?.id ?: run {
            wtError(tag, "Invalid input: to -> $to")
            return
        }

        val msg = (msg as? WTModOpArg.Msg)?.msg ?: run {
            wtError(tag, "Invalid msg: $msg")
            return
        }

        busSendAsync(busId = to, msg = msg)
    }

    override fun registerCallback(
        to: WTModOpArg,
        callback: WTModOpArg
    ) {
        TODO("Not yet implemented")
    }
}

sealed class WTModOpArg {
    sealed class To(val id: WTBusMessageId): WTModOpArg() {
        object Comm: To(WTBusMessageId.ToComm)
        object Activity: To(WTBusMessageId.ToActivity)
        object Wifi: To(WTBusMessageId.ToWifi)
        object CommonData: To(WTBusMessageId.TOCommonData)
        object IpComm: To(WTBusMessageId.ToIpComm)
        object PRMComm: To(WTBusMessageId.ToPRMComm)
        object Chat: To(WTBusMessageId.ToChat)
    }

    sealed class Prop(val id: WTRemoteCallId): WTModOpArg() {
        object WifiPerm: Prop(WTRemoteCallId.RCCheckWifiDPermissions)
    }

    sealed class Cmd(val id: WTRemoteCallId): WTModOpArg() {
        object WifiPermRequest: Cmd(WTRemoteCallId.RCRequestWifiDPermissions)
    }

    data class OnEventInfo(
        val onEvent: (suspend (WTBusMessageId, WTBusMessageApi) -> Unit),
        val scope: CoroutineScope
    ) : WTModOpArg()
    data class Msg(val msg: WTBusMessageApi) : WTModOpArg()

    data class Data<out Data>(val value: Data?): WTModOpArg()

    object None : WTModOpArg()
}

inline fun <reified Out> ModuleOpInt.getVal(
    id: WTModOpArg.Prop
): Out? {
    return (this.get(id) as? WTModOpArg.Data<*>)?.value as? Out
}
