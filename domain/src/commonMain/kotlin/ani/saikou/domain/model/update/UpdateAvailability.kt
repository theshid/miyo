package ani.saikou.domain.model.update

/**
 * Output of [`UpdateDecisionEngine`]. Mandatory is a derived flag — set when
 * the manifest declares it OR when the installed version is below the
 * manifest's minimumSupportedVersion.
 */
sealed interface UpdateAvailability {
    data object UpToDate : UpdateAvailability

    data class Available(
        val manifest: UpdateManifest,
        val mandatory: Boolean,
    ) : UpdateAvailability
}
