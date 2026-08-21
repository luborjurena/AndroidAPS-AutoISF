package app.aaps.core.interfaces.insulin

import app.aaps.core.data.iob.Iob
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.configuration.ConfigExportImport

interface Insulin : ConfigExportImport {

    enum class InsulinType(val value: Int) {
        UNKNOWN(-1),

        // int FAST_ACTING_INSULIN = 0; // old model no longer available
        // int FAST_ACTING_INSULIN_PROLONGED = 1; // old model no longer available
        OREF_RAPID_ACTING(2),
        OREF_ULTRA_RAPID_ACTING(3),
        OREF_FREE_PEAK(4),
        OREF_LYUMJEV(5),

        // Glucodynamic (PD) models ported from https://github.com/piecycle/tsunami
        // Ids are kept in sync with upstream so profiles stay interchangeable
        OREF_LYUMJEV_U100_PD(105),
        OREF_LYUMJEV_U200_PD(205);

        companion object {

            private val map = entries.associateBy(InsulinType::value)
            fun fromInt(type: Int) = map[type]
        }
    }

    val id: InsulinType
    val friendlyName: String
    val comment: String
    val dia: Double

    /**
     * True for the glucodynamic (PD) models. They describe the glucose lowering effect directly,
     * define their own end of action ([dia] is fixed by the model, the profile DIA is ignored)
     * and derive the peak time of each bolus from its size, see [peakTime].
     */
    val glucodynamic: Boolean get() = false

    /**
     * Nominal peak time [min] of the insulin activity curve.
     *
     * Single value describing the model, used where no bolus is in context (profile / [iCfg],
     * Autotune, Nightscout). For models with a dose dependent peak time this is only a
     * representative value, use [peakTime] whenever the bolus size is known.
     */
    val peak: Int

    /**
     * Peak time [min] of the activity curve of a bolus of [bolusAmount] U.
     *
     * Constant for the pharmacokinetic (oref) models, dose dependent for the glucodynamic
     * (PD) models where a bigger bolus forms a bigger subcutaneous depot and peaks later.
     */
    fun peakTime(bolusAmount: Double): Double = peak.toDouble()

    fun iobCalcForTreatment(bolus: BS, time: Long, dia: Double): Iob

    val iCfg: ICfg
}