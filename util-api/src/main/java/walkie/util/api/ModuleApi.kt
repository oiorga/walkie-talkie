package walkie.util.api

import kotlinx.coroutines.CoroutineScope
import walkie.util.api.ModOpMod.Message

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
sealed interface ModOpMod<Prop: Cmd, Dest, Cmd, Msg, MType, DType> {
    companion object {
        val None: Result.None
            get() = Result.None
        val Null: Result.Null
            get() = Result.Null
        val Success = Result.Success
        val Fail = Result.Fail
    }
    interface Destination<Dest> : ModOpMod<Nothing, Dest, Nothing, Nothing, Nothing, Nothing> {
        val id: Dest

        companion object {
            fun <Dest>id(id: Dest): Destination<Dest> = object: Destination<Dest> {
                override val id = id
            }
        }
    }

    interface Property<Prop: Cmd, Cmd, DType>: ModOpMod<Prop, Nothing, Cmd, Nothing, Nothing, DType> {
        val id: Prop
        val value: DType?
            get() = null

        companion object {
            fun <Prop: Cmd, Cmd, DType>build(id: Prop, value: DType): Property<Prop, Cmd, DType> {
                return object: Property<Prop, Cmd, DType> {
                    override val id = id
                    override val value = value
                }
            }
        }
    }
    interface Command<Cmd>: ModOpMod<Nothing, Nothing, Cmd, Nothing, Nothing, Nothing> {
        val id: Cmd

        companion object {
            fun <Cmd>id(id: Cmd): Command<Cmd> = object: Command<Cmd> {
                override val id = id
            }
        }
    }
    interface Message<Msg, MType, DType>: ModOpMod<Nothing, Nothing, Nothing, Msg, MType, DType> {
        val type: MType
        val value: DType?
        fun message(type: MType, value: DType?): Msg

        companion object {
            fun <Msg, MType, DType>build(
                type: MType,
                value: DType?,
                message: (MType, DType?) -> Msg): Message<Msg, MType, DType> {
                return object: Message<Msg, MType, DType> {
                    override val type = type
                    override val value = value
                    override fun message(type: MType, value: DType?): Msg {
                        return message(type, value)
                    }
                }
            }
        }
    }

    sealed interface Data<DType>: ModOpMod<Nothing, Nothing, Nothing, Nothing, Nothing, DType> {
        val value: DType

        data class In<DType>(override val value: DType) : Data<DType>
    }

    sealed interface Result<DType>: ModOpMod <Nothing, Nothing, Nothing, Nothing, Nothing, DType>{
        data object None: Result<Nothing>
        data object Null: Result<Nothing>
        data object Success: Result<Nothing>
        data object Fail: Result<Nothing>
        data class Value<DType>(val value: DType) : Result<DType>
    }

    data class OnEvent<Dest, Msg, MType, DType>(
        val onEventCall: (suspend (Dest, ModOpMod.Message<Msg, MType, DType>) -> Unit),
        val scope: CoroutineScope
    ) : ModOpMod<Nothing, Dest, Nothing, Msg, MType, DType>
}

interface ModuleOpModApi<Prop: Cmd, Dest: MessageBusId, Cmd: RemoteCallId, MType: BusMessageType, DType,
        MsgApi: BusMessageApi<MType, DType>,
        BusApi: MessageBusApi<Dest, MsgApi, MType, DType>,
        CallApi: RemoteCallApi<Cmd>> {
        val messageBus: BusApi?
        val remoteCall: CallApi?
        fun set(prop: ModOpMod.Property<Prop, Cmd, DType>, value: ModOpMod.Data.In<DType>): ModOpMod.Result<Nothing>
        fun get(prop: ModOpMod.Property<Prop, Cmd, DType>): ModOpMod.Result<*>
        fun subscribe(to: ModOpMod.Destination<Dest>, onEvent: ModOpMod.OnEvent<Dest, MsgApi, MType, DType>): ModOpMod.Result<Nothing>
        fun send(to: ModOpMod.Destination<Dest>, msg: ModOpMod.Message<MsgApi, MType, DType>): ModOpMod.Result<Nothing>
        fun execute(cmd: ModOpMod.Command<Cmd>): ModOpMod.Result<Nothing>
}
