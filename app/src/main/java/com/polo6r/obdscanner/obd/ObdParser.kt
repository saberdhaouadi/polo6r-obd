package com.polo6r.obdscanner.obd

data class Monitor(val name: String, val supported: Boolean, val complete: Boolean)

data class Readiness(
    val milOn: Boolean,
    val dtcCount: Int,
    val compressionIgnition: Boolean,
    val monitors: List<Monitor>
)

object ObdParser {

    private val MULTI = Regex("^[0-9A-F]:([0-9A-F]+)$")
    private val HEX = Regex("^[0-9A-F]+$")
    private val HEX3 = Regex("^[0-9A-F]{3}$")
    private val SEARCHING = Regex("SEARCHING\\.*", RegexOption.IGNORE_CASE)

    private val HARD_ERRORS = listOf(
        "UNABLETOCONNECT", "CANERROR", "BUSERROR", "BUSBUSY", "BUFFERFULL",
        "DATAERROR", "FBERROR", "STOPPED", "ERROR"
    )

    /** Strips '>', SEARCHING..., NULs, whitespace; upper-cases; returns non-empty lines without spaces. */
    fun clean(raw: String): List<String> {
        val stripped = raw.replace(">", "").replace("\u0000", "").replace(SEARCHING, "")
        return stripped.split('\r', '\n')
            .map { it.replace(" ", "").replace("\t", "").trim().uppercase() }
            .filter { it.isNotEmpty() && !(it.startsWith("BUSINIT") && !it.contains("ERROR")) }
    }

    fun isNoData(raw: String): Boolean = clean(raw).any { it.contains("NODATA") }

    /** Returns a human-readable error if the adapter reported a hard error, else null. */
    fun hardError(raw: String): String? {
        val lines = clean(raw)
        if (lines.any { it == "?" }) return "Adapter did not understand the command (?)"
        val hit = lines.firstOrNull { l -> HARD_ERRORS.any { l.contains(it) } } ?: return null
        return "Adapter reported: $hit"
    }

    /** Text reply for AT commands: first non-empty line that is not the echoed command. */
    fun textLine(raw: String, echoed: String): String =
        raw.replace(">", "").replace("\u0000", "").split('\r', '\n')
            .map { it.trim() }
            .firstOrNull {
                it.isNotEmpty() && !it.equals(echoed, ignoreCase = true) &&
                    !it.startsWith("SEARCHING", ignoreCase = true)
            } ?: ""

