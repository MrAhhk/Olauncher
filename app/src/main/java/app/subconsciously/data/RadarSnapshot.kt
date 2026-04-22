package app.subconsciously.data

data class RadarSnapshot(
    val awareness: Float,  // A: noticed the urge
    val control: Float,    // C: stopped the open
    val intention: Float,  // I: stopped after hesitating
    val time: Float,       // T: time saved
    val energy: Float      // E: composite feel
)
