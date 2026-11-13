package walkie.util

import walkie.util.api.BusMessageType
import walkie.util.api.MessageBusApi
import walkie.util.api.BusMessageApi
import walkie.util.api.MessageBusId
import walkie.util.api.ModOpMod
import walkie.util.api.ModuleOpModApi
import walkie.util.api.RemoteCallApi
import walkie.util.api.RemoteCallId

/*
* Critics will say that particular details (remote call and bus exchanging messages mechanisms) to the implementation should be kept to the respective module. But we don't care.
*
* We could move the implementation details down the inheritance chain and keep here only the data structures and interfaces, but we don't care. Let this be the only misalignment.
*/

open class ModuleOpMod<
        Prop: Cmd,
        Dest: MessageBusId,
        OnEvent: ModOpMod.OnEvent<Dest, ModOpMod.Message<MType, Data>>,
        Cmd: RemoteCallId,
        MType: BusMessageType,
        Data>(
    private val _busMap: MessageBus<Dest, BusMessageApi<MType, Data>, MType, Data> = MessageBus(),
    private val _callMap: RemoteCall<Cmd> = RemoteCall()
):  ModuleOpModApi<Prop, Dest, OnEvent, Cmd, MType, Data>,
    MessageBusApi<Dest, BusMessageApi<MType, Data>, MType, Data> by _busMap,
    RemoteCallApi<Cmd> by _callMap {

    companion object {
        const val TAG = "ModOpMod"
        val TAGKClass = ModOpMod::class
    }

    override fun set(
        prop: ModOpMod.Property<Prop>,
        value: ModOpMod.DataIn<Data>
    ): ModOpMod.Result<Nothing> {
        TODO("Not yet implemented")
    }

    override fun get(prop: ModOpMod.Property<Prop>): ModOpMod.Result<Data> {
        return (remoteCall(remoteCallId = prop.id) as? Data)?.let {
            ModOpMod.Result.Value(it)
        } ?: run {
            /* To flag here type mismatch */
            ModOpMod.None
        }
    }

    override fun toModOpMessage(msg: BusMessageApi<MType, Data>): ModOpMod.Message<MType, Data> {
        val tag = "convertOnEventData/${randomString(2U)}"
        return (msg.data as? Data)?.let { it ->
            ModOpMod.Message<MType, Data>(msg.type, it)
        } ?: run {
            error("$tag. Cannot convert $msg to BusMessageApi")
        }
    }

    override fun subscribe(
        to: ModOpMod.Destination<Dest>,
        onEvent: ModOpMod.OnEvent<Dest, ModOpMod.Message<MType, Data>>
    ): ModOpMod.Result<Nothing> {
        busSubscribe(
            busId = to.id,
            scope = onEvent.scope,
            autoCreate = true,
            onReceive = { dest, busMessage ->
                onEvent.onEventCall(dest, toModOpMessage(busMessage))
            }
        )

        return ModOpMod.None
    }

    override fun toBusMessage(msg: ModOpMod.Message<MType, Data>): BusMessageApi<MType, Data> {
        return BusMessage<MType, Data>(msg.type, msg.data)
    }

    override fun send(
        to: ModOpMod.Destination<Dest>,
        msg: ModOpMod.Message<MType, Data>
    ): ModOpMod.Result<Nothing> {
        busSendAsync(busId = to.id, toBusMessage(msg))
        return ModOpMod.None
    }

    override fun execute(cmd: ModOpMod.Command<Cmd>): ModOpMod.Result<Nothing> {
        remoteCall(cmd.id)
        return ModOpMod.None
    }
}
