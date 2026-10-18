package cz.crabhi.dragons.app.car

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import cz.crabhi.dragons.app.DragonsApp
import cz.crabhi.dragons.app.Settings

/**
 * One Android Auto connection. Starts (or resumes) the game as soon as the app opens on the car
 * screen, so nothing needs to be touched while driving. The game is saved after every action, so
 * disconnecting the car simply stops it.
 */
class DragonsSession : Session() {
    private val controller get() = DragonsApp.controller(carContext)

    override fun onCreateScreen(intent: Intent): Screen {
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    controller.carMic = null
                    controller.stop()
                }
            },
        )
        val screen = PartyScreen(carContext)
        if (carContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startGame()
        } else {
            carContext.requestPermissions(listOf(Manifest.permission.RECORD_AUDIO)) { granted, _ ->
                if (Manifest.permission.RECORD_AUDIO in granted) startGame()
            }
        }
        return screen
    }

    private fun startGame() {
        val useCarMic =
            Settings(carContext).useCarMic &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                carContext.carAppApiLevel >= CAR_MIC_API_LEVEL
        controller.carMic =
            if (useCarMic) {
                {
                    try {
                        @Suppress("MissingPermission")
                        CarMicStream(carContext)
                    } catch (e: Exception) {
                        Log.w(TAG, "Car microphone unavailable, using the phone microphone", e)
                        null
                    }
                }
            } else {
                null
            }
        controller.start()
    }

    companion object {
        private const val TAG = "DragonsSession"
        private const val CAR_MIC_API_LEVEL = 5
    }
}
