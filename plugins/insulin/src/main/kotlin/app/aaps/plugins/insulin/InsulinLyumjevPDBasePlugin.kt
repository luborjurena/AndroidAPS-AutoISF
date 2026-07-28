package app.aaps.plugins.insulin

import app.aaps.core.data.iob.Iob
import app.aaps.core.data.model.BS
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.HardLimits
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Glucodynamic (pharmacodynamic) insulin model, ported from the Tsunami branch of AndroidAPS
 * (https://github.com/piecycle/tsunami, branch dev).
 *
 * Unlike the oref models, which describe how the insulin gets into the blood (pharmacokinetics)
 * and derive the effect from that, this model describes the measured glucose lowering effect
 * (pharmacodynamics) directly. Its activity curve is
 *
 *     activity(t) = 2 * D / T * t * exp(-t^2 / T)     with T = 2 * tp^2
 *
 * which peaks exactly at t = tp and integrates to the bolus size D over its whole duration, and
 * its integral from t to the end of action gives the insulin on board
 *
 *     iob(t) = D * (exp(-t^2 / T) - exp(-END_OF_ACTION^2 / T))
 *
 * The peak time tp is not a constant here: a bigger bolus forms a bigger subcutaneous depot which
 * takes longer to be absorbed, so tp grows with the bolus size, see [peakTime].
 */
abstract class InsulinLyumjevPDBasePlugin(
    rh: ResourceHelper,
    profileFunction: ProfileFunction,
    rxBus: RxBus,
    aapsLogger: AAPSLogger,
    config: Config,
    hardLimits: HardLimits,
    uiInteraction: UiInteraction
) : InsulinOrefBasePlugin(rh, profileFunction, rxBus, aapsLogger, config, hardLimits, uiInteraction) {

    /**
     * Concentration of the insulin relative to U100.
     *
     * The peak time model is driven by the volume of the depot, so a U200 bolus of x U behaves
     * like a U100 bolus of 2 * x U. The activity curve itself is not scaled: with the peak time
     * already accounting for the concentration, doubling only the dose does not change the shape
     * of the curve.
     */
    protected abstract val concentrationFactor: Double

    private var lastDiaWarning: Long = 0

    /**
     * Nominal peak [min], reported where no bolus size is in context (profile, Nightscout,
     * Autotune). This model has no single peak, so report its lower bound, the peak time of an
     * infinitesimal dose. Use [peakTime] wherever the bolus size is known.
     */
    override val peak get() = PEAK_A0.roundToInt()

    /**
     * Peak time [min] of a bolus of [bolusAmount] U, fitted from clinical data as a rational
     * function of the delivered volume: tp = (a0 + a1 * x) / (1 + b1 * x).
     */
    override fun peakTime(bolusAmount: Double): Double {
        val dose = concentrationFactor * bolusAmount
        return (PEAK_A0 + PEAK_A1 * dose) / (1 + PEAK_B1 * dose)
    }

    override fun iobCalcForTreatment(bolus: BS, time: Long, dia: Double): Iob {
        val result = Iob()
        if (bolus.amount == 0.0) return result
        val t = (time - bolus.timestamp) / 1000.0 / 60.0
        // force IOB and activity to 0 once the model has run out
        if (t >= END_OF_ACTION) return result
        warnIfDiaShorterThanModel(dia)
        // the model is parametrized with T = 2 * tp^2 rather than with the peak time itself
        val tModel = 2 * peakTime(bolus.amount).pow(2.0)
        result.activityContrib = (2 * bolus.amount / tModel) * t * exp(-t.pow(2.0) / tModel)
        result.iobContrib = bolus.amount * (exp(-t.pow(2.0) / tModel) - exp(-END_OF_ACTION.pow(2.0) / tModel))
        return result
    }

    /**
     * IOB is only calculated over the DIA of the running profile, so with a DIA shorter than the
     * end of action of this model boluses are dropped while they still carry insulin on board.
     */
    private fun warnIfDiaShorterThanModel(dia: Double) {
        if (dia * 60 >= END_OF_ACTION) return
        val now = System.currentTimeMillis()
        if (now - lastDiaWarning < T.hours(1).msecs()) return
        lastDiaWarning = now
        uiInteraction.addNotification(
            Notification.DIA_SHORTER_THAN_INSULIN_MODEL,
            rh.gs(R.string.dia_shorter_than_insulin_model, friendlyName, dia, END_OF_ACTION / 60.0),
            Notification.NORMAL
        )
    }

    companion object {

        /** End of insulin action [min]. Fixed by the model, the remaining IOB there is < 0.1 %. */
        const val END_OF_ACTION = 8.0 * 60

        // Coefficients of the peak time model, tp [min] over the dose [U]
        private const val PEAK_A0 = 61.33
        private const val PEAK_A1 = 12.27
        private const val PEAK_B1 = 0.05185
    }
}
