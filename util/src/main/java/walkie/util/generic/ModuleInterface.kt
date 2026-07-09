package walkie.util.generic

fun interface CallBackInterface<TChId, TMsg> {
    suspend fun onEvent(
        chId: TChId,
        msg: TMsg
    )
}

interface ModuleOpInterface<TOp> {
    fun <Output> set(modReq: TOp): ModuleOp.Output<Output>
    fun <Output> get(modReq: TOp): ModuleOp.Output<Output>
    fun subscribe(modReq: TOp): ModuleOp.Output.Empty
    fun registerCallback(modReq: TOp): ModuleOp.Output.Empty
}

sealed class ModuleOp {
    sealed class Get<TOpId, V>: ModuleOp() {
        data class Request<TOpId, V> (val opId: TOpId): Get<TOpId, V>()
    }

    sealed class Set<TOpId, V>: ModuleOp() {
        data class Value<TOpId, V> (val opId: TOpId, val value: V): Set<TOpId, V>()
    }

    sealed class Subscribe<TOpId, TChId, TMsg>: ModuleOp() {
        data class CallBack<TOpId, TChId, TMsg> (
            val opId: TOpId,
            val callBack: CallBackInterface<TChId, TMsg>
        ): Subscribe<TOpId, TChId, TMsg>()
    }

    data class Input<I>(val value: I) : ModuleOp()
    sealed class Output<out O>: ModuleOp() {
        data class Value<O>(val value: O) : Output<O>()
        data object Empty : Output<Nothing>()
    }
}
