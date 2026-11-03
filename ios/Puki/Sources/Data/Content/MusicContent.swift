import SwiftUI

struct InstrumentDef: Hashable {
    let id: String
    let name: BilingualText
    let color: Color
    let drawable: String
}

let instruments = [
    InstrumentDef(id: "drum", name: BilingualText("תוף", "Drum"), color: Color(argb: 0xFFEF5350), drawable: "ic_instrument_drum"),
    InstrumentDef(id: "xylophone", name: BilingualText("קסילופון", "Xylophone"), color: Color(argb: 0xFF42A5F5), drawable: "ic_instrument_xylophone"),
    InstrumentDef(id: "tambourine", name: BilingualText("תוף מרים", "Tambourine"), color: Color(argb: 0xFFFFEE58), drawable: "ic_instrument_tambourine"),
    InstrumentDef(id: "maracas", name: BilingualText("רעשנים", "Maracas"), color: Color(argb: 0xFF66BB6A), drawable: "ic_instrument_maracas"),
]
