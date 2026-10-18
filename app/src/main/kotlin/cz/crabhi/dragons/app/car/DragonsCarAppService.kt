package cz.crabhi.dragons.app.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator

/**
 * Android Auto entry point. Declared under the IOT category: unlike GAMES, IOT apps are not
 * parked-only, so the host keeps the app running while driving (see docs/STATUS.md).
 */
class DragonsCarAppService : CarAppService() {
    // Personal sideloaded app: accept any host (Android Auto, the Desktop Head Unit).
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(sessionInfo: SessionInfo): Session = DragonsSession()
}
