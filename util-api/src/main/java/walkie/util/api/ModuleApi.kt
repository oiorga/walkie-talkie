package walkie.util.api

import kotlinx.coroutines.CoroutineScope

interface ModuleOpApi<TArg> {
    fun set(prop: TArg, value: TArg): TArg
    fun get(prop: TArg): TArg
    fun subscribe(to: TArg, onEvent: TArg): Unit
    fun send(to: TArg, msg: TArg): Unit
    /*
    fun registerEvent(to: TArg, callback: TArg): Unit
    */
    fun execute(cmd: TArg): Unit
}

/* Module Operation Model */
sealed class ModOpMod {
    companion object {
        val None: Result.None
            get() = Result.None
    }
    data class Destination<Dest>(val id: Dest) : ModOpMod()
    data class Property<Prop>(val id: Prop): ModOpMod()
    data class Command<Cmd>(val id: Cmd): ModOpMod()
    data class Message<MType, Data>(val type: MType, val data: Data): ModOpMod()
    sealed class DataIn<in Data>: ModOpMod() {
        data class Value<Data>(val value: Data?): DataIn<Data>()
    }
    sealed class Result<out Data>: ModOpMod() {
        data object None: Result<Nothing>()
        data object Success: Result<Nothing>()
        data object Fail: Result<Nothing>()
        data class Value<out Data>(val value: Data?) : Result<Data>()
    }

    data class OnEvent<Dest, Message>(
        val onEventCall: (suspend (Dest, Message) -> Unit),
        val scope: CoroutineScope
    ) : ModOpMod()
}

interface ModuleOpModBaseApi<Prop: Cmd, Dest, OnEvent, Cmd, MType, Data> {
    fun set(prop: ModOpMod.Property<Prop>, value: ModOpMod.DataIn<Data>): ModOpMod.Result<Nothing>
    fun get(prop: ModOpMod.Property<Prop>): ModOpMod.Result<Data>
    fun subscribe(to: ModOpMod.Destination<Dest>, onEvent: ModOpMod.OnEvent<Dest, ModOpMod.Message<MType, Data>>): ModOpMod.Result<Nothing>
    fun send(to: ModOpMod.Destination<Dest>, msg: ModOpMod.Message<MType, Data>): ModOpMod.Result<Nothing>
    fun execute(cmd: ModOpMod.Command<Cmd>): ModOpMod.Result<Nothing>
}

interface ModuleOpModApi<
        Prop: Cmd,
        Dest: MessageBusId,
        OnEvent: ModOpMod.OnEvent<Dest, ModOpMod.Message<MType, Data>>,
        Cmd: RemoteCallId,
        MType: BusMessageType,
        Data>: ModuleOpModBaseApi<Prop, Dest, OnEvent, Cmd, MType, Data>
{
    fun toModOpMessage(msg: BusMessageApi<MType, Data>): ModOpMod.Message<MType, Data>
    fun toBusMessage(msg: ModOpMod.Message<MType, Data>): BusMessageApi<MType, Data>
}
