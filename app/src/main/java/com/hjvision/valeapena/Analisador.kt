package com.hjvision.valeapena

import android.content.Context
import android.graphics.Rect
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Config(
    val kml: Double,
    val gas: Double,
    val desgaste: Double,
    val metaHora: Double,
    val tempoExtra: Double,
    val vermelho: Double,
    val metaDia: Double,
    val kmLonge: Double,
    val voz: Boolean,
    val todosApps: Boolean
) {
    companion object {
        val PADRAO = Config(
            kml = 35.0, gas = 6.39, desgaste = 0.12,
            metaHora = 25.0, tempoExtra = 5.0, vermelho = 1.00, metaDia = 50.0, kmLonge = 10.0,
            voz = true, todosApps = false
        )

        fun carregar(c: Context): Config {
            val p = c.getSharedPreferences("cfg", Context.MODE_PRIVATE)
            fun f(k: String, d: Double) = p.getFloat(k, d.toFloat()).toDouble()
            return Config(
                kml = f("kml", PADRAO.kml),
                gas = f("gas", PADRAO.gas),
                desgaste = f("desgaste", PADRAO.desgaste),
                metaHora = f("metaHora", PADRAO.metaHora),
                tempoExtra = f("tempoExtra", PADRAO.tempoExtra),
                vermelho = f("vermelho", PADRAO.vermelho),
                metaDia = f("metaDia", PADRAO.metaDia),
                kmLonge = f("kmLonge", PADRAO.kmLonge),
                voz = p.getBoolean("voz", PADRAO.voz),
                todosApps = p.getBoolean("todosApps", PADRAO.todosApps)
            )
        }

        fun salvar(c: Context, cfg: Config) {
            c.getSharedPreferences("cfg", Context.MODE_PRIVATE).edit()
                .putFloat("kml", cfg.kml.toFloat())
                .putFloat("gas", cfg.gas.toFloat())
                .putFloat("desgaste", cfg.desgaste.toFloat())
                .putFloat("metaHora", cfg.metaHora.toFloat())
                .putFloat("tempoExtra", cfg.tempoExtra.toFloat())
                .putFloat("vermelho", cfg.vermelho.toFloat())
                .putFloat("metaDia", cfg.metaDia.toFloat())
                .putFloat("kmLonge", cfg.kmLonge.toFloat())
                .putBoolean("voz", cfg.voz)
                .putBoolean("todosApps", cfg.todosApps)
                .apply()
        }
    }
}

/**
 * Totais por dia. Cada entrega aceita soma valor, km, gasolina, desgaste e lucro.
 * Guarda os últimos 90 dias para você ver o histórico.
 */
