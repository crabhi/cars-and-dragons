package cz.crabhi.dragons.app.car

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import cz.crabhi.dragons.app.Czech
import cz.crabhi.dragons.app.DragonsApp
import cz.crabhi.dragons.app.GameUiState
import cz.crabhi.dragons.app.R
import cz.crabhi.dragons.engine.Phase
import cz.crabhi.dragons.engine.StatusSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The only car screen: party status. Display only — the game is played by voice. The list keeps the
 * same title and row count during a game, so updates count as refreshes, not new steps, and stay
 * within the host's driving limits. Updates are throttled to one per second.
 */
class PartyScreen(
    carContext: CarContext,
) : Screen(carContext) {
    private val controller = DragonsApp.controller(carContext)
    private val res = Czech.resources(carContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private var state: GameUiState = controller.state.value
    private var lastInvalidate = 0L
    private var invalidatePending = false

    init {
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onCreate(owner: LifecycleOwner) {
                    scope.launch {
                        controller.state.collect {
                            state = it
                            requestRefresh()
                        }
                    }
                }

                override fun onDestroy(owner: LifecycleOwner) {
                    scope.cancel()
                    handler.removeCallbacksAndMessages(null)
                }
            },
        )
    }

    private fun requestRefresh() {
        if (invalidatePending) return
        val wait = MIN_REFRESH_MS - (SystemClock.uptimeMillis() - lastInvalidate)
        if (wait <= 0) {
            lastInvalidate = SystemClock.uptimeMillis()
            invalidate()
        } else {
            invalidatePending = true
            handler.postDelayed({
                invalidatePending = false
                lastInvalidate = SystemClock.uptimeMillis()
                invalidate()
            }, wait)
        }
    }

    override fun onGetTemplate(): Template {
        val current = state
        val status =
            when (current) {
                is GameUiState.Running -> current.status
                is GameUiState.Finished -> current.status
                GameUiState.Idle -> null
            }
        val builder =
            ListTemplate.Builder()
                .setTitle(status?.title ?: res.getString(R.string.app_name))
                .setHeaderAction(Action.APP_ICON)
        if (status == null || status.rows.isEmpty()) {
            return builder.setLoading(true).build()
        }
        val list = ItemList.Builder()
        status.rows.forEach { list.addItem(partyRow(it)) }
        list.addItem(infoRow(current, status))
        builder.setSingleList(list.build())
        builder.setActionStrip(actions(current))
        return builder.build()
    }

    private fun partyRow(row: StatusSnapshot.Row): Row {
        val hp = res.getQuantityString(R.plurals.hp_count, row.hp.coerceAtLeast(0), row.hp, row.maxHp)
        val title = if (row.current) res.getString(R.string.car_turn_marker, row.label) else row.label
        return Row.Builder()
            .setTitle(title)
            .addText(res.getString(R.string.car_row_detail, hp, row.ac, row.status))
            .build()
    }

    private fun infoRow(
        state: GameUiState,
        status: StatusSnapshot,
    ): Row {
        val title =
            when {
                state is GameUiState.Finished -> res.getString(R.string.car_game_over)
                state is GameUiState.Running && state.paused -> res.getString(R.string.car_paused)
                status.phase == Phase.COMBAT -> res.getString(R.string.car_enemies, status.enemies)
                else -> res.getString(R.string.car_phase_exploration)
            }
        return Row.Builder().setTitle(title).build()
    }

    private fun actions(state: GameUiState): ActionStrip {
        val action =
            when (state) {
                is GameUiState.Running -> {
                    Action.Builder()
                        .setTitle(res.getString(if (state.paused) R.string.car_action_resume else R.string.car_action_pause))
                        .setOnClickListener { controller.togglePause() }
                        .build()
                }

                else -> {
                    Action.Builder()
                        .setTitle(res.getString(R.string.car_action_new_game))
                        .setOnClickListener { controller.start(newGame = state is GameUiState.Finished) }
                        .build()
                }
            }
        return ActionStrip.Builder().addAction(action).build()
    }

    companion object {
        private const val MIN_REFRESH_MS = 1000L
    }
}
