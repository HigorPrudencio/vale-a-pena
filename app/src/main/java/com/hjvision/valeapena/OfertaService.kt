package com.hjvision.valeapena

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.view.ViewGroup
import android.widget.TextView
import java.util.Locale

/** Estado compartilhado com a tela principal (mesmo processo). */
object Estado {
    @Volatile var servico: OfertaService? = null
    @Volatile var ultimoApp: String = "-"
    @Volatile var ultimoTexto: String = ""
    @Volatile var ultimoResultado: String = ""
    @Volatile var ultimaOferta: Resultado? = null
    @Volatile var ultimaSomada: Resultado? = null
    @Volatile var diagnostico: String = ""
    @Volatile var diagAceitar: String = ""
    @Volatile var eventos99: Int = 0
    @Volatile var capturaAtiva: Boolean = false
    @Volatile var aceitarRect: Rect? = null
    @Volatile var diagOcr: String = ""
    @Volatile var faixaRect: Rect? = null
    @Volatile var horaOfertaVista: Long = 0L
    @Volatile var leiturasOcr: Int = 0
    @Volatile var visto99: Long = 0L
    @Volatile var ultimoOcr: String = ""
}

class OfertaService : AccessibilityService(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var ttsPronto = false
    private var wm: WindowManager? = null
    private var faixa: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val esconder = Runnable { removerFaixa() }

    private var ultimaLeitura = 0L
    private var ultimaAssinatura = ""
    private var horaAssinatura = 0L
    private var cfg: Config = Config.PADRAO
    private var cfgLidaEm = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        Estado.servico = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("pt", "BR")
            ttsPronto = true
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkgEvento = event.packageName?.toString() ?: ""
        if (pkgEvento == packageName) return

        val agora = SystemClock.uptimeMillis()
        if (agora - cfgLidaEm > 3000) {
            cfg = Config.carregar(this)
            cfgLidaEm = agora
        }
        val tipo = event.eventType
        val mudouJanelas = tipo == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        val eh99 = eh99(pkgEvento)
        if (!cfg.todosApps && !eh99 && !mudouJanelas) return

        if (tipo == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            verificarAceite(event, agora)
            return
        }

        if (eh99) {
            Estado.visto99 = agora
            CapturaService.instancia?.pedirLeitura()
            if (CapturaService.instancia == null) lembrarLeitura(agora)
        }

        if (agora - ultimaLeitura < 300) return
        ultimaLeitura = agora

        val leitura = lerTela()
        if (leitura.tem99) {
            Estado.visto99 = agora
            CapturaService.instancia?.pedirLeitura()
        }
        if (!leitura.tem99 && !cfg.todosApps) return
        if (eh99) Estado.ultimoApp = pkgEvento
        Estado.eventos99++
        Estado.diagnostico = "Evento: ${nomeEvento(tipo)} de ${pkgEvento.ifBlank { "-" }}\n" +
            leitura.resumo + "\n--- texto (${leitura.texto.length} letras) ---\n" +
            leitura.texto.take(2500)
        val texto = leitura.texto
        if (texto.contains("Aceitar", ignoreCase = true)) Estado.diagAceitar = Estado.diagnostico
        if (texto.isBlank()) return
        if (texto.contains("R$") || texto.contains("R\u00A0$")) Estado.ultimoTexto = texto

        val oferta = Analisador.extrair(texto.replace('\u00A0', ' ')) ?: return
        processarOferta(oferta)
    }

    /** Recebe as linhas lidas da imagem da tela (OCR). */
    fun receberLinhas(linhas: List<Linha>) {
        Estado.leiturasOcr++
        val cartao = Estado.faixaRect
        val ordenadas = linhas
            .filter { cartao == null || !cartao.contains(it.caixa.centerX(), it.caixa.centerY()) }
            .sortedBy { it.caixa.top }
        val texto = ordenadas.joinToString("\n") { it.texto }
        Estado.ultimoOcr = texto.take(1200)
        if (Analisador.temBotaoOferta(texto)) {
            Estado.diagOcr = texto.take(2500)
        }
        val achado = Analisador.extrairOcr(ordenadas)
        if (achado == null) {
            verificarSumico()
            return
        }
        leiturasSemOferta = 0
        Estado.aceitarRect = achado.second
        processarOferta(achado.first)
    }

    private var leiturasSemOferta = 0
    private var perguntada: Resultado? = null
    private var pergunta: View? = null

    /**
     * A oferta sumiu da tela. Se o app não percebeu o toque em Aceitar,
     * pergunta se a pessoa aceitou, pra somar certo no dia.
     */
    private fun verificarSumico() {
        if (!cfg.perguntar) return  // a pergunta é opcional (desligada por padrão)
        val r = ultimaOferta ?: return
        if (Estado.ultimaSomada === r || perguntada === r) return
        leiturasSemOferta++
        val passou = SystemClock.uptimeMillis() - horaUltimaOferta
        if (leiturasSemOferta >= 2 && passou in 1500..60_000) {
            perguntada = r
            handler.post {
                removerFaixa()
                mostrarPergunta(r)
            }
        }
    }

    private fun mostrarPergunta(r: Resultado) {
        val gerenciador = wm ?: return
        removerPergunta()
        val branco = Color.parseColor("#F2F3F5")
        val caixa = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(12))
            background = redondo(Color.parseColor("#F2121418"), 16, 2, Color.parseColor("#F5A524"))
            minimumWidth = dp(170)
        }
        caixa.addView(txt("Aceitou essa entrega?", 15f, branco, true))
        caixa.addView(txt("${Analisador.dinheiro(r.valor)} · ${Analisador.numero(r.km)} km", 13f, Color.parseColor("#9AA0AB")).apply {
            setPadding(0, dp(4), 0, dp(10))
        })
        val botoes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun botao(t: String, cor: Int, primeiro: Boolean, acao: () -> Unit) {
            botoes.addView(txt(t, 15f, Color.WHITE, true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = redondo(cor, 10)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (!primeiro) leftMargin = dp(8)
                }
                setOnClickListener { acao() }
            })
        }
        botao("✓ Sim", Color.parseColor("#22C55E"), true) {
            removerPergunta()
            somarAceite(r)
        }
        botao("✕ Não", Color.parseColor("#3A3F4A"), false) { removerPergunta() }
        caixa.addView(botoes)

        val prefs = getSharedPreferences("cartao", MODE_PRIVATE)
        val alturaTela = resources.displayMetrics.heightPixels
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val larguraTela = resources.displayMetrics.widthPixels
            x = prefs.getInt("x", dp(8)).coerceIn(0, (larguraTela - dp(180)).coerceAtLeast(0))
            y = prefs.getInt("y", (alturaTela * 0.18).toInt()).coerceIn(0, (alturaTela - dp(160)).coerceAtLeast(0))
        }
        try {
            gerenciador.addView(caixa, params)
            pergunta = caixa
            caixa.post { guardarPosicao(caixa) }
            handler.postDelayed({ removerPergunta() }, 15_000)
        } catch (e: Exception) {
            pergunta = null
        }
    }

    private fun removerPergunta() {
        val v = pergunta ?: return
        try {
            wm?.removeView(v)
        } catch (e: Exception) {
        }
        pergunta = null
        if (faixa == null) Estado.faixaRect = null
    }

    private fun processarOferta(oferta: Oferta) {
        val agora = SystemClock.uptimeMillis()
        Estado.horaOfertaVista = agora
        val assinatura = "${oferta.valor}|${oferta.kmColeta}|${oferta.kmEntrega}"
        if (assinatura == ultimaAssinatura && agora - horaAssinatura < 60_000) {
            horaUltimaOferta = agora
            return
        }
        // Oferta nova: some a pergunta da anterior, se ainda estiver na tela
        handler.post { removerPergunta() }
        ultimaAssinatura = assinatura
        horaAssinatura = agora

        val r = Analisador.avaliar(oferta, cfg)
        ultimaOferta = r
        Estado.ultimaOferta = r
        horaUltimaOferta = agora
        anunciar(r)
    }

    private var ultimoLembrete = 0L
    private var lembrete: View? = null

    /**
     * A 99 ou a Uber estão abertas, mas a leitura por imagem está desligada
     * (por exemplo, depois de reiniciar o celular). Mostra um aviso pra ligar.
     * Aparece no máximo uma vez a cada 3 minutos.
     */
    private fun lembrarLeitura(agora: Long) {
        if (agora - ultimoLembrete < 180_000) return
        ultimoLembrete = agora
        handler.post { mostrarLembrete() }
        if (cfg.voz && ttsPronto) {
            tts?.speak("Vale a Pena desligado. Toque no aviso para ligar.", TextToSpeech.QUEUE_FLUSH, null, "lembrete")
        }
    }

    private fun mostrarLembrete() {
        val gerenciador = wm ?: return
        removerLembrete()
        val caixa = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = redondo(Color.parseColor("#F2121418"), 18, 2, Color.parseColor("#F5A524"))
            setOnClickListener {
                removerLembrete()
                try {
                    startActivity(
                        Intent(this@OfertaService, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            .putExtra(MainActivity.EXTRA_LIGAR_IMAGEM, true)
                    )
                } catch (e: Exception) {
                }
            }
        }
        caixa.addView(txt("⚠", 24f, Color.parseColor("#F5A524"), true).apply { setPadding(0, 0, dp(12), 0) })
        val textos = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        textos.addView(txt("Leitura por imagem desligada", 15f, Color.parseColor("#F2F3F5"), true))
        textos.addView(txt("Toque aqui para ligar o Vale a Pena", 13f, Color.parseColor("#9AA0AB")).apply {
            setPadding(0, dp(4), 0, 0)
        })
        caixa.addView(textos)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            y = dp(44)
            horizontalMargin = 0.03f
        }
        try {
            gerenciador.addView(caixa, params)
            lembrete = caixa
            handler.postDelayed({ removerLembrete() }, 12_000)
        } catch (e: Exception) {
            lembrete = null
        }
    }

    private fun removerLembrete() {
        val v = lembrete ?: return
        try {
            wm?.removeView(v)
        } catch (e: Exception) {
        }
        lembrete = null
    }

    /**
     * Um "resumo" da estrutura das janelas da 99 e da Uber (posição das janelas e quantos itens têm).
     * Muda quando aparece uma janela ou cartão novo, como o da oferta, mas não quando só o mapa se mexe.
     */
    fun assinaturaEstrutura(): Int = try {
        var h = 17
        for (w in windows) {
            val raiz = w.root ?: continue
            val pkg = raiz.packageName?.toString() ?: continue
            if (!eh99(pkg)) continue
            val b = Rect()
            w.getBoundsInScreen(b)
            h = h * 31 + b.hashCode()
            h = h * 31 + contarItens(raiz, 0)
        }
        h
    } catch (e: Exception) {
        0
    }

    private fun contarItens(n: AccessibilityNodeInfo?, nivel: Int): Int {
        if (n == null || nivel > 60) return 0
        var total = 1
        for (i in 0 until n.childCount) total += contarItens(n.getChild(i), nivel + 1)
        return total
    }

    /** Confere se a 99 ou a Uber estão na tela agora, sem depender dos avisos delas. */
    fun appNaTela(): Boolean = try {
        windows.any { w ->
            val pkg = w.root?.packageName?.toString() ?: ""
            pkg.isNotEmpty() && eh99(pkg)
        }
    } catch (e: Exception) {
        false
    }

    /** Apps de entrega e corrida que o leitor acompanha: 99 e Uber (motorista). */
    private fun eh99(pkg: String) = pkg == "com.app99.driver" || pkg == "com.ubercab.driver"

    private fun nomeEvento(t: Int): String = when (t) {
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "janela abriu"
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "conteúdo mudou"
        AccessibilityEvent.TYPE_WINDOWS_CHANGED -> "janelas mudaram"
        AccessibilityEvent.TYPE_VIEW_CLICKED -> "clique"
        else -> "tipo $t"
    }

    private var ultimaOferta: Resultado? = null
    private var horaUltimaOferta = 0L

    /** Quando você toca em Aceitar, soma a oferta no total do dia. */
    private fun verificarAceite(event: AccessibilityEvent, agora: Long) {
        val textos = StringBuilder()
        for (t in event.text) textos.append(t).append(' ')
        event.contentDescription?.let { textos.append(it) }
        try {
            event.source?.text?.let { textos.append(' ').append(it) }
        } catch (e: Exception) {
        }
        var tocouNoBotao = false
        val alvo = Estado.aceitarRect
        if (alvo != null) {
            try {
                val fonte = event.source
                if (fonte != null) {
                    val caixa = Rect()
                    fonte.getBoundsInScreen(caixa)
                    tocouNoBotao = Rect.intersects(caixa, alvo) && caixa.height() < alvo.height() * 6
                }
            } catch (e: Exception) {
            }
        }
        if (!Analisador.temBotaoOferta(textos.toString()) && !tocouNoBotao) return

        val r = ultimaOferta ?: return
        if (agora - horaUltimaOferta > 90_000) return
        handler.post { removerPergunta() }
        somarAceite(r)
    }

    /** Soma a entrega aceita no total do dia e fala quanto já fez. */
    private fun somarAceite(r: Resultado) {
        if (Estado.ultimaSomada === r) return
        Estado.ultimaSomada = r

        val antes = Dia.ler(this)
        val depois = Dia.somar(this, r, cfg)
        val meta = cfg.metaDia
        if (cfg.voz && ttsPronto) {
            val frase = if (meta > 0 && antes.valor < meta && depois.valor >= meta) {
                "Meta do dia batida! ${Math.round(depois.valor)} reais."
            } else if (meta > 0 && depois.valor < meta) {
                "Hoje: ${Math.round(depois.valor)} de ${Math.round(meta)} reais."
            } else {
                "Hoje: ${Math.round(depois.valor)} reais."
            }
            tts?.speak(frase, TextToSpeech.QUEUE_ADD, null, "dia")
        }
    }

    /** Usado também pelo botão de teste da tela principal. */
    /** Desliga o leitor de verdade (some da lista de serviços ativos), para usar apps de banco. */
    fun pausar() {
        handler.post { removerFaixa() }
        tts?.stop()
        disableSelf()
    }

    fun anunciar(r: Resultado) {
        val c = Config.carregar(this)
        val texto = Analisador.textoFaixa(r, Dia.ler(this), c)
        Estado.ultimoResultado = texto
        val dia = Dia.ler(this)
        handler.post { mostrarFaixa(r, dia, c) }
        if (c.voz && ttsPronto) {
            tts?.speak(Analisador.fala(r), TextToSpeech.QUEUE_FLUSH, null, "oferta")
        }
    }

    private class Leitura(val texto: String, val resumo: String, val tem99: Boolean)

    private var nos = 0

    /** Lê todas as janelas da tela, menos a do próprio app e a barra do sistema. */
    private fun lerTela(): Leitura {
        val sb = StringBuilder()
        val resumo = StringBuilder()
        var tem99 = false
        try {
            val janelas = windows
            resumo.append("Janelas: ${janelas.size}\n")
            for (w in janelas) {
                val root = w.root
                val pkg = root?.packageName?.toString() ?: "?"
                if (pkg == packageName || pkg == "com.android.systemui") {
                    resumo.append("- tipo ${w.type} $pkg (ignorada)\n")
                    continue
                }
                if (eh99(pkg)) tem99 = true
                nos = 0
                val antes = sb.length
                coletar(root, sb, 0)
                resumo.append("- tipo ${w.type} $pkg: $nos itens, ${sb.length - antes} letras\n")
            }
            if (janelas.isEmpty()) {
                val root = rootInActiveWindow
                val pkg = root?.packageName?.toString() ?: "?"
                if (eh99(pkg)) tem99 = true
                nos = 0
                coletar(root, sb, 0)
                resumo.append("- janela ativa $pkg: $nos itens\n")
            }
        } catch (e: Exception) {
            resumo.append("Erro: ${e.javaClass.simpleName} ${e.message}\n")
        }
        return Leitura(sb.toString(), resumo.toString(), tem99)
    }

    private fun coletar(n: AccessibilityNodeInfo?, sb: StringBuilder, nivel: Int) {
        if (n == null || nivel > 200 || nos > 4000) return
        nos++
        val t = n.text
        if (t != null && t.isNotBlank()) {
            sb.append(t).append('\n')
        }
        val d = n.contentDescription
        if (d != null && d.isNotBlank()) {
            sb.append(d).append('\n')
        }
        for (i in 0 until n.childCount) {
            coletar(n.getChild(i), sb, nivel + 1)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun txt(t: String, sp: Float, cor: Int, negrito: Boolean = false): TextView = TextView(this).apply {
        text = t
        setTextColor(cor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        if (negrito) typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
    }

    private fun redondo(cor: Int, raio: Int, borda: Int = 0, corBorda: Int = 0) = GradientDrawable().apply {
        setColor(cor)
        cornerRadius = dp(raio).toFloat()
        if (borda > 0) setStroke(dp(borda), corBorda)
    }

    /**
     * Cartão compacto e em pé que aparece por cima da oferta.
     * Fica no canto esquerdo, longe do X e dos botões da 99 e da Uber.
     * Dá pra arrastar com o dedo: o app guarda a posição. Um toque rápido fecha.
     */
    private fun mostrarFaixa(r: Resultado, dia: Dia.Total, cfg: Config) {
        val gerenciador = wm ?: return
        removerFaixa()

        val boa = r.nivel == 2
        val cor = if (boa) Color.parseColor("#22C55E") else Color.parseColor("#EF4444")
        val branco = Color.parseColor("#F2F3F5")
        val cinza = Color.parseColor("#9AA0AB")

        val cartao = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = redondo(Color.parseColor("#F2121418"), 16, 2, cor)
            minimumWidth = dp(120)
        }

        // Selo: ✓ BOA ou ✕ RUIM
        cartao.addView(txt(if (boa) "✓ BOA" else "✕ RUIM", 15f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(5), dp(8), dp(5))
            background = redondo(cor, 10)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        })

        // R$ por hora em destaque
        val h = r.porHora
        if (h != null) {
            cartao.addView(txt(if (h < 0) "Prejuízo" else "R$ ${Math.round(h)}/h", 22f, branco, true).apply {
                setPadding(0, dp(8), 0, 0)
            })
            cartao.addView(txt("meta R$ ${Math.round(cfg.metaHora)}/h", 11f, cinza).apply {
                setPadding(0, dp(2), 0, 0)
            })
        }

        // Detalhes, um embaixo do outro
        fun linha(t: String, corTexto: Int = branco, tamanho: Float = 13f, negrito: Boolean = true) {
            cartao.addView(txt(t, tamanho, corTexto, negrito).apply { setPadding(0, dp(6), 0, 0) })
        }
        linha("${Analisador.dinheiro(r.porKm)}/km")
        linha("lucro ${Analisador.dinheiro(r.lucro)}")
        linha("${r.minutos} min · ${Analisador.numero(r.km)} km", cinza, 12f, false)
        if (r.longe) linha("⚠ Entrega longe", Color.parseColor("#FACC15"), 12f, true)

        // Progresso do dia
        if (cfg.metaDia > 0) {
            linha("Hoje: ${Analisador.inteiro(dia.valor)} de ${Analisador.inteiro(cfg.metaDia)}", cinza, 11f, false)
            val pct = (dia.valor / cfg.metaDia).coerceIn(0.0, 1.0).toFloat()
            val trilho = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum = 1f
                background = redondo(Color.parseColor("#262A33"), 2)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)).apply {
                    topMargin = dp(4)
                }
            }
            trilho.addView(View(this).apply {
                background = redondo(Color.parseColor("#F5A524"), 2)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, pct)
            })
            cartao.addView(trilho)
        }

        // Posição: a última que a pessoa escolheu, ou canto esquerdo, a 18% da altura da tela
        val prefs = getSharedPreferences("cartao", MODE_PRIVATE)
        val alturaTela = resources.displayMetrics.heightPixels
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val larguraTela = resources.displayMetrics.widthPixels
            x = prefs.getInt("x", dp(8)).coerceIn(0, (larguraTela - dp(100)).coerceAtLeast(0))
            y = prefs.getInt("y", (alturaTela * 0.18).toInt()).coerceIn(0, (alturaTela - dp(160)).coerceAtLeast(0))
        }

        // Arrastar com o dedo; toque rápido fecha
        var inicioX = 0
        var inicioY = 0
        var toqueX = 0f
        var toqueY = 0f
        var moveu = false
        cartao.setOnTouchListener { v, ev ->
            when (ev.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    inicioX = params.x
                    inicioY = params.y
                    toqueX = ev.rawX
                    toqueY = ev.rawY
                    moveu = false
                    handler.removeCallbacks(esconder)
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - toqueX).toInt()
                    val dy = (ev.rawY - toqueY).toInt()
                    if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) moveu = true
                    if (moveu) {
                        params.x = (inicioX + dx).coerceAtLeast(0)
                        params.y = (inicioY + dy).coerceAtLeast(0)
                        try {
                            gerenciador.updateViewLayout(v, params)
                        } catch (e: Exception) {
                        }
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    if (moveu) {
                        prefs.edit().putInt("x", params.x).putInt("y", params.y).apply()
                        guardarPosicao(v)
                        handler.postDelayed(esconder, 15_000)
                    } else {
                        removerFaixa()
                    }
                    true
                }
                else -> false
            }
        }

        try {
            gerenciador.addView(cartao, params)
            faixa = cartao
            cartao.post { guardarPosicao(cartao) }
            handler.removeCallbacks(esconder)
            handler.postDelayed(esconder, 15_000)
        } catch (e: Exception) {
            faixa = null
        }
    }

    /** Guarda onde o cartão está, para a leitura por imagem ignorar o próprio cartão. */
    private fun guardarPosicao(v: View) {
        val pos = IntArray(2)
        v.getLocationOnScreen(pos)
        Estado.faixaRect = Rect(pos[0], pos[1], pos[0] + v.width, pos[1] + v.height)
    }

    private fun removerFaixa() {
        Estado.faixaRect = null
        val v = faixa ?: return
        try {
            wm?.removeView(v)
        } catch (e: Exception) {
        }
        faixa = null
    }

    override fun onInterrupt() {
        tts?.stop()
    }

    override fun onDestroy() {
        removerPergunta()
        removerLembrete()
        removerFaixa()
        tts?.shutdown()
        if (Estado.servico === this) Estado.servico = null
        super.onDestroy()
    }
}
