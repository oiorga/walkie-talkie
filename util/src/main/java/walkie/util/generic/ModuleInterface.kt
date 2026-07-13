package walkie.util.generic

interface ModuleOpInterface<TArg> {
    fun set(propId: TArg, value: TArg): TArg
    fun get(propId: TArg): TArg
    fun subscribe(to: TArg, onEventInfo: TArg): Unit
    fun send(to: TArg, msg: TArg): Unit
    fun registerCallback(to: TArg, callback: TArg): Unit
}