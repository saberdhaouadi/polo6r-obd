package com.polo6r.obdscanner.obd

/** Local lookup of common DTCs seen on VW Polo 6R (petrol 1.2 TSI/MPI, 1.4, 1.6 TDI/1.2 TDI etc.). */
object VagDtcDatabase {

    private val codes: Map<String, String> = mapOf(
        // Fuel & air metering
        "P0087" to "Fuel rail pressure too low",
        "P0088" to "Fuel rail pressure too high",
        "P0089" to "Fuel pressure regulator performance",
        "P0100" to "Mass air flow (MAF) circuit malfunction",
        "P0101" to "MAF sensor range/performance",
        "P0102" to "MAF sensor circuit low input",
        "P0103" to "MAF sensor circuit high input",
        "P0106" to "MAP / boost pressure sensor range/performance",
        "P0107" to "MAP / boost pressure sensor circuit low input",
        "P0108" to "MAP / boost pressure sensor circuit high input",
        "P0110" to "Intake air temperature sensor circuit",
        "P0112" to "Intake air temperature sensor circuit low",
        "P0113" to "Intake air temperature sensor circuit high",
        "P0115" to "Coolant temperature sensor circuit",
        "P0116" to "Coolant temperature sensor range/performance",
        "P0117" to "Coolant temperature sensor circuit low",
        "P0118" to "Coolant temperature sensor circuit high",
        "P0120" to "Throttle/pedal position sensor A circuit",
        "P0121" to "Throttle/pedal position sensor A range/performance",
        "P0122" to "Throttle/pedal position sensor A circuit low",
        "P0123" to "Throttle/pedal position sensor A circuit high",
        "P0125" to "Insufficient coolant temperature for closed-loop control",
        "P0128" to "Coolant thermostat below regulating temperature (thermostat stuck open)",
        // O2 sensors
        "P0130" to "O2 sensor circuit, bank 1 sensor 1",
        "P0131" to "O2 sensor circuit low voltage, bank 1 sensor 1",
        "P0132" to "O2 sensor circuit high voltage, bank 1 sensor 1",
        "P0133" to "O2 sensor slow response, bank 1 sensor 1",
        "P0134" to "O2 sensor no activity, bank 1 sensor 1",
        "P0135" to "O2 sensor heater circuit, bank 1 sensor 1",
        "P0136" to "O2 sensor circuit, bank 1 sensor 2 (post-cat)",
        "P0137" to "O2 sensor circuit low voltage, bank 1 sensor 2",
        "P0138" to "O2 sensor circuit high voltage, bank 1 sensor 2",
        "P0140" to "O2 sensor no activity, bank 1 sensor 2",
        "P0141" to "O2 sensor heater circuit, bank 1 sensor 2",
        // Fuel trim
        "P0171" to "System too lean, bank 1 (vacuum/intake leak, MAF, fuel pressure)",
        "P0172" to "System too rich, bank 1 (injectors, MAF, fuel pressure)",
        "P0174" to "System too lean, bank 2",
        "P0175" to "System too rich, bank 2",
        // Rail pressure / injectors
        "P0190" to "Fuel rail pressure sensor circuit",
        "P0191" to "Fuel rail pressure sensor range/performance",
        "P0192" to "Fuel rail pressure sensor circuit low",
        "P0193" to "Fuel rail pressure sensor circuit high",
        "P0201" to "Injector circuit malfunction, cylinder 1",
        "P0202" to "Injector circuit malfunction, cylinder 2",
        "P0203" to "Injector circuit malfunction, cylinder 3",
        "P0204" to "Injector circuit malfunction, cylinder 4",
        "P0217" to "Engine coolant over-temperature condition",
        "P0219" to "Engine overspeed condition",
        // Boost / turbo / throttle B
        "P0234" to "Turbocharger overboost condition",
        "P0235" to "Boost pressure sensor A circuit",
        "P0236" to "Boost pressure sensor A range/performance",
        "P0237" to "Boost pressure sensor A circuit low",
        "P0238" to "Boost pressure sensor A circuit high",
        "P0243" to "Wastegate solenoid A malfunction",
        "P0245" to "Wastegate solenoid A low",
        "P0246" to "Wastegate solenoid A high",
        "P0299" to "Turbo/supercharger underboost (check hoses, N75/N249, wastegate, leaks)",
        // Misfire
        "P0300" to "Random / multiple cylinder misfire detected",
        "P0301" to "Cylinder 1 misfire detected",
        "P0302" to "Cylinder 2 misfire detected",
        "P0303" to "Cylinder 3 misfire detected",
        "P0304" to "Cylinder 4 misfire detected",
        // Knock / crank / cam
        "P0325" to "Knock sensor 1 circuit (bank 1)",
        "P0327" to "Knock sensor 1 circuit low",
        "P0328" to "Knock sensor 1 circuit high",
        "P0335" to "Crankshaft position sensor A circuit",
        "P0336" to "Crankshaft position sensor A range/performance",
        "P0340" to "Camshaft position sensor A circuit (bank 1)",
        "P0341" to "Camshaft position sensor A range/performance",
        "P0016" to "Crankshaft / camshaft position correlation, bank 1 sensor A (timing chain stretch, phaser)",
        "P0011" to "Intake camshaft timing over-advanced (bank 1)",
        "P0014" to "Exhaust camshaft timing over-advanced (bank 1)",
        "P0380" to "Glow plug / heater circuit A",
        "P0670" to "Glow plug control module circuit",
        // EGR / cat / EVAP
        "P0401" to "EGR flow insufficient",
        "P0402" to "EGR flow excessive",
        "P0403" to "EGR control circuit malfunction",
        "P0404" to "EGR control circuit range/performance",
        "P0420" to "Catalyst efficiency below threshold, bank 1",
        "P0430" to "Catalyst efficiency below threshold, bank 2",
        "P0441" to "EVAP system incorrect purge flow",
        "P0442" to "EVAP system small leak detected",
        "P0443" to "EVAP purge control valve circuit",
        "P0455" to "EVAP system large leak detected",
        "P0456" to "EVAP system very small leak detected",
        // Misc engine/vehicle
        "P0480" to "Cooling fan 1 control circuit",
        "P0500" to "Vehicle speed sensor malfunction",
        "P0504" to "Brake switch A/B correlation",
        "P0507" to "Idle control system RPM higher than expected",
        "P0562" to "System voltage low (battery / alternator)",
        "P0563" to "System voltage high",
        "P0571" to "Brake switch A circuit",
        "P0601" to "Control module memory checksum error",
        "P0606" to "Control module processor fault",
        "P0627" to "Fuel pump A control circuit open",
        "P0638" to "Throttle actuator control range/performance",
        "P0685" to "ECM power relay control circuit open",
        // P2xxx
        "P2002" to "Diesel particulate filter efficiency below threshold",
        "P2015" to "Intake manifold runner position sensor/switch range/performance (intake flap)",
        "P2016" to "Intake manifold runner position sensor circuit low",
        "P2017" to "Intake manifold runner position sensor circuit high",
        "P2096" to "Post-catalyst fuel trim system too lean, bank 1",
        "P2097" to "Post-catalyst fuel trim system too rich, bank 1",
        "P2106" to "Throttle actuator control system – forced limited power",
        "P2119" to "Throttle actuator control throttle body range/performance",
        "P2135" to "Throttle/pedal position sensor A/B voltage correlation",
        "P2177" to "System too lean off idle, bank 1",
        "P2178" to "System too rich off idle, bank 1",
        "P2187" to "System too lean at idle, bank 1",
        "P2188" to "System too rich at idle, bank 1",
        "P2279" to "Intake air system leak",
        "P2293" to "Fuel pressure regulator 2 performance",
        "P2300" to "Ignition coil A secondary circuit low (cylinder 1)",
        "P2303" to "Ignition coil B secondary circuit low (cylinder 2)",
        "P2306" to "Ignition coil C secondary circuit low (cylinder 3)",
        "P2309" to "Ignition coil D secondary circuit low (cylinder 4)",
        // VAG-specific P1xxx (descriptions are generic; confirm with VCDS/ODIS)
        "P1128" to "VAG: Long-term fuel trim (multiplicative) bank 1 – system too lean",
        "P1129" to "VAG: Long-term fuel trim (multiplicative) bank 1 – system too rich",
        // Network (U)
        "U0001" to "High-speed CAN communication bus",
        "U0100" to "Lost communication with ECM/PCM",
        "U0101" to "Lost communication with transmission control module",
        "U0121" to "Lost communication with ABS control module",
        "U0140" to "Lost communication with body control module",
        "U0155" to "Lost communication with instrument cluster",
        "U0164" to "Lost communication with HVAC control module"
    )

    fun describe(code: String): String {
        codes[code]?.let { return it }
        return when {
            code.startsWith("P1") -> "VAG manufacturer-specific powertrain code – look up with VCDS/ODIS or a VAG fault list"
            code.startsWith("P0") || code.startsWith("P2") -> "Generic powertrain code – not in the local list"
            code.startsWith("P3") -> "Reserved / manufacturer-specific powertrain code"
            code.startsWith("C") -> "Chassis code (ABS/ESP/steering) – not in the local list"
            code.startsWith("B") -> "Body code (airbag/comfort) – not in the local list"
            code.startsWith("U") -> "Network/communication code – not in the local list"
            else -> "Unknown code"
        }
    }
}
