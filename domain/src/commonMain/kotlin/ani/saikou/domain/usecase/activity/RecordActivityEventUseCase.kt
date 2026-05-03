package ani.saikou.domain.usecase.activity

import ani.saikou.domain.model.ActivityEvent
import ani.saikou.domain.repository.ActivityRepository

class RecordActivityEventUseCase(
    private val repository: ActivityRepository,
) {
    suspend operator fun invoke(event: ActivityEvent) {
        repository.recordEvent(event)
    }
}
