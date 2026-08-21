package app.aaps.plugins.insulin

import app.aaps.core.data.model.BS
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class InsulinLyumjevU100PDPluginTest : TestBase() {

    private lateinit var sut: InsulinLyumjevU100PDPlugin

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var config: Config
    @Mock lateinit var hardLimits: HardLimits
    @Mock lateinit var uiInteraction: UiInteraction

    private val dia = 8.0
    private val time = 1_600_000_000_000L

    private fun bolus(amount: Double, minutesAgo: Double) =
        BS(timestamp = time - Math.round(minutesAgo * 60 * 1000), amount = amount, type = BS.Type.NORMAL)

    @BeforeEach
    fun setup() {
        sut = InsulinLyumjevU100PDPlugin(rh, profileFunction, rxBus, aapsLogger, config, hardLimits, uiInteraction)
    }

    @Test
    fun getIdTest() {
        assertThat(sut.id).isEqualTo(Insulin.InsulinType.OREF_LYUMJEV_U100_PD)
    }

    @Test
    fun getFriendlyNameTest() {
        whenever(rh.gs(eq(R.string.lyumjev_u100_pd))).thenReturn("Lyumjev U100 (Tsunami)")
        assertThat(sut.friendlyName).isEqualTo("Lyumjev U100 (Tsunami)")
    }

    @Test
    fun `nominal peak is the peak of an infinitesimal dose`() {
        assertThat(sut.peak).isEqualTo(61)
        assertThat(sut.peak.toDouble()).isWithin(0.5).of(sut.peakTime(0.0))
    }

    @Test
    fun `peak time grows with the bolus size`() {
        // tp = (61.33 + 12.27 * x) / (1 + 0.05185 * x)
        assertThat(sut.peakTime(0.0)).isWithin(0.01).of(61.33)
        assertThat(sut.peakTime(1.0)).isWithin(0.01).of(69.97)
        assertThat(sut.peakTime(5.0)).isWithin(0.01).of(97.42)
        assertThat(sut.peakTime(10.0)).isWithin(0.01).of(121.19)
        // strictly increasing
        var previous = sut.peakTime(0.0)
        for (i in 1..200) {
            val current = sut.peakTime(i / 10.0)
            assertThat(current).isGreaterThan(previous)
            previous = current
        }
    }

    @Test
    fun `activity peaks at the calculated peak time`() {
        for (amount in listOf(0.5, 1.0, 5.0, 12.0)) {
            val expectedPeak = sut.peakTime(amount)
            var bestMinute = 0.0
            var bestActivity = 0.0
            var minute = 0.0
            while (minute <= 480.0) {
                val activity = sut.iobCalcForTreatment(bolus(amount, minute), time, dia).activityContrib
                if (activity > bestActivity) {
                    bestActivity = activity
                    bestMinute = minute
                }
                minute += 0.5
            }
            assertThat(bestMinute).isWithin(0.5).of(expectedPeak)
        }
    }

    @Test
    fun `iob starts at the bolus size and is zero after end of action`() {
        assertThat(sut.iobCalcForTreatment(bolus(3.0, 0.0), time, dia).iobContrib).isWithin(0.001).of(3.0)
        // monotonically decreasing
        var previous = 3.0
        var minute = 5.0
        while (minute < 480.0) {
            val iob = sut.iobCalcForTreatment(bolus(3.0, minute), time, dia).iobContrib
            assertThat(iob).isLessThan(previous)
            previous = iob
            minute += 5.0
        }
        assertThat(sut.iobCalcForTreatment(bolus(3.0, 480.0), time, dia).iobContrib).isEqualTo(0.0)
        assertThat(sut.iobCalcForTreatment(bolus(3.0, 480.0), time, dia).activityContrib).isEqualTo(0.0)
        // past the end of action nothing is left, not even with a DIA longer than the model
        assertThat(sut.iobCalcForTreatment(bolus(3.0, 540.0), time, 10.0).iobContrib).isEqualTo(0.0)
    }

    @Test
    fun `activity integrates to the bolus size`() {
        val amount = 4.0
        var sum = 0.0
        var minute = 0.0
        val step = 0.1
        while (minute < 480.0) {
            sum += sut.iobCalcForTreatment(bolus(amount, minute + step / 2), time, dia).activityContrib * step
            minute += step
        }
        assertThat(sum).isWithin(0.01).of(amount)
    }

    @Test
    fun `activity is the negative derivative of iob`() {
        val amount = 2.5
        val step = 0.01
        for (minute in listOf(10.0, 45.0, 90.0, 180.0, 300.0)) {
            val before = sut.iobCalcForTreatment(bolus(amount, minute - step), time, dia).iobContrib
            val after = sut.iobCalcForTreatment(bolus(amount, minute + step), time, dia).iobContrib
            val derivative = (before - after) / (2 * step)
            val activity = sut.iobCalcForTreatment(bolus(amount, minute), time, dia).activityContrib
            assertThat(activity).isWithin(1e-6).of(derivative)
        }
    }

    @Test
    fun `zero bolus contributes nothing`() {
        val iob = sut.iobCalcForTreatment(bolus(0.0, 30.0), time, dia)
        assertThat(iob.iobContrib).isEqualTo(0.0)
        assertThat(iob.activityContrib).isEqualTo(0.0)
    }

    @Test
    fun `dia is fixed at 8 h by the model regardless of the profile`() {
        assertThat(sut.glucodynamic).isTrue()
        assertThat(sut.userDefinedDia).isEqualTo(8.0)
        // the model duration is above every hard limit minimum, so dia and iCfg carry it unchanged
        whenever(rh.gs(eq(R.string.lyumjev_u100_pd))).thenReturn("Lyumjev U100 (Tsunami)")
        whenever(hardLimits.minDia()).thenReturn(5.0)
        assertThat(sut.dia).isEqualTo(8.0)
        assertThat(sut.iCfg.insulinEndTime).isEqualTo(8 * 3600 * 1000L)
        // the profile DIA is not consulted
        verify(profileFunction, never()).getProfile()
    }

    @Test
    fun `notifies when dia is shorter than the model`() {
        whenever(rh.gs(eq(R.string.dia_shorter_than_insulin_model), anyString(), any(), any())).thenReturn("short dia")
        whenever(rh.gs(eq(R.string.lyumjev_u100_pd))).thenReturn("Lyumjev U100 (Tsunami)")
        sut.iobCalcForTreatment(bolus(1.0, 30.0), time, 5.0)
        verify(uiInteraction).addNotification(anyInt(), anyString(), anyInt())
        // rate limited, a second calculation must not raise another one
        sut.iobCalcForTreatment(bolus(1.0, 30.0), time, 5.0)
        verify(uiInteraction).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun `does not notify when dia covers the model`() {
        sut.iobCalcForTreatment(bolus(1.0, 30.0), time, 8.0)
        verify(uiInteraction, never()).addNotification(anyInt(), anyString(), anyInt())
    }
}