object Dia {
    private fun hoje(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    data class Total(
        val data: String,
        val valor: Double,
        val lucro: Double,
        val km: Double,
        val gasolina: Double,
        val desgaste: Double,
        val entregas: Int,
        val inicio: Long,
        val fim: Long
    ) {
        /** Horas entre o primeiro aceite e o fim previsto da última entrega. */
        val horas: Double get() = if (inicio > 0 && fim > inicio) (fim - inicio) / 3_600_000.0 else 0.0
        val porHora: Double? get() = if (horas > 0.05) lucro / horas else null
        val porKm: Double? get() = if (km > 0) valor / km else null
    }

    private fun vazio(data: String) = Total(data, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0L, 0L)

    private fun todos(c: Context): JSONObject {
        val t = c.getSharedPreferences("dia", Context.MODE_PRIVATE).getString("dias", "{}") ?: "{}"
        return try { JSONObject(t) } catch (e: Exception) { JSONObject() }
    }

    private fun gravar(c: Context, j: JSONObject) {
        c.getSharedPreferences("dia", Context.MODE_PRIVATE).edit().putString("dias", j.toString()).apply()
    }

    private fun deJson(data: String, o: JSONObject?): Total {
        if (o == null) return vazio(data)
        return Total(
            data,
            o.optDouble("valor", 0.0),
            o.optDouble("lucro", 0.0),
            o.optDouble("km", 0.0),
            o.optDouble("gasolina", 0.0),
            o.optDouble("desgaste", 0.0),
            o.optInt("entregas", 0),
            o.optLong("inicio", 0L),
            o.optLong("fim", 0L)
        )
    }

    private fun paraJson(t: Total): JSONObject = JSONObject()
        .put("valor", t.valor)
        .put("lucro", t.lucro)
        .put("km", t.km)
        .put("gasolina", t.gasolina)
        .put("desgaste", t.desgaste)
        .put("entregas", t.entregas)
        .put("inicio", t.inicio)
        .put("fim", t.fim)

    fun ler(c: Context): Total {
        val d = hoje()
        return deJson(d, todos(c).optJSONObject(d))
    }

    fun somar(c: Context, r: Resultado, cfg: Config): Total {
        val j = todos(c)
        val d = hoje()
        val atual = deJson(d, j.optJSONObject(d))
        val agora = System.currentTimeMillis()
        val kml = if (cfg.kml > 0) cfg.kml else 1.0
        val novo = Total(
            data = d,
            valor = atual.valor + r.valor,
            lucro = atual.lucro + r.lucro,
            km = atual.km + r.km,
            gasolina = atual.gasolina + r.km / kml * cfg.gas,
            desgaste = atual.desgaste + r.km * cfg.desgaste,
            entregas = atual.entregas + 1,
            inicio = if (atual.inicio > 0) atual.inicio else agora,
            fim = maxOf(atual.fim, agora + r.minutos * 60_000L)
        )
        j.put(d, paraJson(novo))
        c.getSharedPreferences("dia", Context.MODE_PRIVATE).edit()
            .putString("anterior", paraJson(atual).toString())
            .putString("anteriorData", d)
            .apply()
        // mantém só os 90 dias mais recentes
        val chaves = j.keys().asSequence().toList().sortedDescending()
        for (k in chaves.drop(90)) j.remove(k)
        gravar(c, j)
        return novo
    }

    /** Volta o dia para como estava antes da última entrega somada. */
    fun desfazer(c: Context): Boolean {
        val p = c.getSharedPreferences("dia", Context.MODE_PRIVATE)
        val d = hoje()
        val ant = p.getString("anterior", null) ?: return false
        if (p.getString("anteriorData", "") != d) return false
        val j = todos(c)
        j.put(d, JSONObject(ant))
        gravar(c, j)
        p.edit().remove("anterior").remove("anteriorData").apply()
        return true
    }

    fun zerarHoje(c: Context) {
        val j = todos(c)
        j.remove(hoje())
        gravar(c, j)
    }

    /** Dias anteriores, do mais novo para o mais antigo (sem hoje). */
    fun historico(c: Context): List<Total> {
        val j = todos(c)
        val h = hoje()
        return j.keys().asSequence().toList()
            .filter { it != h }
            .sortedDescending()
            .map { deJson(it, j.optJSONObject(it)) }
    }

    /** Planilha para colar no Google Planilhas ou Excel. */
    fun planilha(c: Context): String {
        val br = Locale("pt", "BR")
        fun n(v: Double) = String.format(br, "%.2f", v)
        val sb = StringBuilder("Data\tEntregas\tKm\tGanho\tGasolina\tDesgaste\tLucro\tHoras\tLucro por hora\n")
        val dias = listOf(ler(c)).filter { it.entregas > 0 } + historico(c)
        for (t in dias) {
            sb.append(t.data).append('\t')
                .append(t.entregas).append('\t')
                .append(n(t.km)).append('\t')
                .append(n(t.valor)).append('\t')
                .append(n(t.gasolina)).append('\t')
                .append(n(t.desgaste)).append('\t')
                .append(n(t.lucro)).append('\t')
                .append(n(t.horas)).append('\t')
                .append(t.porHora?.let { n(it) } ?: "").append('\n')
        }
        return sb.toString()
    }
}

/** Uma linha de texto lida da imagem da tela, com a posição dela. */
data class Linha(val texto: String, val caixa: Rect)

data class Oferta(val valor: Double, val kmColeta: Double, val kmEntrega: Double, val minutos: Int)

data class Resultado(
    val nivel: Int, // 0 ruim, 1 mais ou menos, 2 boa
    val titulo: String,
    val porKm: Double,
    val lucro: Double,
    val porHora: Double?,
    val minutos: Int,
    val km: Double,
    val valor: Double,
    val longe: Boolean
)

object Analisador {
    private val BR = Locale("pt", "BR")
    private val reValor = Regex("""R\$\s*(\d{1,3}(?:\.\d{3})*,\d{2})""")
    private val reKm = Regex("""(\d+(?:[.,]\d+)?)\s*km\b""", RegexOption.IGNORE_CASE)
    private val reMin = Regex("""(\d+)\s*min\b""", RegexOption.IGNORE_CASE)

