package walkie.util.generic

interface ModuleOpInt<T> {
    fun <I, O> execute(op: ModuleOp.Type<T, O>, input: ModuleOp.Input<I>): ModuleOp.Output<O>
    fun <I, O> modSet(opId: T, input: I): ModuleOp.Output<O>
    fun <I, O> modGet(opId: T): ModuleOp.Output<O>
    fun <I, O> modSend(opId: T, input: I): ModuleOp.Output<O>
    fun <I, O> modSetAsync(opId: T, input: I): ModuleOp.Output<O>
    fun <I, O> modRegister(opId: T, input: I): ModuleOp.Output<O>
}

sealed class ModuleOp {
    sealed class Type<T, O> : ModuleOp() {
        data class Set<T, O>(val id: T): Type<T, O>()
        data class Get<T, O>(val id: T): Type<T, O>()
        data class Send<T, O>(val id: T): Type<T, O>()
        data class Register<T, O>(val id: T): Type<T, O>()
        data class SetAsync<T, O>(val id: T) : Type<T, O>()
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
                modSet(op.id, input.value)
            }

            is ModuleOp.Type.Get -> {
                modGet<I, O>(op.id)
            }

            is ModuleOp.Type.Send -> {
                modSend(op.id, input.value)
            }

            is ModuleOp.Type.SetAsync -> {
                modSetAsync(op.id, input.value)
            }

            is ModuleOp.Type.Register -> {
                modRegister(op.id, input.value)
            }
        }
    }

    override fun <I, O> modSet(opId: T, input: I): ModuleOp.Output<O> {
        error("$TAG Not Implemented")
    }

    override fun <I, O> modGet(opId: T): ModuleOp.Output<O> {
        error("$TAG Not Implemented")
    }

    override fun <I, O> modSend(opId: T, input: I): ModuleOp.Output<O> {
        error("$TAG Not Implemented")
    }

    override fun <I, O> modSetAsync(opId: T, input: I): ModuleOp.Output<O> {
        error("$TAG Not Implemented")
    }

    override fun <I, O> modRegister(opId: T, input: I): ModuleOp.Output<O> {
        error("$TAG Not Implemented")
    }
}
