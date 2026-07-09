package app.aaps.database.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.aaps.database.entities.embedments.InterfaceIDs
import app.aaps.database.entities.interfaces.DBEntryWithTime
import app.aaps.database.entities.interfaces.TraceableDBEntry
import java.util.TimeZone

/** AutoISF key values for plotting in subgraph. */
@Entity(
    tableName = TABLE_AUTOISF_VALUES,
    indices = [Index("id"), Index("timestamp")]
)
data class AutoIsfValues(
    @PrimaryKey(autoGenerate = true)
    override var id: Long = 0,
    /** Milliseconds since the epoch. End of the sampling period, i.e. the value is
     *  sampled from timestamp-duration to timestamp. */
    override var timestamp: Long,
    var acceIsf: Double,
    var bgIsf: Double,
    var ppIsf: Double,
    val driftIsf: Double,       // place holder
    val duraIsf: Double,
    var finalIsf: Double,
    var iobThEffective: Double,
    /** BG at calculation time in mg/dl. Null or 0.0 means "no data". */
    var glucose: Double? = null,
    /** Parabola derived BG acceleration in mg/dl per (5 min)². */
    var bgAcceleration: Double? = null,
    /** BG delta in mg/dl. */
    var delta: Double? = null,
    /** BG short average delta in mg/dl. */
    var shortAvgDelta: Double? = null,
    /** Suggested SMB in U. */
    var smb: Double? = null,
    /** Required insulin in U. */
    var insulinReq: Double? = null,
    /** Suggested temp basal rate in U/h, null if no change was requested. */
    var tbr: Double? = null,
    var steps5: Int? = null,
    var steps15: Int? = null,
    var steps30: Int? = null,
    var steps60: Int? = null,
    var steps180: Int? = null,
    override var utcOffset: Long = TimeZone.getDefault().getOffset(timestamp).toLong(),
    override var version: Int = 0,
    override var dateCreated: Long = -1,
    override var isValid: Boolean = true,
    override var referenceId: Long? = null,
    @Embedded
    override var interfaceIDs_backing: InterfaceIDs? = null,
) : TraceableDBEntry, DBEntryWithTime