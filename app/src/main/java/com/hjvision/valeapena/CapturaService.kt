package com.hjvision.valeapena

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Lê a tela por imagem: guarda o quadro mais recente da tela e, quando a 99 muda,
 * passa o quadro por um leitor de texto (OCR) que roda no próprio celular.
 * Usado porque a 99 esconde o texto dos serviços de acessibilidade.
 */
class CapturaService : Service() {

    companion object {
        @Volatile var instancia: CapturaService? = null
        const val EXTRA_CODIGO = "codigo"
        const val EXTRA_DADOS = "dados"
        private const val CANAL = "captura"
        private const val ESCALA = 0.6f
    }

    private var projecao: MediaProjection? = null
    private var leitor: ImageReader? = null
    private var tela: VirtualDisplay? = null
    private val reconhecedor = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val linha = HandlerThread("captura").apply { start() }
    private val fundo = Handler(linha.looper)
    private val principal = Handler(Looper.getMainLooper())
    private val trava = Any()
    private var ultimaImagem: Image? = null

    @Volatile private var ocupado = false
    private var ultimoPedido = 0L
    @Volatile private var inicioLeitura = 0L
    private val energia by lazy { getSystemService(POWER_SERVICE) as android.os.PowerManager }

    // Resumo da última imagem lida, para não ler de novo quando a tela não mudou
    private var ultimoResumo: IntArray? = null
    private var ultimaLeituraFeita = 0L
    private var economiaLidaEm = 0L
    private var economiaCache = false

    /** Modo economia de bateria: lê a tela a cada 1 segundo em vez de meio segundo. */
    private val economia: Boolean
        get() {
            val agora = SystemClock.uptimeMillis()
            if (agora - economiaLidaEm > 3000) {
                economiaCache = Config.carregar(this).economia
                economiaLidaEm = agora
            }
            return economiaCache
        }

    /** Lê a tela uma vez por segundo enquanto a 99 apareceu nos últimos 5 segundos. */
    private val ciclo = object : Runnable {
        override fun run() {
            val agora = SystemClock.uptimeMillis()
            // Se travou esperando uma leitura por muito tempo, libera de novo
            if (ocupado && agora - inicioLeitura > 6000) ocupado = false
            val naTela = Estado.servico?.appNaTela() ?: false
            if (naTela) Estado.visto99 = agora
            val telaLigada = energia.isInteractive
            if (telaLigada && (naTela || agora - Estado.visto99 < 8000)) pedirLeitura()
            principal.postDelayed(this, if (economia) 1500L else 600L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        iniciarPrimeiroPlano()
        if (projecao != null) return START_NOT_STICKY

        val codigo = intent?.getIntExtra(EXTRA_CODIGO, 0) ?: 0
        val dados: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_DADOS, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_DADOS)
        }
        if (dados == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val gerenciador = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val p = try {
            gerenciador.getMediaProjection(codigo, dados)
        } catch (e: Exception) {
            null
        }
        if (p == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        projecao = p
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                principal.post { stopSelf() }
            }
        }, principal)

        val medidas = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(medidas)
        // Copia a tela em 60% do tamanho: os números da oferta são grandes e continuam legíveis,
        // e o celular gasta bem menos pra copiar cada quadro.
        val largura = (medidas.widthPixels * ESCALA).toInt()
        val altura = (medidas.heightPixels * ESCALA).toInt()

