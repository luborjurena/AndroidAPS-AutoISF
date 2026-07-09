package app.aaps.ui.activities

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.children
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.aaps.core.data.model.AIV
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.ui.R
import app.aaps.ui.databinding.ActivityAutoisfHistoryBinding
import app.aaps.ui.databinding.AutoisfHistoryItemBinding
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import kotlin.math.abs

/** Table of the AutoISF factors and insulin decisions of the recent loop runs, modeled after the iAPS AutoISF history view. */
class AutoIsfHistoryActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var decimalFormatter: DecimalFormatter
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var fabricPrivacy: FabricPrivacy

    private lateinit var binding: ActivityAutoisfHistoryBinding
    private val disposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAutoisfHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(app.aaps.core.ui.R.drawable.ic_close)
        supportActionBar?.title = rh.gs(R.string.autoisf_history)

        bindHeader(binding.header)
        binding.recyclerview.layoutManager = LinearLayoutManager(this)
    }

    override fun onResume() {
        super.onResume()
        disposable += Single.fromCallable { persistenceLayer.getAutoIsfValuesFromTime(dateUtil.now() - T.days(1).msecs()) }
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe({ values ->
                           val rows = values.filter { (it.glucose ?: 0.0) != 0.0 }.sortedByDescending { it.timestamp }
                           binding.recyclerview.adapter = RecyclerViewAdapter(rows)
                       }, fabricPrivacy::logException)
    }

    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    // Non-localized variable acronyms, same wording as the iAPS history view
    private fun bindHeader(header: AutoisfHistoryItemBinding) {
        header.time.text = rh.gs(app.aaps.core.ui.R.string.time)
        header.bgl.text = "BGL"
        header.finalRatio.text = "Final"
        header.acce.text = "acce"
        header.bg.text = "bg"
        header.pp.text = "pp"
        header.dura.text = "dura"
        header.smb.text = "SMB"
        header.iobTh.text = "iobT"
        header.bgAccel.text = "acce"
        header.delta.text = "Δ"
        header.shortDelta.text = "SΔ"
        header.req.text = "Req"
        header.tbr.text = "TBR"
        header.steps5.text = "S5"
        header.steps15.text = "S15"
        header.steps30.text = "S30"
        header.steps60.text = "S60"
        header.steps180.text = "S180"
        header.root.children.filterIsInstance<TextView>().forEach { it.setTypeface(it.typeface, Typeface.BOLD) }
    }

    /** AutoISF ratios equal to 1.0 did not modify the profile ISF and are displayed as "--" like in iAPS. */
    private fun ratio(value: Double): String =
        if (isNeutral(value)) "--" else decimalFormatter.to2Decimal(value)

    private fun isNeutral(value: Double) = abs(value - 1.0) < 0.005

    private fun insulin(value: Double?): String =
        if (value == null || value == 0.0) "--" else decimalFormatter.to2Decimal(value)

    private fun steps(value: Int?): String =
        if (value == null || value < 0) "--" else value.toString()

    private fun deltaInUnits(value: Double?): String =
        value?.let { profileUtil.fromMgdlToSignedStringInUnits(it) } ?: "--"

    inner class RecyclerViewAdapter(private val list: List<AIV>) : RecyclerView.Adapter<RecyclerViewAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(AutoisfHistoryItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount(): Int = list.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val aiv = list[position]
            with(holder.binding) {
                time.text = dateUtil.timeString(aiv.timestamp)
                bgl.text = profileUtil.fromMgdlToStringInUnits(aiv.glucose)
                finalRatio.text = ratio(aiv.finalIsf)
                acce.text = ratio(aiv.acceIsf)
                bg.text = ratio(aiv.bgIsf)
                pp.text = ratio(aiv.ppIsf)
                dura.text = ratio(aiv.duraIsf)
                smb.text = insulin(aiv.smb)
                iobTh.text = decimalFormatter.to2Decimal(aiv.iobThEffective)
                bgAccel.text = aiv.bgAcceleration?.let { decimalFormatter.to2Decimal(it) } ?: "--"
                delta.text = deltaInUnits(aiv.delta)
                shortDelta.text = deltaInUnits(aiv.shortAvgDelta)
                req.text = insulin(aiv.insulinReq)
                tbr.text = aiv.tbr?.let { decimalFormatter.to2Decimal(it) } ?: "--"
                steps5.text = steps(aiv.steps5)
                steps15.text = steps(aiv.steps15)
                steps30.text = steps(aiv.steps30)
                steps60.text = steps(aiv.steps60)
                steps180.text = steps(aiv.steps180)
                // color the final ratio like the factor it originates from
                finalRatio.setTextColor(rh.gc(finalRatioColor(aiv)))
            }
        }

        private fun finalRatioColor(aiv: AIV): Int =
            when {
                isNeutral(aiv.finalIsf)                     -> app.aaps.core.ui.R.color.finalIsfColor
                abs(aiv.finalIsf - aiv.acceIsf) < 0.005     -> app.aaps.core.ui.R.color.acceIsfColor
                abs(aiv.finalIsf - aiv.bgIsf) < 0.005       -> app.aaps.core.ui.R.color.bgIsfColor
                abs(aiv.finalIsf - aiv.ppIsf) < 0.005       -> app.aaps.core.ui.R.color.ppIsfColor
                abs(aiv.finalIsf - aiv.duraIsf) < 0.005     -> app.aaps.core.ui.R.color.duraIsfColor
                else                                        -> app.aaps.core.ui.R.color.finalIsfColor
            }

        inner class ViewHolder(val binding: AutoisfHistoryItemBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
