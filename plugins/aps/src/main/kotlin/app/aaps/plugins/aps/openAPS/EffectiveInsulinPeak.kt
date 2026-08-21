package app.aaps.plugins.aps.openAPS

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.BS
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.utils.DateUtil
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Peak time [min] of the insulin that is currently on board.
 *
 * Glucodynamic models derive the peak time from the size of each single bolus, so no single
 * number describes them. Average the peak times of the boluses inside the DIA window, weighted
 * by their size, which is what the IOB is made of. Models with a constant peak time return that
 * peak for every dose, so for them this is the plain peak of the plugin.
 */
@Singleton
class EffectiveInsulinPeak @Inject constructor(
    private val activePlugin: ActivePlugin,
    private val profileFunction: ProfileFunction,
    private val persistenceLayer: PersistenceLayer,
    private val dateUtil: DateUtil
) {

    operator fun invoke(): Double {
        val insulin = activePlugin.activeInsulin
        val dia = profileFunction.getProfile()?.dia ?: Constants.defaultDIA
        val now = dateUtil.now()
        val boluses = persistenceLayer
            .getBolusesFromTimeToTime(now - T.mins((dia * 60).toLong()).msecs(), now, true)
            .filter { it.type != BS.Type.PRIMING && it.amount > 0.0 }
        val amount = boluses.sumOf { it.amount }
        // nothing delivered inside the DIA window, take the peak of an infinitesimal dose
        if (amount <= 0.0) return insulin.peakTime(0.0)
        return boluses.sumOf { it.amount * insulin.peakTime(it.amount) } / amount
    }
}
