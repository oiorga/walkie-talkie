package walkie.util

import walkie.util.api.BusMessageType
import walkie.util.api.BusMessageApi
import walkie.util.api.MessageBusApi
import walkie.util.api.MessageBusId
import walkie.util.api.ModOpMod
import walkie.util.api.ModuleOpModApi
import walkie.util.api.RemoteCallApi
import walkie.util.api.RemoteCallId

abstract class ModuleOpMod<
        Prop: Cmd,
        Dest: MessageBusId,
        Cmd: RemoteCallId,
        MType: BusMessageType,
        DType,
        MsgApi: BusMessageApi<MType, DType>,                /* Some will say that these transport APIs don't make sense here. */
        BusApi: MessageBusApi<Dest, MsgApi, MType, DType>,  /* We agree and we don't care. */
        CallApi: RemoteCallApi<Cmd>                         /* Let this be the only flaw of this app. */
        >(
    override val messageBus: BusApi?,
    override val remoteCall: CallApi?,
    override val busMessageFactory: ((MType, DType?) -> MsgApi),
): ModuleOpModApi<Prop, Dest, Cmd, MType, DType, MsgApi, BusApi, CallApi> {

    companion object {
        const val TAG = "ModOpMod"
        val TAGKClass = ModOpMod::class
    }

    override fun set(
        prop: ModOpMod.Property<Prop, Cmd, DType>,
        value: ModOpMod.Data.In<DType>
    ): ModOpMod.Result<Nothing> {
        TODO("Not yet implemented")
    }

    override fun get(prop: ModOpMod.Property<Prop, Cmd, DType>): ModOpMod.Result<*> {
        return (remoteCall?.remoteCall(remoteCallId = prop.id))?.let {
            ModOpMod.Result.Value(it)
        } ?: run {
            ModOpMod.Null
        }
    }

    fun modOpMessage(msg: BusMessageApi<MType, DType>): ModOpMod.Message<MsgApi, MType, DType> {
        val tag = "modOpMessage/${randomString(2U)}"
        return ModOpMod.Message.build(
            type = msg.type,
            value = msg.data,
            build = { type, value ->
                busMessageFactory?.invoke(type, value) ?: error ("$tag busMessageFactory is null")
            }
        )
    }

    override fun subscribe(
        to: ModOpMod.Destination<Dest>,
        onEvent: ModOpMod.OnEvent<Dest, MsgApi, MType, DType>
    ): ModOpMod.Result<Nothing> {
        messageBus?.busSubscribe(
            busId = to.id,
            scope = onEvent.scope,
            autoCreate = true,
            onReceive = { dest, busMessage ->
                onEvent.onEventCall(dest, modOpMessage(busMessage))
            }
        ) ?: run {
            return ModOpMod.Null
        }

        return ModOpMod.Success
    }

    fun busMessage(msg: ModOpMod.Message<MsgApi, MType, DType>): MsgApi {
        val tag = "busMessage/${randomString(2U)}"
        return busMessageFactory?.invoke(msg.type, msg.value) ?: error ("$tag busMessageFactory is null")
    }

    override fun send(
        to: ModOpMod.Destination<Dest>,
        modMsg: ModOpMod.Message<MsgApi, MType, DType>
    ): ModOpMod.Result<Nothing> {

        messageBus?.busSendAsync(
            busId = to.id,
            msg = busMessage(modMsg)
        ) ?: return ModOpMod.Null

        return ModOpMod.Success
    }

    override fun execute(cmd: ModOpMod.Command<Cmd>): ModOpMod.Result<Nothing> {
        remoteCall?.remoteCall(cmd.id) ?: run {return ModOpMod.Null }
        return ModOpMod.Success
    }
}
