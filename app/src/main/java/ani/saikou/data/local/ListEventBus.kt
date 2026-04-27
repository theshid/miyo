package ani.saikou.data.local

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Lightweight event bus for list mutations. Any ViewModel that modifies the user's
 * AniList data emits an event here; consuming ViewModels (Home, UserLists) collect
 * and refresh only when relevant events arrive.
 *
 * Process-lifetime `object` — no DI needed since it's stateless apart from the
 * SharedFlow buffer.
 */
object ListEventBus {

    private val _events = MutableSharedFlow<ListEvent>(
        extraBufferCapacity = 10,  // buffer events even if no one is collecting yet
    )
    val events: SharedFlow<ListEvent> = _events.asSharedFlow()

    suspend fun emit(event: ListEvent) {
        _events.emit(event)
    }

    fun tryEmit(event: ListEvent) {
        _events.tryEmit(event)
    }
}

sealed class ListEvent {
    /** User added/changed/removed an anime or manga from their list. */
    data class ListEntryChanged(val mediaId: Int, val newStatus: String?) : ListEvent()

    /** Episode progress was synced to AniList. */
    data class ProgressUpdated(val mediaId: Int, val episode: Int) : ListEvent()

    /** Chapter progress was synced to AniList. */
    data class ReadingProgressUpdated(val mediaId: Int, val chapter: Int) : ListEvent()

    /** Favorite toggled. */
    data class FavoriteToggled(val mediaId: Int) : ListEvent()
}