    /**
     * Rebuilds hex payloads from lines. Handles ELM multi-frame output:
     *   014            <- total length header
     *   0:490201...    <- frame lines
     *   1:...
     * and plain single-frame lines. Each returned string is one ECU message.
     */
    fun messages(lines: List<String>): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        fun flush() {
            if (cur.isNotEmpty()) { out.add(cur.toString()); cur.setLength(0) }
        }
        for ((i, l) in lines.withIndex()) {
            val m = MULTI.matchEntire(l)
            if (m != null) { cur.append(m.groupValues[1]); continue }
            flush()
            if (HEX3.matches(l) && lines.getOrNull(i + 1)?.startsWith("0:") == true) continue
            if (HEX.matches(l) && l.length % 2 == 0) out.add(l)
        }
        flush()
        return out
    }

    // ------------------------------------------------------------------ Mode 01

    /** Returns data bytes (after "41 PID") or null if absent / too short. */
    fun pidData(raw: String, pid: String, minBytes: Int): IntArray? {
        val prefix = "41$pid"
        for (m in messages(clean(raw))) {
            val idx = m.indexOf(prefix)
            if (idx < 0 || idx % 2 != 0) continue
            val hex = m.substring(idx + prefix.length)
            if (hex.length < minBytes * 2) continue
            return IntArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16) }
        }
        return null
    }

    fun decodePid(pid: String, raw: String): Double? = when (pid) {
        "0C" -> pidData(raw, pid, 2)?.let { (it[0] * 256 + it[1]) / 4.0 }
        "0D" -> pidData(raw, pid, 1)?.let { it[0].toDouble() }
        "05", "0F" -> pidData(raw, pid, 1)?.let { it[0] - 40.0 }
        "04", "11" -> pidData(raw, pid, 1)?.let { it[0] * 100.0 / 255.0 }
        "06", "07" -> pidData(raw, pid, 1)?.let { (it[0] - 128) * 100.0 / 128.0 }
        else -> null
    }

    fun decodeFuelStatus(raw: String): String? {
        val d = pidData(raw, "03", 1) ?: return null
        return when (d[0]) {
            0 -> "Engine off / motor off"
            1 -> "Open loop – engine not warm yet"
            2 -> "Closed loop – using O2 sensor"
            4 -> "Open loop – load / fuel cut"
            8 -> "Open loop – system fault"
            16 -> "Closed loop – O2 sensor fault"
            else -> "Status 0x%02X".format(d[0])
        }
    }

    /** ATRV reply such as "12.6V". */
    fun parseVoltage(raw: String): Double? {
        val text = clean(raw).filter { it != "ATRV" }.joinToString("")
        return Regex("(\\d+(?:\\.\\d+)?)").find(text)?.groupValues?.get(1)?.toDoubleOrNull()
    }

    // ------------------------------------------------------------------ PID 0101

    fun parseReadiness(raw: String): Readiness? {
        val d = pidData(raw, "01", 4) ?: return null
        val a = d[0]; val b = d[1]; val c = d[2]; val e = d[3]
        val mil = (a and 0x80) != 0
        val count = a and 0x7F
        val compression = (b and 0x08) != 0

        val list = mutableListOf<Monitor>()
        list += Monitor("Misfire", (b and 0x01) != 0, (b and 0x10) == 0)
        list += Monitor("Fuel system", (b and 0x02) != 0, (b and 0x20) == 0)
        list += Monitor("Components (CCM)", (b and 0x04) != 0, (b and 0x40) == 0)

        val defs: List<Pair<String, Int>> = if (!compression) listOf(
            "Catalyst" to 0, "Heated catalyst" to 1, "Evaporative system" to 2,
            "Secondary air" to 3, "A/C refrigerant" to 4, "Oxygen sensor" to 5,
            "O2 sensor heater" to 6, "EGR / VVT" to 7
        ) else listOf(
            "NMHC catalyst" to 0, "NOx / SCR" to 1, "Boost pressure" to 3,
            "Exhaust gas sensor" to 5, "PM filter (DPF)" to 6, "EGR / VVT" to 7
        )
        for ((name, bit) in defs) {
            list += Monitor(name, ((c shr bit) and 1) == 1, ((e shr bit) and 1) == 0)
        }
        return Readiness(mil, count, compression, list)
    }

    // ------------------------------------------------------------- Modes 03 / 07

    fun decodeDtc(b1: Int, b2: Int): String {
        val letter = "PCBU"[(b1 shr 6) and 0x03]
        val d1 = (b1 shr 4) and 0x03
        val d2 = (b1 and 0x0F).toString(16)
        val d3 = ((b2 shr 4) and 0x0F).toString(16)
        val d4 = (b2 and 0x0F).toString(16)
        return "$letter$d1$d2$d3$d4".uppercase()
    }

    /**
     * @param responseSid "43" for Mode 03, "47" for Mode 07
     * @param isCan CAN replies carry a DTC-count byte after the SID; KWP/ISO replies don't.
     */
    fun parseDtcs(raw: String, responseSid: String, isCan: Boolean): List<String> {
        val result = LinkedHashSet<String>()
        for (m in messages(clean(raw))) {
            if (!m.startsWith(responseSid)) continue
            var hex = m.substring(responseSid.length)
            if (isCan && hex.length >= 2) hex = hex.substring(2)
            var i = 0
            while (i + 4 <= hex.length) {
                val b1 = hex.substring(i, i + 2).toInt(16)
                val b2 = hex.substring(i + 2, i + 4).toInt(16)
                if (b1 != 0 || b2 != 0) result.add(decodeDtc(b1, b2))
                i += 4
            }
        }
        return result.toList()
    }

    /** Mode 04 positive response is "44". */
    fun clearSucceeded(raw: String): Boolean =
        messages(clean(raw)).any { it.startsWith("44") }
}
