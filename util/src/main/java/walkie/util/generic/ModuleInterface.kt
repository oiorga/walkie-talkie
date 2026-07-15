package walkie.util.generic

interface ModuleOpInterface<TArg> {
    fun set(prop: TArg, value: TArg): TArg
    fun get(prop: TArg): TArg
    fun subscribe(to: TArg, onEventInfo: TArg): Unit
    fun send(to: TArg, msg: TArg): Unit
    fun registerCallback(to: TArg, callback: TArg): Unit
}