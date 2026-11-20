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
import android.view.View
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
    private val campos = LinkedHashMap<String, EditText>()
    private lateinit var swVoz: Switch
    private lateinit var swTodos: Switch

    // Status da configuração
    private lateinit var stLeitor: TextView
    private lateinit var stImagem: TextView
    private lateinit var stBateria: TextView
    private lateinit var btLeitor: Button
    private lateinit var btImagem: Button
    private lateinit var btBateria: Button

    // Hoje
    private lateinit var hojeValor: TextView
    private lateinit var hojeMeta: TextView
    private lateinit var barra: View
    private val blocos = HashMap<String, TextView>()

    // Última oferta e histórico
    private lateinit var ofertaCaixa: LinearLayout
    private lateinit var ofertaTexto: TextView
    private lateinit var historicoLista: LinearLayout

    private val corFundo = Color.parseColor("#0F1115")
    private val corCard = Color.parseColor("#1A1D24")
    private val corBloco = Color.parseColor("#22262F")
    private val corTexto = Color.parseColor("#F2F3F5")
    private val corMuted = Color.parseColor("#9AA0AB")
    private val corDestaque = Color.parseColor("#F5A524")
    private val corBotao = Color.parseColor("#2A2E38")
    private val verde = Color.parseColor("#22C55E")
    private val amarelo = Color.parseColor("#FACC15")
    private val vermelho = Color.parseColor("#EF4444")

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = corFundo
        if (Perfil.carregar(this).feito) construirTela() else mostrarCadastro()
    }

    // ===== Cadastro (primeira vez e "Editar cadastro") =====
    private fun mostrarCadastro() {
        val perfil = Perfil.carregar(this)
        val cfg = Config.carregar(this)
        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
            setBackgroundColor(corFundo)
        }
        raiz.addView(banner())
        raiz.addView(texto(
            if (perfil.feito) "Editar cadastro" else "Bem-vindo! Vamos configurar o app pra você.",
            20f, corTexto, true
        ).apply { setPadding(0, dp(18), 0, dp(4)) })
        raiz.addView(texto(
            "Com esses dados o app calcula seus gastos e diz se a entrega vale a pena pra você. Dá pra mudar depois.",
            14f, corMuted, false
        ))

        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val cNome = campoTexto(form, "Seu nome", perfil.nome, "Ex: Higor")
        val cMoto = campoTexto(form, "Sua moto", perfil.moto, "Ex: Yamaha Fazer 150")
        val cKml = campoNumero(form, "Quantos km a moto faz por litro", cfg.kml)
        form.addView(texto(
            "Não sabe? Valores aproximados na cidade: Pop 110 ≈ 50 · Biz 125 ≈ 45 · CG 160 ≈ 40 · Factor 150 ≈ 40 · Fazer 150 ≈ 35 · Bros 160 ≈ 35 · CB 300 ≈ 30",
            12f, corMuted, false
        ))
        val cGas = campoNumero(form, "Preço da gasolina (R$ por litro)", cfg.gas)
        val cMeta = campoNumero(form, "Quanto quer ganhar por dia (R$)", cfg.metaDia)
        val cHoras = campoNumero(form, "Quantas horas quer trabalhar por dia", perfil.horas)
        raiz.addView(card(form))

        val previa = texto("", 15f, corTexto, true)
        raiz.addView(card(previa))
        fun num(e: EditText, padrao: Double) = e.text.toString().trim().replace(",", ".").toDoubleOrNull() ?: padrao
        fun atualizarPrevia() {
            val meta = num(cMeta, 0.0)
            val horas = num(cHoras, 0.0)
            val kml = num(cKml, 0.0)
            val gas = num(cGas, 0.0)
            val sb = StringBuilder()
            if (meta > 0 && horas > 0) {
                sb.append("Sua meta: ${Analisador.dinheiro(meta / horas)} por hora de lucro.\n")
                sb.append("Entregas que pagam isso ficam 🟢 verdes.")
            } else {
                sb.append("Preencha a meta e as horas pra ver sua meta por hora.")
            }
            if (kml > 0 && gas > 0) {
                sb.append("\n\nGasolina: ${Analisador.dinheiro(gas / kml)} por km.")
            }
            previa.text = sb.toString()
        }
        val ouvinte = object : android.text.TextWatcher {
            override fun beforeTextChanged(t: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(t: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(t: android.text.Editable?) { atualizarPrevia() }
        }
        listOf(cKml, cGas, cMeta, cHoras).forEach { it.addTextChangedListener(ouvinte) }
        atualizarPrevia()

        raiz.addView(botao("✓  Confirmar", corDestaque) {
            val kml = num(cKml, 0.0)
            val gas = num(cGas, 0.0)
            val meta = num(cMeta, 0.0)
            val horas = num(cHoras, 0.0)
            val erro = when {
                cNome.text.isBlank() -> "Digite seu nome."
                cMoto.text.isBlank() -> "Digite sua moto."
                kml <= 0 -> "Digite quantos km a moto faz por litro."
                gas <= 0 -> "Digite o preço da gasolina."
                meta <= 0 -> "Digite quanto quer ganhar por dia."
                horas <= 0 || horas > 24 -> "Digite quantas horas quer trabalhar (de 1 a 24)."
                else -> null
            }
            if (erro != null) {
                Toast.makeText(this, erro, Toast.LENGTH_LONG).show()
                return@botao
            }
            Perfil.salvar(this, Perfil(cNome.text.toString().trim(), cMoto.text.toString().trim(), horas, true))
            val metaHora = Math.round(meta / horas * 100) / 100.0
            Config.salvar(this, Config.carregar(this).copy(kml = kml, gas = gas, metaDia = meta, metaHora = metaHora))
            Toast.makeText(this, "Pronto! Sua meta é ${Analisador.dinheiro(metaHora)} por hora.", Toast.LENGTH_LONG).show()
            campos.clear()
            blocos.clear()
            construirTela()
            atualizarStatus()
        })
        if (perfil.feito) {
            raiz.addView(botao("Cancelar", corBotao) {
                campos.clear()
                blocos.clear()
                construirTela()
                atualizarStatus()
            })
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(corFundo)
            addView(raiz)
        })
    }

    private fun campoTexto(pai: LinearLayout, rotulo: String, valor: String, dica: String): EditText {
        pai.addView(texto(rotulo, 13f, corMuted, false).apply { setPadding(0, dp(10), 0, dp(4)) })
        val et = EditText(this).apply {
            setText(valor)
            hint = dica
            setHintTextColor(Color.parseColor("#5B606B"))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setTextColor(corTexto)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0B0D11"))
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.parseColor("#2A2E38"))
            }
        }
        pai.addView(et)
        return et
    }

    private fun campoNumero(pai: LinearLayout, rotulo: String, valor: Double): EditText {
        val chave = "cad_$rotulo"
        campo(pai, chave, rotulo, valor)
        return campos.remove(chave)!!
    }

    private fun construirTela() {
        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
            setBackgroundColor(corFundo)
        }
        status = TextView(this)

        raiz.addView(banner())

        // ===== Configuração =====
        val conf = secao("CONFIGURAÇÃO")
        val l1 = linhaStatus("Leitor de ofertas", "Ativar") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Toque em \"Vale a Pena: leitor de ofertas\" e ative.", Toast.LENGTH_LONG).show()
        }
        stLeitor = l1.second; btLeitor = l1.third; conf.addView(l1.first)
        val l2 = linhaStatus("Leitura por imagem", "Ligar") { ligarImagem() }
        stImagem = l2.second; btImagem = l2.third; conf.addView(l2.first)
        val l3 = linhaStatus("Bateria liberada", "Liberar") {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                Toast.makeText(this, "Procure \"Vale a Pena\" e escolha \"Não otimizar\".", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
        stBateria = l3.second; btBateria = l3.third; conf.addView(l3.first)
        conf.addView(texto(
            "A leitura por imagem precisa ser ligada de novo quando o celular reinicia. Tudo fica no celular: nada é gravado nem enviado.",
            12f, corMuted, false
        ).apply { setPadding(0, dp(8), 0, 0) })
        conf.addView(botao("⏸  Pausar tudo para usar o banco", Color.parseColor("#7F1D1D")) { pausarTudo() })
        conf.addView(texto(
            "Apps de banco bloqueiam apps de fora da Play Store que leem a tela. Antes de abrir o banco, toque em Pausar. Depois, ligue de novo pelos botões Ativar e Ligar.",
            12f, corMuted, false
        ).apply { setPadding(0, dp(8), 0, 0) })
        raiz.addView(card(conf))

        // ===== Hoje =====
        val hoje = secao("HOJE")
        hojeValor = texto("R$ 0,00", 34f, corTexto, true).apply { setPadding(0, 0, 0, 0) }
        hoje.addView(hojeValor)
        hojeMeta = texto("", 14f, corMuted, false)
        hoje.addView(hojeMeta)
        hoje.addView(barraProgresso())
        hoje.addView(gradeBlocos(listOf(
            "entregas" to "Entregas", "km" to "Km rodados",
            "gasolina" to "Gasolina", "desgaste" to "Desgaste",
            "lucro" to "Lucro real", "hora" to "Lucro por hora"
        )))
        val acoes = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, 0)
        }
        acoes.addView(botaoPequeno("+ Somar") { somarUltima() })
        acoes.addView(botaoPequeno("Desfazer") { desfazer() })
        acoes.addView(botaoPequeno("Zerar") { zerar() })
        hoje.addView(acoes)
        hoje.addView(texto(
            "Soma sozinho quando você toca em Aceitar na 99. Use \"+ Somar\" se ele não somar e \"Desfazer\" se a entrega for cancelada.",
            12f, corMuted, false
        ).apply { setPadding(0, dp(8), 0, 0) })
        raiz.addView(card(hoje))

        // ===== Última oferta =====
        ofertaTexto = texto("", 15f, corTexto, true).apply { setPadding(0, 0, 0, 0) }
        ofertaCaixa = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            addView(texto("ÚLTIMA OFERTA", 11f, corMuted, true).apply { setPadding(0, 0, 0, dp(4)) })
            addView(ofertaTexto)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }
        raiz.addView(ofertaCaixa)

        // ===== Dias anteriores =====
        val hist = secao("DIAS ANTERIORES")
        historicoLista = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        hist.addView(historicoLista)
        hist.addView(botao("Copiar histórico para planilha", corBotao) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("historico", Dia.planilha(this)))
            Toast.makeText(this, "Copiado. Cole no Google Planilhas ou Excel.", Toast.LENGTH_LONG).show()
        })
        raiz.addView(card(hist))

        // ===== Ajustes (recolhido) =====
        val cfg = Config.carregar(this)
        val ajustesConteudo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val seta = texto("▼", 14f, corMuted, true)
        val cabecalho = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(texto("⚙  Meus ajustes", 17f, corTexto, true).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(seta)
            setOnClickListener {
                val abrir = ajustesConteudo.visibility != View.VISIBLE
                ajustesConteudo.visibility = if (abrir) View.VISIBLE else View.GONE
                seta.text = if (abrir) "▲" else "▼"
            }
        }
        campo(ajustesConteudo, "metaHora", "Meta de lucro por hora (R$)", cfg.metaHora)
        campo(ajustesConteudo, "metaDia", "Meta do dia (R$)", cfg.metaDia)
        campo(ajustesConteudo, "tempoExtra", "Tempo extra por entrega (min)", cfg.tempoExtra)
        ajustesConteudo.addView(texto(
            "Estacionar, esperar o pedido e entregar. Entra na conta do R$ por hora.",
            12f, corMuted, false
        ))
        campo(ajustesConteudo, "kml", "Consumo da moto (km/l)", cfg.kml)
        campo(ajustesConteudo, "gas", "Gasolina (R$ por litro)", cfg.gas)
        campo(ajustesConteudo, "desgaste", "Desgaste da moto (R$ por km)", cfg.desgaste)
        campo(ajustesConteudo, "vermelho", "Recusar sempre abaixo de (R$ por km)", cfg.vermelho)
        campo(ajustesConteudo, "kmLonge", "Avisar entrega longe a partir de (km)", cfg.kmLonge)
        swVoz = Switch(this).apply {
            text = "Falar o resultado em voz alta"
            setTextColor(corTexto)
            isChecked = cfg.voz
            setPadding(0, dp(12), 0, dp(4))
        }
        ajustesConteudo.addView(swVoz)
        swTodos = Switch(this).apply {
            text = "Modo teste: ler qualquer app"
            setTextColor(corTexto)
            isChecked = cfg.todosApps
            setPadding(0, dp(4), 0, dp(4))
        }
        ajustesConteudo.addView(swTodos)
        ajustesConteudo.addView(botao("Salvar ajustes", corDestaque) { salvar() })
        ajustesConteudo.addView(botao("✎  Editar cadastro (moto, meta, horas)", corBotao) { mostrarCadastro() })
        ajustesConteudo.addView(botao("Voltar ao padrão", corBotao) { padrao() })
        val ajustesBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(cabecalho)
            addView(ajustesConteudo)
        }
        raiz.addView(card(ajustesBox))

        // ===== Ajuda =====
        val ajuda = secao("TESTE E AJUDA")
        ajuda.addView(botao("Testar com oferta de exemplo", corBotao) { testar() })
        resultado = texto("", 14f, corTexto, false)
        ajuda.addView(resultado)
        ajuda.addView(botao("Copiar diagnóstico da 99", corBotao) { copiarDiagnostico() })
        ajuda.addView(botao("Desligar leitura por imagem", corBotao) {
            stopService(Intent(this, CapturaService::class.java))
            Toast.makeText(this, "Leitura por imagem desligada.", Toast.LENGTH_SHORT).show()
            hojeValor.postDelayed({ atualizarStatus() }, 500)
        })
        ajuda.addView(texto(
            "Se uma oferta não for reconhecida, toque em \"Copiar diagnóstico\" logo depois dela e mande o texto para o Claude.",
            12f, corMuted, false
        ).apply { setPadding(0, dp(8), 0, 0) })
        raiz.addView(card(ajuda))

        raiz.addView(texto("Vale a Pena · H.JVision", 12f, corMuted, false).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
        })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(corFundo)
            addView(raiz)
        })
    }

    private fun pausarTudo() {
        stopService(Intent(this, CapturaService::class.java))
        val servico = Estado.servico
        if (servico != null) {
            servico.pausar()
            Estado.servico = null
        }
        Toast.makeText(this, "Pausado. Já pode abrir o banco.", Toast.LENGTH_LONG).show()
        hojeValor.postDelayed({ atualizarStatus() }, 600)
    }

    private fun ligarImagem() {
        if (CapturaService.instancia != null) {
            Toast.makeText(this, "A leitura por imagem já está ligada.", Toast.LENGTH_SHORT).show()
            return
        }
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
        Toast.makeText(this, "Toque em \"Iniciar agora\".", Toast.LENGTH_SHORT).show()
        startActivityForResult(mpm.createScreenCaptureIntent(), PEDIDO_CAPTURA)
    }

    private fun somarUltima() {
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
    }

    private fun desfazer() {
        if (Dia.desfazer(this)) {
            Estado.ultimaSomada = null
            Toast.makeText(this, "Última entrega removida.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Nada para desfazer.", Toast.LENGTH_SHORT).show()
        }
        atualizarStatus()
    }

    private fun zerar() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Zerar hoje?")
            .setMessage("Apaga as entregas somadas hoje. Os outros dias continuam salvos.")
            .setPositiveButton("Zerar") { _, _ ->
                Dia.zerarHoje(this)
                atualizarStatus()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun copiarDiagnostico() {
        val t = "Vale a Pena v1.0\nEventos lidos da 99: ${Estado.eventos99}\n" +
            "Leitura por imagem: ${if (CapturaService.instancia != null) "ligada" else "desligada"} · ${Estado.leiturasOcr} leituras\n\n" +
            "=== IMAGEM COM BOTÃO ACEITAR ===\n${Estado.diagOcr.ifBlank { "(nenhuma)" }}\n\n" +
            "=== ÚLTIMA IMAGEM LIDA ===\n${Estado.ultimoOcr.ifBlank { "(nenhuma)" }}\n\n" +
            "=== LEITURA COM BOTÃO ACEITAR ===\n${Estado.diagAceitar.ifBlank { "(nenhuma)" }}\n\n" +
            "=== ÚLTIMA LEITURA ===\n${Estado.diagnostico.ifBlank { "(nenhuma)" }.take(1500)}"
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("diagnostico", t))
        Toast.makeText(this, "Copiado. Cole na conversa com o Claude.", Toast.LENGTH_LONG).show()
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
        hojeValor.postDelayed({ atualizarStatus() }, 800)
    }

    override fun onResume() {
        super.onResume()
        atualizarStatus()
    }

    private fun atualizarStatus() {
        if (!::hojeValor.isInitialized || !::stLeitor.isInitialized) return
        if (hojeValor.parent == null && stLeitor.parent == null) return
        val leitor = Estado.servico != null
        val imagem = CapturaService.instancia != null
        val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val bateria = pm.isIgnoringBatteryOptimizations(packageName)
        marcar(stLeitor, btLeitor, leitor)
        marcar(stImagem, btImagem, imagem)
        marcar(stBateria, btBateria, bateria)

        val cfg = Config.carregar(this)
        val d = Dia.ler(this)
        hojeValor.text = Analisador.dinheiro(d.valor)
        val falta = cfg.metaDia - d.valor
        hojeMeta.text = if (cfg.metaDia <= 0) {
            "${horasTexto(d.horas)} trabalhadas"
        } else if (falta > 0) {
            "de ${Analisador.inteiro(cfg.metaDia)} · faltam ${Analisador.dinheiro(falta)}"
        } else {
            "Meta de ${Analisador.inteiro(cfg.metaDia)} batida! ✅"
        }
        val pct = if (cfg.metaDia > 0) (d.valor / cfg.metaDia).coerceIn(0.0, 1.0).toFloat() else 0f
        (barra.layoutParams as LinearLayout.LayoutParams).weight = pct
        barra.requestLayout()
        (barra.background as? GradientDrawable)?.setColor(if (pct >= 1f) verde else corDestaque)

        blocos["entregas"]?.text = d.entregas.toString()
        blocos["km"]?.text = "${Analisador.numero(d.km)} km"
        blocos["gasolina"]?.text = Analisador.dinheiro(d.gasolina)
        blocos["desgaste"]?.text = Analisador.dinheiro(d.desgaste)
        blocos["lucro"]?.text = Analisador.dinheiro(d.lucro)
        blocos["hora"]?.text = d.porHora?.let { "${Analisador.dinheiro(it)}/h" } ?: "-"

        val r = Estado.ultimaOferta
        val corOferta = when (r?.nivel) {
            2 -> verde
            1 -> amarelo
            0 -> vermelho
            else -> corCard
        }
        ofertaCaixa.background = GradientDrawable().apply {
            setColor(if (r == null) corCard else corOferta)
            cornerRadius = dp(14).toFloat()
        }
        val corLetra = if (r == null) corMuted else if (r.nivel == 0) Color.WHITE else Color.parseColor("#111111")
        ofertaTexto.setTextColor(corLetra)
        (ofertaCaixa.getChildAt(0) as? TextView)?.setTextColor(if (r == null) corMuted else corLetra)
        ofertaTexto.text = if (r == null) {
            "Nenhuma oferta lida ainda. Fique online na 99."
        } else {
            Estado.ultimoResultado.ifBlank { Analisador.textoFaixa(r, null, cfg) }
        }

        historicoLista.removeAllViews()
        val hist = Dia.historico(this).take(7)
        if (hist.isEmpty()) {
            historicoLista.addView(texto("Ainda não tem dias salvos.", 14f, corMuted, false))
        } else {
            for (t in hist) historicoLista.addView(linhaDia(t))
        }
    }

    private fun marcar(estado: TextView, botao: Button, ok: Boolean) {
        estado.text = if (ok) "●  Ligado" else "●  Desligado"
        estado.setTextColor(if (ok) verde else vermelho)
        botao.visibility = if (ok) View.GONE else View.VISIBLE
    }

    private fun linhaDia(t: Dia.Total): View {
        val partes = t.data.split("-")
        val data = if (partes.size == 3) "${partes[2]}/${partes[1]}" else t.data
        val caixa = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(corBloco)
                cornerRadius = dp(10).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        val topo = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        topo.addView(texto(data, 15f, corTexto, true).apply {
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        topo.addView(texto("lucro ${Analisador.dinheiro(t.lucro)}", 15f, verde, true).apply { setPadding(0, 0, 0, 0) })
        caixa.addView(topo)
        val ph = t.porHora?.let { " · ${Analisador.inteiro(it)}/h" } ?: ""
        caixa.addView(texto(
            "${t.entregas} entregas · ${Analisador.numero(t.km)} km · ganho ${Analisador.dinheiro(t.valor)} · gasolina ${Analisador.dinheiro(t.gasolina)}$ph",
            12f, corMuted, false
        ).apply { setPadding(0, dp(2), 0, 0) })
        return caixa
    }

    private fun secao(titulo: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(texto(titulo, 11f, corMuted, true).apply { setPadding(0, 0, 0, dp(8)) })
    }

    private fun linhaStatus(titulo: String, acaoTexto: String, acao: () -> Unit): Triple<LinearLayout, TextView, Button> {
        val linha = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        val textos = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        textos.addView(texto(titulo, 15f, corTexto, true).apply { setPadding(0, 0, 0, 0) })
        val estado = texto("", 13f, corMuted, false).apply { setPadding(0, dp(2), 0, 0) }
        textos.addView(estado)
        linha.addView(textos)
        val b = Button(this).apply {
            text = acaoTexto
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#111111"))
            background = GradientDrawable().apply {
                setColor(corDestaque)
                cornerRadius = dp(10).toFloat()
            }
            minWidth = dp(90)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40))
            setOnClickListener { acao() }
        }
        linha.addView(b)
        return Triple(linha, estado, b)
    }

    private fun barraProgresso(): View {
        val trilho = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 1f
            background = GradientDrawable().apply {
                setColor(corBloco)
                cornerRadius = dp(5).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10)).apply {
                topMargin = dp(8)
                bottomMargin = dp(12)
            }
        }
        barra = View(this).apply {
            background = GradientDrawable().apply {
                setColor(corDestaque)
                cornerRadius = dp(5).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0f)
        }
        trilho.addView(barra)
        return trilho
    }

    private fun gradeBlocos(itens: List<Pair<String, String>>): LinearLayout {
        val grade = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (par in itens.chunked(2)) {
            val linha = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            par.forEachIndexed { i, (chave, rotulo) ->
                val bloco = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    background = GradientDrawable().apply {
                        setColor(corBloco)
                        cornerRadius = dp(10).toFloat()
                    }
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        bottomMargin = dp(8)
                        if (i == 0) rightMargin = dp(4) else leftMargin = dp(4)
                    }
                }
                bloco.addView(texto(rotulo, 12f, corMuted, false).apply { setPadding(0, 0, 0, 0) })
                val valor = texto("-", 18f, corTexto, true).apply { setPadding(0, dp(2), 0, 0) }
                bloco.addView(valor)
                blocos[chave] = valor
                linha.addView(bloco)
            }
            grade.addView(linha)
        }
        return grade
    }

    private fun botaoPequeno(t: String, acao: () -> Unit): Button = Button(this).apply {
        text = t
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(corTexto)
        background = GradientDrawable().apply {
            setColor(corBotao)
            cornerRadius = dp(10).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            leftMargin = dp(3)
            rightMargin = dp(3)
        }
        setOnClickListener { acao() }
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
            val pf = Perfil.carregar(this@MainActivity)
            text = if (pf.feito) "${pf.nome} · ${pf.moto}" else "Entregas e corridas de moto"
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