    /**
     * Lê o texto da tela. Só considera oferta se tiver botão Aceitar, valor em R$ e km.
     * Lê só o trecho do card (de "Entrega Moto" até "Aceitar"), para não pegar
     * valores de outras partes da tela, como os ganhos do dia.
     * Soma todas as paradas: a primeira é a coleta, o resto é entrega.
     */
    fun extrair(texto: String): Oferta? {
        val iAceitar = texto.lastIndexOf("Aceitar", ignoreCase = true)
        if (iAceitar < 0) return null
        val iTitulo = texto.lastIndexOf("Entrega Moto", iAceitar, ignoreCase = true)
        val trecho = texto.substring(if (iTitulo >= 0) iTitulo else 0, iAceitar)
        val v = reValor.find(trecho) ?: return null
        val valor = v.groupValues[1].replace(".", "").replace(",", ".").toDoubleOrNull() ?: return null
        val resto = trecho.substring(v.range.last + 1)
        val kms = reKm.findAll(resto)
            .mapNotNull { it.groupValues[1].replace(",", ".").toDoubleOrNull() }
            .toList()
        if (kms.isEmpty()) return null
        val mins = reMin.findAll(resto).mapNotNull { it.groupValues[1].toIntOrNull() }.toList()
        return Oferta(
            valor = valor,
            kmColeta = if (kms.size >= 2) kms[0] else 0.0,
            kmEntrega = if (kms.size >= 2) kms.drop(1).sum() else kms[0],
            minutos = mins.sum()
        )
    }

    private val reValorOcr = Regex("""R\s?[${'$'}S5]\s?(\d{1,3}(?:[.\s]\d{3})*[,.]\d{2})""")

    /** Textos da faixa do próprio app, que também aparecem na imagem. */
    private fun ehNosso(t: String): Boolean =
        t.contains("/km") || t.contains("/h") || t.contains("Hoje:") || t.contains("lucro", ignoreCase = true)

    /**
     * Lê a oferta a partir das linhas da imagem (OCR).
     * Valor: o R$ com a maior letra acima do botão Aceitar (é o preço grande da oferta).
     * Km e minutos: as linhas entre o preço e o botão Aceitar.
     * Devolve a oferta e a posição do botão Aceitar na tela.
     */
    fun extrairOcr(linhas: List<Linha>): Pair<Oferta, Rect>? {
        val aceitar = linhas.lastOrNull { it.texto.contains("Aceitar", ignoreCase = true) } ?: return null
        val acima = linhas
            .filter { it.caixa.top < aceitar.caixa.top && !ehNosso(it.texto) }
            .sortedBy { it.caixa.top }
        var melhor: Linha? = null
        var melhorMatch: MatchResult? = null
        for (l in acima) {
            val m = reValorOcr.find(l.texto) ?: continue
            val atual = melhor
            if (atual == null || l.caixa.height() > atual.caixa.height()) {
                melhor = l
                melhorMatch = m
            }
        }
        val preco = melhor ?: return null
        val m = melhorMatch ?: return null
        val digitos = m.groupValues[1].filter { it.isDigit() }
        if (digitos.length < 3) return null
        val valor = (digitos.toLongOrNull() ?: return null) / 100.0

        val abaixo = acima.filter { it.caixa.top > preco.caixa.top }.joinToString("\n") { it.texto }
        val kms = reKm.findAll(abaixo)
            .mapNotNull { it.groupValues[1].replace(",", ".").toDoubleOrNull() }
            .toList()
        if (kms.isEmpty()) return null
        val mins = reMin.findAll(abaixo).mapNotNull { it.groupValues[1].toIntOrNull() }.toList()
        val oferta = Oferta(
            valor = valor,
            kmColeta = if (kms.size >= 2) kms[0] else 0.0,
            kmEntrega = if (kms.size >= 2) kms.drop(1).sum() else kms[0],
            minutos = mins.sum()
        )
        return Pair(oferta, Rect(aceitar.caixa))
    }

