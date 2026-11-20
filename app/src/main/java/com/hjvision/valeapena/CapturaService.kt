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
            if (naTela || agora - Estado.visto99 < 8000) pedirLeitura()
            principal.postDelayed(this, if (economia) 1000L else 500L)
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
        val largura = medidas.widthPixels
        val altura = medidas.heightPixels

        val r = ImageReader.newInstance(largura, altura, PixelFormat.RGBA_8888, 3)
        r.setOnImageAvailableListener({ leitorAtual ->
            val nova = try {
                leitorAtual.acquireLatestImage()
            } catch (e: Exception) {
                null
            }
            if (nova != null) {
                synchronized(trava) {
                    ultimaImagem?.close()
                    ultimaImagem = nova
                }
            }
        }, fundo)
        leitor = r
        tela = p.createVirtualDisplay(
            "ValeAPena", largura, altura, medidas.densityDpi,
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
        if (ocupado || agora - ultimoPedido < (if (economia) 900 else 400)) return
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
                                val caixa = l.boundingBox ?: continue
                                caixa.offset(0, topo)
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
     * Copia só a parte de baixo da tela (a partir de 30% da altura), onde fica o cartão da oferta.
     * Ler menos imagem deixa o leitor de texto bem mais rápido. Devolve a imagem e onde ela começa.
     */
    private fun capturar(): Pair<Bitmap, Int>? {
        synchronized(trava) {
            val img = ultimaImagem ?: return null
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
            return Pair(recorte, topo)
        }
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
