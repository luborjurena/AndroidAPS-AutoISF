package app.aaps.database.persistence.converters

import app.aaps.core.data.model.AIV
import app.aaps.database.entities.AutoIsfValues

fun AutoIsfValues.fromDb(): AIV =
    AIV(
        id = this.id,
        version = this.version,
        dateCreated = this.dateCreated,
        isValid = this.isValid,
        referenceId = this.referenceId,
        timestamp = this.timestamp,
        utcOffset = this.utcOffset,
        acceIsf = this.acceIsf,
        bgIsf = this.bgIsf,
        ppIsf = this.ppIsf,
        driftIsf = this.driftIsf,
        duraIsf = this.duraIsf,
        finalIsf = this.finalIsf,
        iobThEffective = this.iobThEffective,
        glucose = this.glucose,
        bgAcceleration = this.bgAcceleration,
        delta = this.delta,
        shortAvgDelta = this.shortAvgDelta,
        smb = this.smb,
        insulinReq = this.insulinReq,
        tbr = this.tbr,
        steps5 = this.steps5,
        steps15 = this.steps15,
        steps30 = this.steps30,
        steps60 = this.steps60,
        steps180 = this.steps180,
        ids = this.interfaceIDs.fromDb()
    )

fun AIV.toDb(): AutoIsfValues =
    AutoIsfValues(
        id = this.id,
        version = this.version,
        dateCreated = this.dateCreated,
        isValid = this.isValid,
        referenceId = this.referenceId,
        timestamp = this.timestamp,
        utcOffset = this.utcOffset,
        acceIsf = this.acceIsf,
        bgIsf = this.bgIsf,
        ppIsf = this.ppIsf,
        driftIsf = this.driftIsf,
        duraIsf = this.duraIsf,
        finalIsf = this.finalIsf,
        iobThEffective = this.iobThEffective,
        glucose = this.glucose,
        bgAcceleration = this.bgAcceleration,
        delta = this.delta,
        shortAvgDelta = this.shortAvgDelta,
        smb = this.smb,
        insulinReq = this.insulinReq,
        tbr = this.tbr,
        steps5 = this.steps5,
        steps15 = this.steps15,
        steps30 = this.steps30,
        steps60 = this.steps60,
        steps180 = this.steps180,
        interfaceIDs_backing = this.ids.toDb()
    )
