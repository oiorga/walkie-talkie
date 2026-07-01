package walkie.util.generic

interface ModuleOpInt<T> {
    fun <I, O> execute(op: ModuleOp.Type<T, O>, input: ModuleOp.Input<I>): ModuleOp.Output<O>
    fun <I, O> set(opId: T, input: I): ModuleOp.Output<O>
    fun <I, O> get(opId: T): ModuleOp.Output<O>
    fun <I, Nothing> registerForEvent(opId: T, emitter: I): ModuleOp.Output.Empty
    fun <I, O> registerCallback(opId: T, callBack: I): ModuleOp.Output.Empty
}

sealed class ModuleOp {
    sealed class Type<T, O> : ModuleOp() {
        data class Set<T, O>(val id: T): Type<T, O>()
        data class Get<T, O>(val id: T): Type<T, O>()
        data class RegisterCallback<T, O>(val id: T): Type<T, O>()
        data class RegisterForEvent<T, O>(val id: T): Type<T, O>()
    }

    data class Input<I>(val value: I) : ModuleOp()
    sealed class Output<out O>: ModuleOp() {
        data class Value<O>(val value: O) : Output<O>()
        data object Empty : Output<Nothing>()
    }
}

class ModuleOpImpl<T>(): ModuleOpInt<T> {
    companion object {
        const val TAG = "WTWiFiDirectManager"
        val TAGKClass = ModuleOpImpl::class
    }

    override fun <I, O> execute(
        op: ModuleOp.Type<T, O>,
        input: ModuleOp.Input<I>
    ): ModuleOp.Output<O> {
        return when (op) {
            is ModuleOp.Type.Set -> {
                set(op.id, input.value)
            }

            is ModuleOp.Type.Get -> {
                get<I, O>(op.id)
            }

            is ModuleOp.Type.RegisterForEvent -> {
                registerForEvent<I, Nothing>(op.id, input.value)
            }

            is ModuleOp.Type.RegisterCallback -> {
                registerCallback<I, O>(op.id, input.value)
            }

            else -> {
                ModuleOp.Output.Empty
            }
        }
    }

    override fun <I, O> set(opId: T, input: I): ModuleOp.Output<O> {
        error("$TAG Not Implemented")
    }

    override fun <I, O> get(opId: T): ModuleOp.Output<O> {
        error("$TAG Not Implemented")
    }

    override fun <I, Nothing> registerForEvent(opId: T, emitter: I): ModuleOp.Output.Empty {
        error("$TAG Not Implemented")
    }

    override fun <I, Nothing> registerCallback(opId: T, callBack: I): ModuleOp.Output.Empty {
        error("$TAG Not Implemented")
    }
}
