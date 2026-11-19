package walkie.talkie.api.wtModule

import kotlinx.coroutines.CoroutineScope
import walkie.talkie.api.WTBusMessage
import walkie.talkie.api.WTBusMessageApi
import walkie.talkie.api.WTBusMessageType
import walkie.talkie.api.WTMessageBus
import walkie.talkie.api.WTMessageBusApi
import walkie.talkie.api.WTMessageBusId
import walkie.talkie.api.WTRemoteCall
import walkie.talkie.api.WTRemoteCallApi
import walkie.talkie.api.WTRemoteCallId
import walkie.talkie.api.wtdebug.wtError
import walkie.util.ModuleOpMod
import walkie.util.api.BusMessageApi
import walkie.util.api.BusMessageType
import walkie.util.api.MessageBusApi
import walkie.util.api.MessageBusId
import walkie.util.api.ModOpMod
import walkie.util.api.ModuleOpApi
import walkie.util.api.ModuleOpModApi
import walkie.util.api.RemoteCallApi
import walkie.util.api.RemoteCallId
import walkie.util.randomString
import kotlin.Any

interface WTModuleOpModApi :
    ModuleOpModApi<
            WTRemoteCallId,
            WTMessageBusId,
            WTRemoteCallId,
            WTBusMessageType,
            Any,
            WTBusMessageApi,
            WTMessageBusApi,
            WTRemoteCallApi>

class WTModuleOpMod(
    override val messageBus: WTMessageBusApi,
    override val remoteCall: WTRemoteCallApi,
    busMessageFactory: (WTBusMessageType, Any?) -> WTBusMessageApi
): ModuleOpMod<WTRemoteCallId,
        WTMessageBusId,
        WTRemoteCallId,
        WTBusMessageType,
        Any,
        WTBusMessageApi,
        WTMessageBusApi,
        WTRemoteCallApi>(messageBus, remoteCall, busMessageFactory)




interface WTModuleOpApi: ModuleOpApi<WTModOpArg>

class WTModuleOp(
    private val _busMap: WTMessageBus = WTMessageBus(),
    private val _callMap: WTRemoteCall = WTRemoteCall()
):
    WTMessageBusApi by _busMap,
    WTRemoteCallApi by _callMap,
    WTModuleOpApi
{
    companion object {
        const val TAG = "WTModuleOp"
        val TAGKClass = WTModuleOp::class
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
        onEvent: WTModOpArg
    ) {
        val tag = "subscribe/${randomString(2U)}"

        val to = (to as? WTModOpArg.To)?.id ?: run {
            wtError(tag, "Invalid input: to -> $to")
            return
        }

        (onEvent as? WTModOpArg.OnEvent)?.let { eventInfo ->
            busSubscribe(
                busId = to,
                scope = eventInfo.scope,
                autoCreate = true,
                onReceive = eventInfo.onEvent
            )
        } ?: run {
            wtError(tag, "Invalid input: onEvent -> $onEvent")
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

        busSendAsync(busId = to, msg = msg as WTBusMessage)
    }

    /*
    override fun registerEvent(
        to: WTModOpArg,
        callback: WTModOpArg
    ) {
        TODO("Not yet implemented")
    }
    */
}

sealed class WTModOpArg {
    sealed class To(val id: WTMessageBusId): WTModOpArg() {
        object Comm: To(WTMessageBusId.ToComm)
        object Activity: To(WTMessageBusId.ToActivity)
        object Wifi: To(WTMessageBusId.ToWifi)
        object CommonData: To(WTMessageBusId.TOCommonData)
        object IpComm: To(WTMessageBusId.ToIpComm)
        object PRMComm: To(WTMessageBusId.ToPRMComm)
        object Chat: To(WTMessageBusId.ToChat)
    }

    sealed class Prop(val id: WTRemoteCallId): WTModOpArg() {
        object WifiPerm: Prop(WTRemoteCallId.RCCheckWifiDPermissions)
    }

    sealed class Cmd(val id: WTRemoteCallId): WTModOpArg() {
        object WifiPermRequest: Cmd(WTRemoteCallId.RCRequestWifiDPermissions)
    }

    data class OnEvent(
        val onEvent: (suspend (WTMessageBusId, WTBusMessageApi) -> Unit),
        val scope: CoroutineScope
    ) : WTModOpArg()
    data class Msg(val msg: WTBusMessage) : WTModOpArg()

    data class Data<out Data>(val value: Data?): WTModOpArg()

    object None : WTModOpArg()
}

inline fun <reified Out> WTModuleOpApi.getVal(
    id: WTModOpArg.Prop
): Out? {
    return (this.get(id) as? WTModOpArg.Data<*>)?.value as? Out
}

