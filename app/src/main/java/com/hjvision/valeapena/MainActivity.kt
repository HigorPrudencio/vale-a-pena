package com.hjvision.valeapena

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    companion object {
        private const val PEDIDO_CAPTURA = 77
    }

    private lateinit var status: TextView
    private lateinit var resultado: TextView
    private lateinit var diaTexto: TextView
    private lateinit var historicoTexto: TextView
    private val campos = LinkedHashMap<String, EditText>()
    private lateinit var swVoz: Switch
    private lateinit var swTodos: Switch

    private val corFundo = Color.parseColor("#0F1115")
    private val corCard = Color.parseColor("#1A1D24")
    private val corTexto = Color.parseColor("#F2F3F5")
    private val corMuted = Color.parseColor("#9AA0AB")
    private val corDestaque = Color.parseColor("#F5A524")

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = corFundo

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(32))
            setBackgroundColor(corFundo)
        }

        raiz.addView(banner())

        status = texto("", 15f, corTexto, true)
        raiz.addView(card(status))

        diaTexto = texto("", 16f, corTexto, true)
        val diaBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        diaBox.addView(diaTexto)
        diaBox.addView(texto(
            "Soma sozinho quando você toca em Aceitar na 99. Os km são os da oferta (coleta + entrega).",
            12f, corMuted, false
        ))
        diaBox.addView(botao("+ Somar última oferta (se não somou)", Color.parseColor("#2A2E38")) {
            val r = Estado.ultimaOferta
            when {
                r == null -> Toast.makeText(this, "Nenhuma oferta lida ainda.", Toast.LENGTH_SHORT).show()
                Estado.ultimaSomada === r -> Toast.makeText(this, "Essa oferta já foi somada.", Toast.LENGTH_SHORT).show()
                else -> {
                    Estado.ultimaSomada = r
                    Dia.somar(this, r, Config.carregar(this))
                    Toast.makeText(this, "Somado: ${Analisador.dinheiro(r.valor)}", Toast.LENGTH_SHORT).show()
                    atualizarStatus()
                }
            }
        })
        diaBox.addView(botao("Desfazer última entrega (cancelada)", Color.parseColor("#2A2E38")) {
            if (Dia.desfazer(this)) {
                Estado.ultimaSomada = null
                Toast.makeText(this, "Última entrega removida.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Nada para desfazer.", Toast.LENGTH_SHORT).show()
            }
            atualizarStatus()
        })
        diaBox.addView(botao("Zerar hoje", Color.parseColor("#2A2E38")) {
            android.app.AlertDialog.Builder(this)
                .setTitle("Zerar hoje?")
                .setMessage("Apaga as entregas somadas hoje. Os outros dias continuam salvos.")
                .setPositiveButton("Zerar") { _, _ ->
                    Dia.zerarHoje(this)
                    atualizarStatus()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        })
        raiz.addView(card(diaBox))

        historicoTexto = texto("", 14f, corTexto, false)
        val histBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        histBox.addView(texto("Dias anteriores", 18f, corTexto, true))
        histBox.addView(historicoTexto)
        histBox.addView(botao("Copiar histórico (planilha)", Color.parseColor("#2A2E38")) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("historico", Dia.planilha(this)))
            Toast.makeText(this, "Copiado. Cole no Google Planilhas ou Excel.", Toast.LENGTH_LONG).show()
        })
        raiz.addView(card(histBox))

        raiz.addView(botao("1. Ativar leitor de ofertas", corDestaque) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        raiz.addView(texto(
            "Na tela que abrir, entre em \"Apps instalados\" (ou \"Serviços\"), toque em \"Vale a Pena: leitor de ofertas\" e ative.",
            13f, corMuted, false
        ))
        raiz.addView(botao("2. Ligar leitura por imagem", corDestaque) {
            if (CapturaService.instancia != null) {
                Toast.makeText(this, "A leitura por imagem já está ligada.", Toast.LENGTH_SHORT).show()
            } else {
                val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
                startActivityForResult(mpm.createScreenCaptureIntent(), PEDIDO_CAPTURA)
            }
        })
        raiz.addView(texto(
            "A 99 esconde o texto da tela. Por isso o app lê a oferta pela imagem, direto no celular. Nada é gravado nem enviado. Toque em \"Iniciar agora\". Precisa ligar de novo se reiniciar o celular.",
            13f, corMuted, false
        ))
        raiz.addView(botao("Desligar leitura por imagem", Color.parseColor("#2A2E38")) {
            stopService(Intent(this, CapturaService::class.java))
            Toast.makeText(this, "Leitura por imagem desligada.", Toast.LENGTH_SHORT).show()
            status.postDelayed({ atualizarStatus() }, 500)
        })
        raiz.addView(botao("Liberar uso de bateria", Color.parseColor("#2A2E38")) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        })
        raiz.addView(texto(
            "Procure \"Vale a Pena\" e escolha \"Não otimizar\" ou \"Sem restrição\". Sem isso, o celular pode desligar o leitor no meio do dia.",
            13f, corMuted, false
        ))

        // Ajustes
        val cfg = Config.carregar(this)
        val ajustes = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ajustes.addView(texto("Meus ajustes", 18f, corTexto, true))
        campo(ajustes, "metaHora", "Meta de lucro por hora (R$)", cfg.metaHora)
        campo(ajustes, "metaDia", "Meta do dia (R$)", cfg.metaDia)
        campo(ajustes, "tempoExtra", "Tempo extra por entrega (min)", cfg.tempoExtra)
        ajustes.addView(texto(
            "Estacionar, esperar o pedido e entregar. Esse tempo entra na conta do R$ por hora.",
            12f, corMuted, false
        ))
        campo(ajustes, "kml", "Consumo da moto (km/l)", cfg.kml)
        campo(ajustes, "gas", "Gasolina (R$ por litro)", cfg.gas)
        campo(ajustes, "desgaste", "Desgaste da moto (R$ por km)", cfg.desgaste)
        campo(ajustes, "vermelho", "Recusar sempre abaixo de (R$ por km)", cfg.vermelho)
        campo(ajustes, "kmLonge", "Avisar entrega longe a partir de (km)", cfg.kmLonge)
        swVoz = Switch(this).apply {
            text = "Falar o resultado em voz alta"
            setTextColor(corTexto)
            isChecked = cfg.voz
            setPadding(0, dp(10), 0, dp(4))
        }
        ajustes.addView(swVoz)
        swTodos = Switch(this).apply {
            text = "Modo teste: ler qualquer app"
            setTextColor(corTexto)
            isChecked = cfg.todosApps
            setPadding(0, dp(4), 0, dp(4))
        }
        ajustes.addView(swTodos)
        ajustes.addView(texto(
            "Use o modo teste só se o app não reconhecer a 99. Depois desligue.",
            12f, corMuted, false
        ))
        ajustes.addView(botao("Salvar ajustes", Color.parseColor("#2A2E38")) { salvar() })
        ajustes.addView(botao("Voltar ao padrão", Color.parseColor("#2A2E38")) { padrao() })
        raiz.addView(card(ajustes))

        // Teste
        resultado = texto("", 16f, corTexto, true)
        raiz.addView(botao("3. Testar com oferta de exemplo", corDestaque) { testar() })
        raiz.addView(resultado)

        raiz.addView(botao("Copiar diagnóstico da 99", Color.parseColor("#2A2E38")) {
            val t = "Vale a Pena v0.5\nEventos lidos da 99: ${Estado.eventos99}\n" +
                "Leitura por imagem: ${if (CapturaService.instancia != null) "ligada" else "desligada"} · ${Estado.leiturasOcr} leituras\n\n" +
                "=== IMAGEM COM BOTÃO ACEITAR ===\n${Estado.diagOcr.ifBlank { "(nenhuma)" }}\n\n" +
                "=== LEITURA COM BOTÃO ACEITAR ===\n${Estado.diagAceitar.ifBlank { "(nenhuma)" }}\n\n" +
                "=== ÚLTIMA LEITURA ===\n${Estado.diagnostico.ifBlank { "(nenhuma)" }.take(1500)}\n\n" +
                "=== ÚLTIMO TEXTO COM R$ ===\n${Estado.ultimoTexto.ifBlank { "(nenhum)" }.take(2500)}"
            run {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("texto 99", t))
                Toast.makeText(this, "Copiado. Cole na conversa com o Claude.", Toast.LENGTH_LONG).show()
            }
        })
        raiz.addView(texto(
            "Se uma oferta não for reconhecida, toque nesse botão logo depois que ela aparecer e mande o texto para o Claude.",
            12f, corMuted, false
        ))

        setContentView(ScrollView(this).apply {
            setBackgroundColor(corFundo)
            addView(raiz)
        })
    }

    @Deprecated("Activity sem AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PEDIDO_CAPTURA) return
        if (resultCode != RESULT_OK || data == null) {
            Toast.makeText(this, "Leitura por imagem não foi liberada.", Toast.LENGTH_LONG).show()
            return
        }
        val i = Intent(this, CapturaService::class.java)
            .putExtra(CapturaService.EXTRA_CODIGO, resultCode)
            .putExtra(CapturaService.EXTRA_DADOS, data)
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        Toast.makeText(this, "Leitura por imagem ligada.", Toast.LENGTH_SHORT).show()
        status.postDelayed({ atualizarStatus() }, 800)
    }

    override fun onResume() {
        super.onResume()
        atualizarStatus()
    }

    private fun atualizarStatus() {
        val ativo = Estado.servico != null
        val imagem = if (CapturaService.instancia != null) "LIGADA ✅" else "DESLIGADA ❌ (toque no botão 2)"
        status.text = if (ativo) {
            "Leitor: ATIVO ✅\nLeitura por imagem: $imagem\nÚltimo app lido: ${Estado.ultimoApp}" +
                (if (Estado.ultimoResultado.isNotBlank()) "\nÚltima oferta: ${Estado.ultimoResultado.replace("\n", " · ")}" else "")
        } else {
            "Leitor: DESATIVADO ❌\nToque no botão 1 para ativar."
        }
        status.setTextColor(
            if (ativo && CapturaService.instancia != null) Color.parseColor("#22C55E")
            else if (ativo) Color.parseColor("#FACC15")
            else Color.parseColor("#EF4444")
        )

        val cfg = Config.carregar(this)
        val d = Dia.ler(this)
        val falta = cfg.metaDia - d.valor
        val sbDia = StringBuilder()
        sbDia.append("Hoje: ${Analisador.dinheiro(d.valor)} de ${Analisador.inteiro(cfg.metaDia)}\n")
        sbDia.append(if (falta > 0) "Faltam ${Analisador.dinheiro(falta)}\n" else "Meta batida! ✅\n")
        sbDia.append("\n")
        sbDia.append("Entregas: ${d.entregas}\n")
        sbDia.append("Km rodados: ${Analisador.numero(d.km)} km\n")
        sbDia.append("Gasolina: ${Analisador.dinheiro(d.gasolina)}\n")
        sbDia.append("Desgaste da moto: ${Analisador.dinheiro(d.desgaste)}\n")
        sbDia.append("Lucro real: ${Analisador.dinheiro(d.lucro)}\n")
        sbDia.append("Horas: ${horasTexto(d.horas)}")
        d.porHora?.let { sbDia.append("\nLucro por hora: ${Analisador.dinheiro(it)}") }
        diaTexto.text = sbDia.toString()

        val hist = Dia.historico(this).take(7)
        historicoTexto.text = if (hist.isEmpty()) {
            "Ainda não tem dias salvos."
        } else {
            hist.joinToString("\n\n") { t ->
                val partes = t.data.split("-")
                val data = if (partes.size == 3) "${partes[2]}/${partes[1]}" else t.data
                val ph = t.porHora?.let { " · ${Analisador.inteiro(it)}/h" } ?: ""
                "$data · ${t.entregas} entregas · ${Analisador.numero(t.km)} km\n" +
                    "Ganho ${Analisador.dinheiro(t.valor)} · gasolina ${Analisador.dinheiro(t.gasolina)} · " +
                    "lucro ${Analisador.dinheiro(t.lucro)}$ph"
            }
        }
    }

    private fun horasTexto(h: Double): String {
        val totalMin = Math.round(h * 60)
        return "${totalMin / 60}h${String.format("%02d", totalMin % 60)}"
    }

    private fun lerCampo(chave: String, padrao: Double): Double {
        val t = campos[chave]?.text?.toString()?.trim()?.replace(",", ".") ?: return padrao
        return t.toDoubleOrNull() ?: padrao
    }

    private fun lerTela(): Config {
        val p = Config.PADRAO
        return Config(
            kml = lerCampo("kml", p.kml),
            gas = lerCampo("gas", p.gas),
            desgaste = lerCampo("desgaste", p.desgaste),
            metaHora = lerCampo("metaHora", p.metaHora),
            tempoExtra = lerCampo("tempoExtra", p.tempoExtra),
            vermelho = lerCampo("vermelho", p.vermelho),
            metaDia = lerCampo("metaDia", p.metaDia),
            kmLonge = lerCampo("kmLonge", p.kmLonge),
            voz = swVoz.isChecked,
            todosApps = swTodos.isChecked
        )
    }

    private fun salvar() {
        Config.salvar(this, lerTela())
        Toast.makeText(this, "Ajustes salvos", Toast.LENGTH_SHORT).show()
    }

    private fun padrao() {
        val p = Config.PADRAO
        campos["kml"]?.setText(fmt(p.kml))
        campos["gas"]?.setText(fmt(p.gas))
        campos["desgaste"]?.setText(fmt(p.desgaste))
        campos["vermelho"]?.setText(fmt(p.vermelho))
        campos["metaHora"]?.setText(fmt(p.metaHora))
        campos["metaDia"]?.setText(fmt(p.metaDia))
        campos["tempoExtra"]?.setText(fmt(p.tempoExtra))
        campos["kmLonge"]?.setText(fmt(p.kmLonge))
        swVoz.isChecked = p.voz
        swTodos.isChecked = p.todosApps
        Config.salvar(this, p)
        Toast.makeText(this, "Ajustes padrão", Toast.LENGTH_SHORT).show()
    }

    private fun testar() {
        salvar()
        val oferta = Analisador.extrair(Analisador.EXEMPLO)
        if (oferta == null) {
            resultado.text = "Erro ao ler o exemplo."
            return
        }
        val r = Analisador.avaliar(oferta, Config.carregar(this))
        val cfgAtual = Config.carregar(this)
        resultado.text = "Exemplo: R$ 10,50 · 1,2 km + 5,2 km · 17 min\n" +
            Analisador.textoFaixa(r, Dia.ler(this), cfgAtual)
        val s = Estado.servico
        if (s != null) {
            s.anunciar(r)
        } else {
            Toast.makeText(this, "Ative o leitor para ver a faixa e ouvir a voz.", Toast.LENGTH_LONG).show()
        }
        atualizarStatus()
    }

    private fun fmt(v: Double): String {
        val s = String.format(java.util.Locale.US, "%.2f", v)
        return s.trimEnd('0').trimEnd('.')
    }

    private fun campo(pai: LinearLayout, chave: String, rotulo: String, valor: Double) {
        pai.addView(texto(rotulo, 13f, corMuted, false).apply { setPadding(0, dp(10), 0, dp(4)) })
        val et = EditText(this).apply {
            setText(fmt(valor))
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setTextColor(corTexto)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0B0D11"))
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.parseColor("#2A2E38"))
            }
        }
        campos[chave] = et
        pai.addView(et)
    }

    private fun texto(t: String, tamanho: Float, cor: Int, negrito: Boolean): TextView =
        TextView(this).apply {
            text = t
            setTextColor(cor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, tamanho)
            if (negrito) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(4), 0, dp(4))
        }

    private fun botao(t: String, cor: Int, acao: () -> Unit): Button =
        Button(this).apply {
            text = t
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (cor == corDestaque) Color.parseColor("#111111") else corTexto)
            background = GradientDrawable().apply {
                setColor(cor)
                cornerRadius = dp(12).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54)
            ).apply { topMargin = dp(12) }
            setOnClickListener { acao() }
        }

    private fun banner(): LinearLayout {
        val caixa = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(18))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#F5A524"), Color.parseColor("#FACC15"))
            ).apply { cornerRadius = dp(18).toFloat() }
        }
        val icone = ImageView(this).apply {
            setImageResource(R.drawable.ic_moto)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#111111"))
            }
            setPadding(dp(9), dp(9), dp(9), dp(9))
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64))
        }
        caixa.addView(icone)
        val textos = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        textos.addView(TextView(this).apply {
            text = "Vale a Pena"
            setTextColor(Color.parseColor("#111111"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = Typeface.DEFAULT_BOLD
        })
        textos.addView(TextView(this).apply {
            text = "Saiba na hora se a entrega compensa"
            setTextColor(Color.parseColor("#3A2E00"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        })
        textos.addView(TextView(this).apply {
            text = "Fazer 150 · Entrega Moto"
            setTextColor(Color.parseColor("#5C4A00"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(4), 0, 0)
        })
        caixa.addView(textos)
        return caixa
    }

    private fun card(conteudo: android.view.View): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply {
                setColor(corCard)
                cornerRadius = dp(14).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
            addView(conteudo)
        }
}
