package com.automatelinux.shchuna

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.automatelinux.shchuna.data.ApiException
import com.automatelinux.shchuna.data.StartOutcome
import com.automatelinux.shchuna.data.WhereApi
import com.automatelinux.shchuna.data.model.Where
import com.automatelinux.shchuna.location.LocationSource
import com.automatelinux.shchuna.ui.Confirmation
import com.automatelinux.shchuna.ui.ScreenState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WhereViewModel(app: Application) : AndroidViewModel(app) {
    private val location = LocationSource(app)
    private val api = WhereApi()

    private val _state = MutableStateFlow(ScreenState())
    val state: StateFlow<ScreenState> = _state

    private var current: Location? = null
    /** The point the shown answer is for; a new answer is fetched after moving REQUERY_M. */
    private var answeredFor: Location? = null
    private var tracking: Job? = null
    private var lookup: Job? = null
    private var searchJob: Job? = null
    /** The answer for where the phone is, kept while the user looks at another street. */
    private var liveWhere: Where? = null

    /** Called on start and whenever permission may have changed. */
    fun start() {
        if (!location.hasPermission()) { _state.update { it.copy(needsPermission = true) }; return }
        if (!location.providersEnabled()) { _state.update { it.copy(needsPermission = false, locationOff = true) }; return }
        _state.update { it.copy(needsPermission = false, locationOff = false) }
        if (tracking?.isActive != true) {
            tracking = viewModelScope.launch {
                location.positions().collect { fix ->
                    current = fix
                    _state.update { it.copy(accuracyM = if (fix.hasAccuracy()) fix.accuracy.toInt() else null) }
                    val last = answeredFor
                    if (last == null || last.distanceTo(fix) > REQUERY_M) lookUp(fix)
                }
            }
        }
        refreshSessions()
    }

    fun refresh() {
        if (_state.value.browsing != null) { backToLive(); return }
        val fix = current
        if (fix == null) start() else lookUp(fix)
        refreshSessions()
    }

    private fun lookUp(fix: Location) {
        answeredFor = fix
        lookup?.cancel()
        lookup = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val where = api.where(fix.latitude, fix.longitude)
                liveWhere = where
                // While browsing another street, the live answer waits behind it.
                _state.update { if (it.browsing == null) it.copy(where = where, loading = false) else it.copy(loading = false) }
            } catch (e: ApiException) {
                answeredFor = null
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    private fun refreshSessions() {
        viewModelScope.launch {
            try {
                val sessions = api.sessions()
                _state.update { it.copy(sessions = sessions) }
            } catch (e: ApiException) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    // ── Street search: "which neighbourhood is this street in?" ──

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _state.update { it.copy(results = null, searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(450) // wait for a pause in typing; Nominatim allows one request a second
            _state.update { it.copy(searching = true) }
            try {
                val results = api.street(query.trim())
                _state.update { it.copy(results = results, searching = false) }
            } catch (e: ApiException) {
                _state.update { it.copy(searching = false, results = emptyList(), error = e.message) }
            }
        }
    }

    /** Show everything about a picked place — its neighbourhood, the town, its Cello zone. */
    fun openPlace(label: String, lat: Double, lon: Double) {
        searchJob?.cancel()
        lookup?.cancel()
        lookup = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, results = null, searching = false, browsing = label, parkingMessage = null) }
            try {
                val where = api.where(lat, lon)
                _state.update { it.copy(where = where, loading = false) }
            } catch (e: ApiException) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun backToLive() {
        searchJob?.cancel()
        _state.update { it.copy(browsing = null, query = "", results = null, searching = false, where = liveWhere ?: it.where, parkingMessage = null) }
        current?.let { if (liveWhere == null) lookUp(it) }
    }

    fun startParking(zoneId: Int, confirmationCode: String? = null) {
        val fix = current ?: return
        viewModelScope.launch {
            _state.update { it.copy(parkingBusy = true, parkingMessage = null, confirmation = null) }
            try {
                when (val outcome = api.start(fix.latitude, fix.longitude, if (fix.hasAccuracy()) fix.accuracy.toInt() else null, zoneId, confirmationCode)) {
                    is StartOutcome.Started -> _state.update { it.copy(parkingMessage = outcome.message) }
                    is StartOutcome.Info -> _state.update { it.copy(parkingMessage = outcome.message) }
                    is StartOutcome.NeedsConfirmation -> _state.update {
                        it.copy(confirmation = Confirmation(outcome.zoneId, outcome.message, outcome.code))
                    }
                }
            } catch (e: ApiException) {
                _state.update { it.copy(parkingMessage = e.message) }
            }
            _state.update { it.copy(parkingBusy = false) }
            refreshSessions()
        }
    }

    fun confirmParking(c: Confirmation) = startParking(c.zoneId, c.code)

    fun dismissConfirmation() = _state.update { it.copy(confirmation = null) }

    fun stopParking() {
        viewModelScope.launch {
            _state.update { it.copy(parkingBusy = true, parkingMessage = null) }
            try {
                val message = api.stop(current?.latitude, current?.longitude)
                _state.update { it.copy(parkingMessage = message) }
            } catch (e: ApiException) {
                _state.update { it.copy(parkingMessage = e.message) }
            }
            _state.update { it.copy(parkingBusy = false) }
            refreshSessions()
        }
    }

    private companion object {
        const val REQUERY_M = 30f
    }
}
