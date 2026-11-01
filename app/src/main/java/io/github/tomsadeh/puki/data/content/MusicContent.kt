package io.github.tomsadeh.puki.data.content

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import io.github.tomsadeh.puki.R
import io.github.tomsadeh.puki.data.models.BilingualText

data class InstrumentDef(
    val id: String,
    val name: BilingualText,
    val color: Color,
    @DrawableRes val drawableRes: Int
)

val instruments = listOf(
    InstrumentDef("drum", BilingualText("תוף", "Drum"), Color(0xFFEF5350), R.drawable.ic_instrument_drum),
    InstrumentDef("xylophone", BilingualText("קסילופון", "Xylophone"), Color(0xFF42A5F5), R.drawable.ic_instrument_xylophone),
    InstrumentDef("tambourine", BilingualText("תוף מרים", "Tambourine"), Color(0xFFFFEE58), R.drawable.ic_instrument_tambourine),
    InstrumentDef("maracas", BilingualText("רעשנים", "Maracas"), Color(0xFF66BB6A), R.drawable.ic_instrument_maracas)
)
