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
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.kotlin.whenever

class InsulinLyumjevU200PDPluginTest : TestBase() {

    private lateinit var sut: InsulinLyumjevU200PDPlugin
    private lateinit var u100: InsulinLyumjevU100PDPlugin

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
        sut = InsulinLyumjevU200PDPlugin(rh, profileFunction, rxBus, aapsLogger, config, hardLimits, uiInteraction)
        u100 = InsulinLyumjevU100PDPlugin(rh, profileFunction, rxBus, aapsLogger, config, hardLimits, uiInteraction)
    }

    @Test
    fun getIdTest() {
        assertThat(sut.id).isEqualTo(Insulin.InsulinType.OREF_LYUMJEV_U200_PD)
    }

    @Test
    fun getFriendlyNameTest() {
        whenever(rh.gs(eq(R.string.lyumjev_u200_pd))).thenReturn("Lyumjev U200 (Tsunami)")
        assertThat(sut.friendlyName).isEqualTo("Lyumjev U200 (Tsunami)")
    }

    @Test
    fun `peak time of x units matches U100 with the doubled volume`() {
        for (amount in listOf(0.0, 0.5, 1.0, 3.0, 10.0))
            assertThat(sut.peakTime(amount)).isWithin(1e-9).of(u100.peakTime(2 * amount))
    }

    @Test
    fun `iob is scaled by the delivered units, not by the concentration`() {
        // The activity curve is not scaled for U200, the concentration only enters the peak time.
        // The displayed IOB is therefore the delivered volume, half of the actual units.
        assertThat(sut.iobCalcForTreatment(bolus(2.0, 0.0), time, dia).iobContrib).isWithin(0.001).of(2.0)
        assertThat(sut.iobCalcForTreatment(bolus(2.0, 480.0), time, dia).iobContrib).isEqualTo(0.0)
    }

    @Test
    fun `activity peaks later than U100 for the same number of units`() {
        val amount = 3.0
        assertThat(sut.peakTime(amount)).isGreaterThan(u100.peakTime(amount))
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
}
