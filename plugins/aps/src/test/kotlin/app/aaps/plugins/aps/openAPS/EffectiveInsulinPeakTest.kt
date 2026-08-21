package app.aaps.plugins.aps.openAPS

import app.aaps.core.data.model.BS
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.Insulin
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

class EffectiveInsulinPeakTest : TestBaseWithProfile() {

    @Mock lateinit var persistenceLayer: PersistenceLayer
    private lateinit var effectiveInsulinPeak: EffectiveInsulinPeak

    @BeforeEach fun prepare() {
        effectiveInsulinPeak = EffectiveInsulinPeak(activePlugin, profileFunction, persistenceLayer, dateUtil)
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
        assertThat(effectiveInsulinPeak()).isEqualTo(75.0)
        // and also without any bolus in the window
        givenBoluses()
        assertThat(effectiveInsulinPeak()).isEqualTo(75.0)
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
        assertThat(effectiveInsulinPeak()).isWithin(0.001).of(98.0)
    }

    @Test
    fun `effective peak ignores primings and falls back to the zero dose peak`() {
        val insulin = dosePeakInsulin()
        whenever(activePlugin.activeInsulin).thenReturn(insulin)
        givenBoluses(BS(timestamp = dateUtil.now(), amount = 2.0, type = BS.Type.PRIMING))
        assertThat(effectiveInsulinPeak()).isWithin(0.001).of(30.0)
    }
}
