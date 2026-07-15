package walkie.talkie.api.wtModule

import kotlinx.coroutines.CoroutineScope
import walkie.talkie.api.wtdebug.wtError
import walkie.util.api.MessageBusIdInt
import walkie.util.api.BusMessageInt
import walkie.util.api.MessageBusInt
import walkie.util.api.RemoteCallIdInt
import walkie.util.api.RemoteCallMuxInt
import walkie.util.generic.MessageBus
import walkie.util.generic.ModuleOpInterface
import walkie.util.generic.RemoteCallMux
import walkie.util.randomString

interface ModuleOpInt : ModuleOpInterface<WTModOpArg>
typealias WTPipeMessage = BusMessageInt<PipeMessageType, Any>

class ModuleOpImpl(
    private val _busMap: MessageBusInt<PipeMessageType, Any> = MessageBus(),
    private val _callMap: RemoteCallMuxInt = RemoteCallMux()
):
    MessageBusInt<PipeMessageType, Any> by _busMap,
    RemoteCallMuxInt by _callMap,
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
    sealed class To(val id: MessageBusIdInt): WTModOpArg() {
        object Comm: To(MessageBusId.ToComm)
        object Activity: To(MessageBusId.ToActivity)
        object Wifi: To(MessageBusId.ToWifi)
        object CommonData: To(MessageBusId.TOCommonData)
        object IpComm: To(MessageBusId.ToIpComm)
        object PRMComm: To(MessageBusId.ToPRMComm)
        object Chat: To(MessageBusId.ToChat)
    }

    sealed class Prop(val id: RemoteCallIdInt): WTModOpArg() {
        object WifiPerm: Prop(RemoteCallId.RCCheckWifiDPermissions)
        object WifiPermRequest: Prop(RemoteCallId.RCRequestWifiDPermissions)
    }

    data class OnEventInfo(
        val onEvent: (suspend (MessageBusIdInt, BusMessageInt<PipeMessageType, Any>) -> Unit),
        val scope: CoroutineScope
    ) : WTModOpArg()
    data class Msg(val msg: WTPipeMessage) : WTModOpArg()

    data class Data<out Data>(val value: Data?): WTModOpArg()

    object None : WTModOpArg()
}

inline fun <reified Out> ModuleOpInt.getVal(
    id: WTModOpArg.Prop
): Out? {
    return (this.get(id) as? WTModOpArg.Data<*>)?.value as? Out
}

enum class PipeMessageType {
    PipeMessageNA,
    ControlMesh,
    ChatMessage,
    ChatMessageLoopback,
    WifiMessage,
    WifiDebugInfoMessage,
    WifiRestartChannel,
    MeshDebugInfoMessage,
    WifiBroadcastReceiver,
    LocalIp,
    LocalServerPort,
    GroupInfo,
    GroupServerPort,
    UpdateChatUI,
    UpdatePeersUI,
    CommToChatPacket
}

enum class MessageBusId : MessageBusIdInt {
    PipeNA,
    ToComm,
    ToWifi,
    ToChat,
    ToPRMComm,
    ToIpComm,
    ToActivity,
    TOCommonData
}

enum class RemoteCallId : RemoteCallIdInt {
    RCDummy0, RCDummy1, RCDummy2, RCDummy3, RCDummy4, RCDummy5, RCDummy6, RCDummy7, RCDummy8, RCDummy9,
    RCNA,
    RCCheckWifiDPermissions,
    RCRequestWifiDPermissions,
    RCChatMessageToComm,
    RCChatMessageFromComm,
    RCSendChatMessage,
    RCUpdateUI
}
