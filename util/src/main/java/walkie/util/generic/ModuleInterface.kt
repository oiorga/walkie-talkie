package walkie.util.generic

interface ModuleOpInterface<TOp> {
    fun <Output> set(modReq: TOp): ModuleOp.Output<Output>
    fun <Output> get(modReq: TOp): ModuleOp.Output<Output>
    fun subscribe(modReq: TOp): ModuleOp.Output.Empty
    fun send(modReq: TOp): ModuleOp.Output.Empty
    fun registerCallback(modReq: TOp): ModuleOp.Output.Empty
}

sealed class ModuleOp<TOp> {
    sealed class Get<TOp>: ModuleOp<TOp>() {
        data class Request<TOp> (val get: TOp): Get<TOp>()
    }

    sealed class Set<TOp>: ModuleOp<TOp>() {
        data class Value<TOp> (val set: TOp): Set<TOp>()
    }

    data class Subscribe<TOp>(
        val to: TOp,
        val onEvent: TOp,
    ): ModuleOp<TOp>()

    data class Send<TOp>(
        val to: TOp,
        val msg: TOp
    ): ModuleOp<TOp>()

    data class Input<I>(val value: I) : ModuleOp<Nothing>()
    sealed class Output<out O>: ModuleOp<Nothing>() {
        data class Value<O>(val value: O) : Output<O>()
        data object Empty : Output<Nothing>()
    }

    object None: ModuleOp<Nothing>()
}
