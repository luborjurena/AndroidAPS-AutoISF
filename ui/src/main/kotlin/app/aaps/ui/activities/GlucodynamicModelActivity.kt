package app.aaps.ui.activities

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.children
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.aaps.core.data.model.BS
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.ui.R
import app.aaps.ui.databinding.ActivityGlucodynamicModelBinding
import app.aaps.ui.databinding.GlucodynamicModelItemBinding
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Debug table for the glucodynamic insulin model, modeled after the AutoISF history view.
 *
 * Lists every bolus inside the DIA window with the values the active insulin model calculates
 * for it right now (dose dependent peak time, activity, remaining IOB), so the model can be
 * verified live against the overview IOB and the expected curve. The header shows the model DIA
 * next to the profile DIA and the bolus size weighted peak time the algorithm derives from it.
 */
class GlucodynamicModelActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var decimalFormatter: DecimalFormatter
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var fabricPrivacy: FabricPrivacy

    private lateinit var binding: ActivityGlucodynamicModelBinding
    private val disposable = CompositeDisposable()
    private var defaultDiaColor = 0

    /** One bolus with the values the insulin model reports for it at [now]. */
    private data class ModelRow(
        val timestamp: Long,
        val amount: Double,
        val peakTime: Double,
        val elapsedMinutes: Long,
        val activity: Double,
        val iob: Double
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGlucodynamicModelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(app.aaps.core.ui.R.drawable.ic_close)
        supportActionBar?.title = rh.gs(R.string.glucodynamic_model)

        bindHeader(binding.header)
        defaultDiaColor = binding.modelDia.currentTextColor
        binding.recyclerview.layoutManager = LinearLayoutManager(this)
    }

    override fun onResume() {
        super.onResume()
        val insulin = activePlugin.activeInsulin
        val insulinDia = insulin.dia
        val profileDia = profileFunction.getProfile()?.dia

        binding.modelName.text = rh.gs(
            if (insulin.glucodynamic) R.string.glucodynamic_model_insulin else R.string.glucodynamic_model_insulin_not_glucodynamic,
            insulin.friendlyName
        )
        binding.modelDia.text = rh.gs(R.string.glucodynamic_model_dia, insulinDia, profileDia ?: 0.0)
        // a profile switch created before the model was enabled still carries the old DIA and cuts the IOB window short
        binding.modelDia.setTextColor(
            if (profileDia != null && profileDia < insulinDia) rh.gc(app.aaps.core.ui.R.color.warning)
            else defaultDiaColor
        )

        disposable += Single.fromCallable {
            val now = dateUtil.now()
            persistenceLayer
                .getBolusesFromTimeToTime(now - T.mins((insulinDia * 60).toLong()).msecs(), now, true)
                .filter { it.type != BS.Type.PRIMING && it.amount > 0.0 }
                .map { bolus ->
                    val iob = insulin.iobCalcForTreatment(bolus, now, profileDia ?: insulinDia)
                    ModelRow(
                        timestamp = bolus.timestamp,
                        amount = bolus.amount,
                        peakTime = insulin.peakTime(bolus.amount),
                        elapsedMinutes = (now - bolus.timestamp) / 60000,
                        activity = iob.activityContrib,
                        iob = iob.iobContrib
                    )
                }
                .sortedByDescending { it.timestamp }
        }
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe({ rows ->
                           bindSummary(rows, insulin.peak)
                           binding.recyclerview.adapter = RecyclerViewAdapter(rows)
                       }, fabricPrivacy::logException)
    }

    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    private fun bindSummary(rows: List<ModelRow>, nominalPeak: Int) {
        // same bolus size weighted mean the algorithm feeds into dynISF and the prediction peak
        val amount = rows.sumOf { it.amount }
        val effectivePeak = if (amount > 0.0) rows.sumOf { it.amount * it.peakTime } / amount else nominalPeak.toDouble()
        binding.modelPeak.text = rh.gs(R.string.glucodynamic_model_peak, nominalPeak, effectivePeak)
        binding.modelTotals.text = rh.gs(R.string.glucodynamic_model_totals, rows.sumOf { it.iob }, rows.sumOf { it.activity })
    }

    // Non-localized variable acronyms, same convention as the AutoISF history view
    private fun bindHeader(header: GlucodynamicModelItemBinding) {
        header.time.text = rh.gs(app.aaps.core.ui.R.string.time)
        header.amount.text = "U"
        header.peakMinutes.text = "tp"
        header.peakAt.text = "Peak"
        header.elapsed.text = "min"
        header.activity.text = "Act"
        header.iob.text = "IOB"
        header.root.children.filterIsInstance<TextView>().forEach { it.setTypeface(it.typeface, Typeface.BOLD) }
    }

    private inner class RecyclerViewAdapter(private val list: List<ModelRow>) : RecyclerView.Adapter<RecyclerViewAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(GlucodynamicModelItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount(): Int = list.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val row = list[position]
            with(holder.binding) {
                time.text = dateUtil.timeString(row.timestamp)
                amount.text = decimalFormatter.to2Decimal(row.amount)
                peakMinutes.text = row.peakTime.roundToInt().toString()
                peakAt.text = dateUtil.timeString(row.timestamp + T.mins(row.peakTime.toLong()).msecs())
                elapsed.text = row.elapsedMinutes.toString()
                activity.text = decimalFormatter.to3Decimal(row.activity)
                iob.text = decimalFormatter.to2Decimal(row.iob)
            }
        }

        inner class ViewHolder(val binding: GlucodynamicModelItemBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
