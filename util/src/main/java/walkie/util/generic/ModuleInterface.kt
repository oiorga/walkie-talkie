package walkie.util.generic

interface ModuleOpInterface<TArg> {
    fun set(propId: TArg, value: TArg): TArg
    fun get(propId: TArg): TArg
    fun subscribe(to: TArg, onEvent: TArg): Unit
    fun send(to: TArg, msg: TArg): Unit
    fun registerCallback(chId: TArg, callback: TArg): Unit
}