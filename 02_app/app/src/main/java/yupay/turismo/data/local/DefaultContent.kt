package yupay.turismo.data.local

/**
 * Contenido por defecto (tips/resúmenes de mapa por idioma) con el que arranca la app
 * "de fábrica". Compartido por [yupay.turismo.ui.MainViewModel] (siembra inicial) y por
 * [yupay.turismo.data.AppReset] (reset total), para que todos los resets dejen el mismo estado.
 */
object DefaultContent {
    val tips: Map<String, String> = mapOf(
        "Español" to "La hospitalidad es la clave.\nSiempre recibe a tus turistas con una sonrisa.\nConoce bien tu historia local para compartirla.\nManten tus espacios limpios y ordenados.\nOfrece productos locales de calidad.",
        "Quechua" to "Allin chaskiymi ancha allin.\nTuristaykikunataqa sapa kutin p'isñuywan chaskiy.\nLlaqtaykiq kawsayninta allinta yachay willanaykipaq.\nKuyuchiy wasiykikunata ch'uya hinaspa allichasqa.\nAllin llaqtaykiq rurunkunata quy.",
        "Inglés" to "Hospitality is the key.\nAlways welcome your tourists with a smile.\nKnow your local history well to share it.\nKeep your spaces clean and organized.\nOffer quality local products.",
        "Portugués" to "A hospitalidade é a chave.\nSempre receba seus turistas com um sorriso.\nConheça bem sua história local para compartilhá-la.\nMantenha seus espaços limpos e organizados.\nOfereça produtos locais de qualidade."
    )

    val summaries: Map<String, String> = mapOf(
        "Español" to "Este mapa muestra la distribución de tus visitas.\nLos puntos azules representan hospedaje.\nLos puntos verdes son de alimentación.\nLos puntos rojos indican artesanía.\nUsa el zoom para ver más detalles.",
        "Quechua" to "Kay saywitipim watukuyniykikuna rakisqa kachkan.\nAnqas unanchakunaqa puñuy wasim.\nQ'umir unanchakunaqa mikhuy wasim.\nPuka unanchakunaqa makipi rurasqakuna.\nHatunyachiy aswan allinta qhawanaykipaq.",
        "Inglés" to "This map shows the distribution of your visits.\nBlue points represent lodging.\nGreen points are for food services.\nRed points indicate handicrafts.\nUse zoom to see more details.",
        "Portugués" to "Este mapa mostra a distribuição das suas visitas.\nOs pontos azuis representam hospedagem.\nOs pontos verdes são de alimentação.\nOs pontos vermelhos indicam artesanato.\nUse o zoom para ver mais detalhes."
    )
}
