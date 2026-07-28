package app.aaps.plugins.aps.openAPSSMB

import app.aaps.core.data.model.BS
import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.profiling.Profiler
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyDouble
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mock
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class OpenAPSSMBPluginTest : TestBaseWithProfile() {

    @Mock lateinit var constraintChecker: ConstraintsChecker
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Mock lateinit var determineBasalSMB: DetermineBasalSMB
    @Mock lateinit var bgQualityCheck: BgQualityCheck
    @Mock lateinit var tddCalculator: TddCalculator
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var profiler: Profiler
    private lateinit var openAPSSMBPlugin: OpenAPSSMBPlugin

    @BeforeEach fun prepare() {
        openAPSSMBPlugin = OpenAPSSMBPlugin(
            aapsLogger, rxBus, constraintChecker, rh, profileFunction, profileUtil, config, activePlugin,
            iobCobCalculator, hardLimits, preferences, dateUtil, processedTbrEbData, persistenceLayer, glucoseStatusProvider,
            tddCalculator, bgQualityCheck, uiInteraction, determineBasalSMB, profiler, GlucoseStatusCalculatorSMB(aapsLogger, iobCobCalculator, dateUtil, decimalFormatter, deltaCalculator), apsResultProvider
        )
    }

    @Test
    fun specialEnableConditionTest() {
        assertThat(openAPSSMBPlugin.specialEnableCondition()).isTrue()
    }

    @Test
    fun specialShowInListConditionTest() {
        assertThat(openAPSSMBPlugin.specialShowInListCondition()).isTrue()
    }

    @Test
    fun preferenceScreenTest() {
        val screen = preferenceManager.createPreferenceScreen(context)
        openAPSSMBPlugin.addPreferenceScreen(preferenceManager, screen, context, null)
        assertThat(screen.preferenceCount).isGreaterThan(0)
    }

    /** Insulin with a constant peak time, like all the oref models. */
    private fun constantPeakInsulin(constantPeak: Int) = mock<Insulin>().also {
        whenever(it.peak).thenReturn(constantPeak)
        whenever(it.peakTime(anyDouble())).thenReturn(constantPeak.toDouble())
    }

    /** Insulin with a dose dependent peak time, like the glucodynamic models. */
    private fun dosePeakInsulin() = mock<Insulin>().also {
        whenever(it.peak).thenReturn(45)
        whenever(it.peakTime(anyDouble())).thenAnswer { invocation ->
            val amount = invocation.getArgument<Double>(0)
            30.0 + 20.0 * amount
        }
    }

    private fun givenBoluses(vararg boluses: BS) {
        whenever(profileFunction.getProfile()).thenReturn(validProfile)
        whenever(persistenceLayer.getBolusesFromTimeToTime(anyLong(), anyLong(), anyBoolean())).thenReturn(boluses.toList())
    }

    @Test
    fun `effective peak of a constant peak insulin is its peak`() {
        val insulin = constantPeakInsulin(75)
        whenever(activePlugin.activeInsulin).thenReturn(insulin)
        givenBoluses(BS(timestamp = dateUtil.now(), amount = 1.0, type = BS.Type.NORMAL))
        assertThat(openAPSSMBPlugin.effectiveInsulinPeak()).isEqualTo(75.0)
        // and also without any bolus in the window
        givenBoluses()
        assertThat(openAPSSMBPlugin.effectiveInsulinPeak()).isEqualTo(75.0)
    }

    @Test
    fun `effective peak of a dose dependent insulin is weighted by the bolus size`() {
        val insulin = dosePeakInsulin()
        whenever(activePlugin.activeInsulin).thenReturn(insulin)
        val now = dateUtil.now()
        // 1 U peaks at 50, 4 U peaks at 110, weighted: (1 * 50 + 4 * 110) / 5 = 98
        givenBoluses(
            BS(timestamp = now, amount = 1.0, type = BS.Type.NORMAL),
            BS(timestamp = now, amount = 4.0, type = BS.Type.NORMAL)
        )
        assertThat(openAPSSMBPlugin.effectiveInsulinPeak()).isWithin(0.001).of(98.0)
    }

    @Test
    fun `effective peak ignores primings and falls back to the zero dose peak`() {
        val insulin = dosePeakInsulin()
        whenever(activePlugin.activeInsulin).thenReturn(insulin)
        givenBoluses(BS(timestamp = dateUtil.now(), amount = 2.0, type = BS.Type.PRIMING))
        assertThat(openAPSSMBPlugin.effectiveInsulinPeak()).isWithin(0.001).of(30.0)
    }
}
