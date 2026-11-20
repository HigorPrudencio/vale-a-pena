package com.hjvision.valeapena

import android.accessibilityservice.AccessibilityService
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
        val ordenadas = linhas.sortedBy { it.caixa.top }
        val texto = ordenadas.joinToString("\n") { it.texto }
        Estado.ultimoOcr = texto.take(1200)
        if (texto.contains("Aceitar", ignoreCase = true)) {
            Estado.diagOcr = texto.take(2500)
        }
        val achado = Analisador.extrairOcr(ordenadas) ?: return
        Estado.aceitarRect = achado.second
        processarOferta(achado.first)
    }

    private fun processarOferta(oferta: Oferta) {
        val agora = SystemClock.uptimeMillis()
        val assinatura = "${oferta.valor}|${oferta.kmColeta}|${oferta.kmEntrega}"
        if (assinatura == ultimaAssinatura && agora - horaAssinatura < 60_000) {
            horaUltimaOferta = agora
            return
        }
        ultimaAssinatura = assinatura
        horaAssinatura = agora

        val r = Analisador.avaliar(oferta, cfg)
        ultimaOferta = r
        Estado.ultimaOferta = r
        horaUltimaOferta = agora
        anunciar(r)
    }

    private fun eh99(pkg: String) = pkg.contains("99") || pkg.contains("taxis", ignoreCase = true)

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
        if (!textos.toString().contains("Aceitar", ignoreCase = true) && !tocouNoBotao) return

        val r = ultimaOferta ?: return
        if (agora - horaUltimaOferta > 90_000) return
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
        handler.post { mostrarFaixa(r, texto) }
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

    private fun mostrarFaixa(r: Resultado, conteudo: String) {
        val gerenciador = wm ?: return
        removerFaixa()

        val cor = when (r.nivel) {
            2 -> Color.parseColor("#22C55E")
            1 -> Color.parseColor("#FACC15")
            else -> Color.parseColor("#EF4444")
        }
        val corTexto = if (r.nivel == 0) Color.WHITE else Color.parseColor("#111111")
        val dp = resources.displayMetrics.density

        val tv = TextView(this).apply {
            val linhas = conteudo.split("\n")
            val sp = android.text.SpannableString(conteudo)
            val fimPrimeira = linhas[0].length
            sp.setSpan(android.text.style.RelativeSizeSpan(1.5f), 0, fimPrimeira, 0)
            text = sp
            setTextColor(corTexto)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
            background = GradientDrawable().apply {
                setColor(cor)
                cornerRadius = 18 * dp
            }
            setOnClickListener { removerFaixa() }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            x = 0
            y = (60 * dp).toInt()
            horizontalMargin = 0.03f
        }

        try {
            gerenciador.addView(tv, params)
            faixa = tv
            handler.removeCallbacks(esconder)
            handler.postDelayed(esconder, 15_000)
        } catch (e: Exception) {
            faixa = null
        }
    }

    private fun removerFaixa() {
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
        removerFaixa()
        tts?.shutdown()
        if (Estado.servico === this) Estado.servico = null
        super.onDestroy()
    }
}
