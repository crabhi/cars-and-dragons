package cz.crabhi.dragons.app

import android.app.Application
import android.content.Context

class DragonsApp : Application() {
    val controller: GameController by lazy { GameController(this) }

    companion object {
        fun controller(context: Context): GameController = (context.applicationContext as DragonsApp).controller
    }
}
