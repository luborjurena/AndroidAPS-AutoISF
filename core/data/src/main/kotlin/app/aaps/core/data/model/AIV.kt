package app.aaps.core.data.model

import java.util.TimeZone

/** AutoISF key values for plotting in subgraph. */
data class AIV(
    override var id: Long = 0,
    /** Milliseconds since the epoch. End of the sampling period, i.e. the value is
     *  sampled from timestamp-duration to timestamp. */
    var timestamp: Long,
    var acceIsf: Double,
    var bgIsf: Double,
    var ppIsf: Double,
    var driftIsf: Double,       // place bolder
    var duraIsf: Double,
    var finalIsf: Double,
    var iobThEffective: Double,
    /** BG at calculation time in mg/dl. Null or 0.0 means "no data", the record is skipped in the history table. */
    var glucose: Double? = null,
    /** Parabola derived BG acceleration in mg/dl per (5 min)². */
    var bgAcceleration: Double? = null,
    /** BG delta in mg/dl. */
    var delta: Double? = null,
    /** BG short average delta in mg/dl. */
    var shortAvgDelta: Double? = null,
    /** Suggested SMB in U. */
    var smb: Double? = null,
    /** Variable SMB delivery ratio (fraction of required insulin delivered as SMB). */
    var smbRatio: Double? = null,
    /** Required insulin in U. */
    var insulinReq: Double? = null,
    /** Suggested temp basal rate in U/h, null if no change was requested. */
    var tbr: Double? = null,
    var steps5: Int? = null,
    var steps15: Int? = null,
    var steps30: Int? = null,
    var steps60: Int? = null,
    var steps180: Int? = null,
    var utcOffset: Long = TimeZone.getDefault().getOffset(timestamp).toLong(),
    override var version: Int = 0,
    override var dateCreated: Long = -1,
    override var isValid: Boolean = true,
    override var referenceId: Long? = null,
    override var ids: IDs = IDs()
) : HasIDs {

    fun contentEqualsTo(other: AIV): Boolean {
        return this === other || (
            timestamp == other.timestamp &&
                acceIsf == other.acceIsf &&
                bgIsf == other.bgIsf &&
                ppIsf == other.ppIsf &&
                driftIsf == other.driftIsf &&
                driftIsf == other.driftIsf &&
                duraIsf == other.duraIsf &&
                finalIsf == other.finalIsf &&
                iobThEffective == other.iobThEffective &&
                isValid == other.isValid)
    }
}