        // Sem ouvir cada quadro: a imagem só é pega na hora de ler.
        val r = ImageReader.newInstance(largura, altura, PixelFormat.RGBA_8888, 2)
        leitor = r
        tela = p.createVirtualDisplay(
            "ValeAPena", largura, altura, (medidas.densityDpi * ESCALA).toInt().coerceAtLeast(1),
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, fundo
        )
        instancia = this
        Estado.capturaAtiva = true
        principal.postDelayed(ciclo, 1000)
        return START_NOT_STICKY
    }

    private fun iniciarPrimeiroPlano() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CANAL, "Leitura da tela", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val n: Notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CANAL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
            .setContentTitle("Vale a Pena")
            .setContentText("Lendo as ofertas da 99")
            .setSmallIcon(R.drawable.ic_moto)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, n)
        }
    }

    /** Chamado pelo leitor de acessibilidade quando a tela da 99 muda. */
    fun pedirLeitura() {
        val agora = SystemClock.uptimeMillis()
        if (ocupado || agora - ultimoPedido < (if (economia) 1400 else 500)) return
        ultimoPedido = agora
        inicioLeitura = agora
        ocupado = true
        fundo.post {
            val corte = try {
                capturar()
            } catch (e: Exception) {
                null
            }
            if (corte == null) {
                ocupado = false
                return@post
            }
            val bmp = corte.first
            val topo = corte.second
            val tarefa = try {
                reconhecedor.process(InputImage.fromBitmap(bmp, 0))
            } catch (e: Exception) {
                ocupado = false
                return@post
            }
            tarefa
                .addOnSuccessListener { resultado ->
                    try {
                        val linhas = ArrayList<Linha>()
                        for (bloco in resultado.textBlocks) {
                            for (l in bloco.lines) {
                                val c = l.boundingBox ?: continue
                                val caixa = android.graphics.Rect(
                                    (c.left / ESCALA).toInt(),
                                    ((c.top + topo) / ESCALA).toInt(),
                                    (c.right / ESCALA).toInt(),
                                    ((c.bottom + topo) / ESCALA).toInt()
                                )
                                linhas.add(Linha(l.text, caixa))
                            }
                        }
                        Estado.servico?.receberLinhas(linhas)
                    } finally {
                        ocupado = false
                    }
                }
                .addOnFailureListener { ocupado = false }
        }
    }

    /**
     * Pega a imagem mais nova da tela, só a parte de baixo (a partir de 30% da altura),
     * onde fica o cartão da oferta. Devolve null quando não precisa ler:
     * a tela não mudou desde a última leitura.
     */
    private fun capturar(): Pair<Bitmap, Int>? {
        val img = leitor?.acquireLatestImage() ?: return null  // null: nenhum quadro novo, a tela não mudou
        try {
            val plano = img.planes[0]
            val passoPixel = plano.pixelStride
            val passoLinha = plano.rowStride
            val sobra = passoLinha - passoPixel * img.width
            val bruto = Bitmap.createBitmap(img.width + sobra / passoPixel, img.height, Bitmap.Config.ARGB_8888)
            val buffer = plano.buffer
            buffer.rewind()
            bruto.copyPixelsFromBuffer(buffer)
            val topo = (img.height * 0.30).toInt()
            val recorte = Bitmap.createBitmap(bruto, 0, topo, img.width, img.height - topo)
            if (recorte !== bruto) bruto.recycle()

            // Compara com a última leitura usando uma miniatura. Se quase nada mudou, não lê de novo.
            // Mesmo assim, lê pelo menos a cada 4 segundos, por segurança.
            val resumo = resumir(recorte)
            val anterior = ultimoResumo
            val agora = SystemClock.uptimeMillis()
            if (anterior != null && agora - ultimaLeituraFeita < 4000 && parecido(anterior, resumo)) {
                recorte.recycle()
                return null
            }
            ultimoResumo = resumo
            ultimaLeituraFeita = agora
            return Pair(recorte, topo)
        } finally {
            img.close()
        }
    }

    /** Miniatura de 24 x 40 em tons de cinza. */
    private fun resumir(b: Bitmap): IntArray {
        val mini = Bitmap.createScaledBitmap(b, 24, 40, false)
        val px = IntArray(24 * 40)
        mini.getPixels(px, 0, 24, 0, 0, 24, 40)
        mini.recycle()
        for (i in px.indices) {
            val c = px[i]
            px[i] = (((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)) / 3
        }
        return px
    }

    private fun parecido(a: IntArray, b: IntArray): Boolean {
        var diferentes = 0
        for (i in a.indices) if (Math.abs(a[i] - b[i]) > 24) diferentes++
        return diferentes < a.size / 50  // menos de 2% da miniatura mudou
    }

    override fun onDestroy() {
        principal.removeCallbacks(ciclo)
        instancia = null
        Estado.capturaAtiva = false
        try {
            tela?.release()
        } catch (e: Exception) {
        }
        try {
            projecao?.stop()
        } catch (e: Exception) {
        }
        synchronized(trava) {
            ultimaImagem?.close()
            ultimaImagem = null
        }
        try {
            leitor?.close()
        } catch (e: Exception) {
        }
        linha.quitSafely()
        projecao = null
        super.onDestroy()
    }
}
