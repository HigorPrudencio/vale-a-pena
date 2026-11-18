package com.hjvision.valeapena

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
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
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == packageName) return

        val agora = SystemClock.uptimeMillis()
        if (agora - cfgLidaEm > 3000) {
            cfg = Config.carregar(this)
            cfgLidaEm = agora
        }
        val eh99 = pkg.contains("99") || pkg.contains("taxis", ignoreCase = true)
        if (!cfg.todosApps && !eh99) return

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            verificarAceite(event, agora)
            return
        }

        if (agora - ultimaLeitura < 300) return
        ultimaLeitura = agora

        val texto = lerTela(pkg)
        if (texto.isBlank()) return
        Estado.ultimoApp = pkg
        if (texto.contains("R$")) Estado.ultimoTexto = texto

        val oferta = Analisador.extrair(texto) ?: return
        val assinatura = "${oferta.valor}|${oferta.kmColeta}|${oferta.kmEntrega}"
        if (assinatura == ultimaAssinatura && agora - horaAssinatura < 60_000) return
        ultimaAssinatura = assinatura
        horaAssinatura = agora

        val r = Analisador.avaliar(oferta, cfg)
        ultimaOferta = r
        Estado.ultimaOferta = r
        horaUltimaOferta = agora
        anunciar(r)
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
        if (!textos.toString().contains("Aceitar", ignoreCase = true)) return

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
    fun anunciar(r: Resultado) {
        val c = Config.carregar(this)
        val texto = Analisador.textoFaixa(r, Dia.ler(this), c)
        Estado.ultimoResultado = texto
        handler.post { mostrarFaixa(r, texto) }
        if (c.voz && ttsPronto) {
            tts?.speak(Analisador.fala(r), TextToSpeech.QUEUE_FLUSH, null, "oferta")
        }
    }

    private fun lerTela(pkg: String): String {
        val sb = StringBuilder()
        try {
            val janelas = windows
            var achou = false
            for (w in janelas) {
                val root = w.root ?: continue
                if (root.packageName?.toString() == pkg) {
                    coletar(root, sb, 0)
                    achou = true
                }
            }
            if (!achou) coletar(rootInActiveWindow, sb, 0)
        } catch (e: Exception) {
            // tela mudou no meio da leitura; tenta de novo no próximo evento
        }
        return sb.toString()
    }

    private fun coletar(n: AccessibilityNodeInfo?, sb: StringBuilder, nivel: Int) {
        if (n == null || nivel > 50) return
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
