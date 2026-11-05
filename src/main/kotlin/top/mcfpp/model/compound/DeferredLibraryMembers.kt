package top.mcfpp.model.compound

/**
 * A closed built-in declaration keeps its protocol members until a semantic lookup needs them.
 * Raw journals inspect the state without executing the loader. Loading is reentrant for nominal
 * cycles; a failure poisons this generation until the owning kernel restores its baseline.
 */
internal class DeferredLibraryMembers(
    private val requireOpen: () -> Unit,
    private val load: () -> Unit,
    private val onLoaded: () -> Unit,
) : StructuredTemplateMemberState {
    private enum class State { DEFERRED, LOADING, LOADED, FAILED }
    private var state = State.DEFERRED
    val isDeferred: Boolean get() = state != State.LOADED

    fun materialize() {
        requireOpen()
        when (state) {
            State.LOADED, State.LOADING -> return
            State.FAILED -> error("A failed library member load requires restoring the compiler baseline")
            State.DEFERRED -> Unit
        }
        state = State.LOADING
        try {
            load()
            state = State.LOADED
            onLoaded()
        } catch (failure: Throwable) {
            state = State.FAILED
            throw failure
        }
    }

    override fun checkpoint(): () -> Unit {
        check(state != State.LOADING) { "Cannot checkpoint an active member load" }
        val saved = state
        return { state = saved }
    }

    override fun preparationFacts(): RawPreparationFact = RawPreparationFact.row(
        RawPreparationFact.scalar("builtin-members"), RawPreparationFact.scalar(state.name),
    )
}
