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
        "Español" to "Este mapa resume tus visitas por país de origen.\nCada país muestra de dónde vienen tus turistas.\nLos colores representan los productos que consumieron.\nEl tamaño refleja cuánto se vendió de cada uno.\nCambia entre puntos y burbujas para ver el detalle.",
        "Quechua" to "Kay saywitipim watukuyniykikuna llaqtankuman hina rakisqa kachkan.\nSapa llaqtaqa maymantam turistaykikuna hamun chaytam rikuchin.\nLlimphukunaqa ima rurukunatam rantikurqan chaytam rikuchin.\nSayayninqa hayk'a rantikusqantam rikuchin.\nT'uqyakunata hinaspa muyukunata tikraspa aswan allinta qhaway.",
        "Inglés" to "This map summarizes your visits by country of origin.\nEach country shows where your tourists come from.\nThe colors represent the products they consumed.\nThe size reflects how much of each one was sold.\nSwitch between points and bubbles to see the detail.",
        "Portugués" to "Este mapa resume suas visitas por país de origem.\nCada país mostra de onde vêm seus turistas.\nAs cores representam os produtos que consumiram.\nO tamanho reflete quanto foi vendido de cada um.\nAlterne entre pontos e bolhas para ver o detalhe."
    )
}