    /**
     * Regra principal: lucro por hora.
     * Verde: bate a meta por hora. Amarelo: pelo menos 80% da meta. Vermelho: abaixo disso.
     * Proteção: se pagar menos que o mínimo por km, é vermelho de qualquer jeito.
     */
    fun avaliar(o: Oferta, c: Config): Resultado {
        val km = o.kmColeta + o.kmEntrega
        val kml = if (c.kml > 0) c.kml else 1.0
        val custo = km / kml * c.gas + km * c.desgaste
        val lucro = o.valor - custo
        val porKm = if (km > 0) o.valor / km else 0.0
        val minutos = if (o.minutos > 0) o.minutos + c.tempoExtra.toInt() else 0
        val porHora = if (minutos > 0) lucro / (minutos / 60.0) else null

        var nivel = if (porHora != null && c.metaHora > 0) {
            when {
                porHora >= c.metaHora -> 2
                porHora >= c.metaHora * 0.8 -> 1
                else -> 0
            }
        } else {
            when {
                porKm >= 1.5 -> 2
                porKm >= c.vermelho -> 1
                else -> 0
            }
        }
        if (porKm < c.vermelho) nivel = 0
        if (lucro <= 0) nivel = 0

        val titulo = when (nivel) {
            2 -> "BOA"
            1 -> "MAIS OU MENOS"
            else -> "RECUSA"
        }
        val longe = c.kmLonge > 0 && o.kmEntrega >= c.kmLonge
        return Resultado(nivel, titulo, porKm, lucro, porHora, minutos, km, o.valor, longe)
    }

    fun dinheiro(v: Double): String = "R$ " + String.format(BR, "%.2f", v)

    fun inteiro(v: Double): String = "R$ " + Math.round(v).toString()

    fun numero(v: Double): String = String.format(BR, "%.1f", v)

    /** Texto da faixa: linha grande com o resultado e o R$/hora, depois os detalhes. */
    fun textoFaixa(r: Resultado, dia: Dia.Total?, cfg: Config): String {
        val linha1 = if (r.porHora != null) "${r.titulo} · ${inteiro(r.porHora)}/h" else r.titulo
        val linha2 = "${dinheiro(r.porKm)}/km · lucro ${dinheiro(r.lucro)} · ${r.minutos} min"
        val linha3 = if (dia != null && cfg.metaDia > 0) {
            "\nHoje: ${dinheiro(dia.valor)} de ${inteiro(cfg.metaDia)} · ${numero(dia.km)} km"
        } else ""
        val aviso = if (r.longe) "\n⚠ Entrega longe: veja se tem pedido na volta" else ""
        return "$linha1\n$linha2$aviso$linha3"
    }

    fun fala(r: Resultado): String {
        val inicio = when (r.nivel) {
            2 -> "Entrega boa."
            1 -> "Mais ou menos."
            else -> "Entrega ruim."
        }
        val h = r.porHora
        val base = if (h != null && h > 0) {
            "$inicio ${Math.round(h)} reais por hora."
        } else {
            "$inicio ${valorFalado(r.porKm)} por quilômetro."
        }
        return if (r.longe) "$base Atenção, entrega longe." else base
    }

    private fun valorFalado(v: Double): String {
        val totalCent = Math.round(v * 100)
        val reais = totalCent / 100
        val cent = totalCent % 100
        val parteReais = if (reais == 1L) "1 real" else "$reais reais"
        return when {
            reais == 0L -> "$cent centavos"
            cent == 0L -> parteReais
            else -> "$parteReais e $cent"
        }
    }

    const val EXEMPLO = "Entrega Moto\nR\$10,50\nPreço x1,4\n4,97\nPerfil Essencial\n" +
        "7 min (1,2 km)\nEscola Reunida Manoel da Silva\n10 min (5,2 km)\nR. Capri, 182 - Pagani\nAceitar"
}
