package walkie.talkie.api.wtModule

import kotlinx.coroutines.CoroutineScope
import walkie.talkie.api.WTBusMessage
import walkie.talkie.api.WTBusMessageApi
import walkie.talkie.api.WTBusMessageType
import walkie.talkie.api.WTMessageBusApi
import walkie.talkie.api.WTMessageBusId
import walkie.talkie.api.WTRemoteCallApi
import walkie.talkie.api.WTRemoteCallId
import walkie.util.ModuleOpMod
import walkie.util.api.ModOpMod
import walkie.util.api.ModuleOpModApi
import kotlin.Any

interface WTModuleOpModApi: ModuleOpModApi<
        WTRemoteCallId,
        WTMessageBusId,
        WTRemoteCallId,
        WTBusMessageType,
        Any,
        WTBusMessageApi,
        WTMessageBusApi,
        WTRemoteCallApi>

class WTModuleOpMod(
    override val messageBus: WTMessageBusApi?,
    override val remoteCall: WTRemoteCallApi?,
    override val busMessageFactory: (WTBusMessageType, Any?) -> WTBusMessageApi = { type, data ->
        WTBusMessage(
            type = type,
            data = data
        )
    }
): ModuleOpMod<WTRemoteCallId,
        WTMessageBusId,
        WTRemoteCallId,
        WTBusMessageType,
        Any,
        WTBusMessageApi,
        WTMessageBusApi,
        WTRemoteCallApi>(messageBus, remoteCall, busMessageFactory = busMessageFactory),
    WTModuleOpModApi

inline fun <reified Out> WTModuleOpModApi.getPropVal(
    prop: WTModOpMod.Prop
): Out? {
    val out: ModOpMod.Result<*> = this.get(prop)
    if (out !is ModOpMod.Result.Value)
        return null

    return (out.value as? Out)
}

sealed class WTModOpMod: ModOpMod<
        WTRemoteCallId,
        WTMessageBusId,
        WTRemoteCallId,
        WTMessageBusApi,
        WTBusMessageType,
        Any> {
    sealed class To(override val id: WTMessageBusId) : ModOpMod.Destination<WTMessageBusId> {
        object Comm : To(WTMessageBusId.ToComm)
        object Activity : To(WTMessageBusId.ToActivity)
        object Wifi : To(WTMessageBusId.ToWifi)
        object CommonData : To(WTMessageBusId.TOCommonData)
        object IpComm : To(WTMessageBusId.ToIpComm)
        object PRMComm : To(WTMessageBusId.ToPRMComm)
        object Chat : To(WTMessageBusId.ToChat)
    }
    sealed class Prop(override val id: WTRemoteCallId): ModOpMod.Property<WTRemoteCallId, WTRemoteCallId, Any> {
        object WifiPerm: Prop(WTRemoteCallId.RCCheckWifiDPermissions)
    }
    sealed class Cmd(override val id: WTRemoteCallId): ModOpMod.Command<WTRemoteCallId> {
        object WifiPermRequest: Cmd(WTRemoteCallId.RCRequestWifiDPermissions)
    }

    data class Msg(override val type: WTBusMessageType, override val value: Any?): ModOpMod.Message<WTBusMessageApi, WTBusMessageType, Any>

    data class OnEvent(
        override val scope: CoroutineScope,
        override val onEventCall: suspend (WTMessageBusId, ModOpMod.Message<WTBusMessageApi, WTBusMessageType, Any>) -> Unit
    ) : ModOpMod.OnEvent<WTMessageBusId, WTBusMessageApi, WTBusMessageType, Any>
}
