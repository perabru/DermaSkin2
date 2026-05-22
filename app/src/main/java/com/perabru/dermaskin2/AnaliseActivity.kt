package com.perabru.dermaskin2

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

class AnaliseActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    private lateinit var imgPreview: ImageView
    private lateinit var btnCamera: TextView
    private lateinit var btnAnalyze: TextView
    private lateinit var btnExportPdf: TextView
    private lateinit var btnLogout: TextView
    private lateinit var progress: ProgressBar
    private lateinit var resultCard: LinearLayout
    private lateinit var txtRiskTitle: TextView
    private lateinit var txtPercentage: TextView
    private lateinit var txtDescription: TextView
    private lateinit var txtClasses: TextView
    private lateinit var txtABCDE: TextView
    private lateinit var webViewIA: WebView
    private lateinit var edtDiameter: EditText
    private lateinit var checkEvolution: CheckBox

    private var currentBitmap: Bitmap? = null
    private var modelLoaded = false

    private var lastCancerPercent = 0.0
    private var lastNaoCancerPercent = 0.0
    private var lastRiskTitle = ""
    private var lastDescription = ""
    private var lastClasses = ""
    private var lastAbcdeReport = ""
    private var lastSizeReport = ""

    private var lastDiameterMm: Double? = null
    private var lastEstimatedAreaMm2: Double? = null
    private var lastEstimatedAreaPixels = 0
    private var lastEstimatedImagePercent = 0.0
    private var lastEstimatedPixelDiameter = 0.0

    private var pendingPdfBytes: ByteArray? = null

    private val createPdfLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
            if (uri != null) {
                try {
                    val bytes = pendingPdfBytes

                    if (bytes == null) {
                        Toast.makeText(this, "Nenhum PDF foi gerado.", Toast.LENGTH_LONG).show()
                        return@registerForActivityResult
                    }

                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(bytes)
                    }

                    Toast.makeText(this, "Relatório PDF salvo com sucesso!", Toast.LENGTH_LONG).show()

                } catch (e: Exception) {
                    Toast.makeText(this, "Erro ao salvar PDF: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                openCamera()
            } else {
                showError("Permissão da câmera negada.")
            }
        }

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap != null) {
                currentBitmap = bitmap
                imgPreview.setImageBitmap(bitmap)

                btnAnalyze.isEnabled = modelLoaded
                btnAnalyze.alpha = if (modelLoaded) 1.0f else 0.55f

                btnExportPdf.isEnabled = false
                btnExportPdf.alpha = 0.55f

                resultCard.visibility = View.GONE
                limparUltimosResultados()
            } else {
                showError("Nenhuma imagem foi capturada.")
            }
        }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analise)

        auth = FirebaseAuth.getInstance()

        if (auth.currentUser == null) {
            voltarParaLogin()
            return
        }

        iniciarComponentes()
        configurarEstadoInicial()
        configurarWebViewIA()
        configurarCliques()
    }

    override fun onDestroy() {
        try {
            webViewIA.removeJavascriptInterface("Android")
            webViewIA.stopLoading()
            webViewIA.loadUrl("about:blank")
            webViewIA.clearHistory()
            webViewIA.destroy()
        } catch (_: Exception) {
        }

        super.onDestroy()
    }

    private fun iniciarComponentes() {
        imgPreview = findViewById(R.id.imgPreview)
        btnCamera = findViewById(R.id.btnCamera)
        btnAnalyze = findViewById(R.id.btnAnalyze)
        btnExportPdf = findViewById(R.id.btnExportPdf)
        btnLogout = findViewById(R.id.btnLogout)
        progress = findViewById(R.id.progress)
        resultCard = findViewById(R.id.resultCard)
        txtRiskTitle = findViewById(R.id.txtRiskTitle)
        txtPercentage = findViewById(R.id.txtPercentage)
        txtDescription = findViewById(R.id.txtDescription)
        txtClasses = findViewById(R.id.txtClasses)
        txtABCDE = findViewById(R.id.txtABCDE)
        webViewIA = findViewById(R.id.webViewIA)
        edtDiameter = findViewById(R.id.edtDiameter)
        checkEvolution = findViewById(R.id.checkEvolution)
    }

    private fun configurarEstadoInicial() {
        progress.visibility = View.GONE
        resultCard.visibility = View.GONE

        btnAnalyze.isEnabled = false
        btnAnalyze.alpha = 0.55f

        btnExportPdf.isEnabled = false
        btnExportPdf.alpha = 0.55f

        btnCamera.isEnabled = true
        btnCamera.alpha = 1.0f

        btnLogout.isEnabled = true
        btnLogout.alpha = 1.0f

        txtRiskTitle.text = ""
        txtPercentage.text = ""
        txtDescription.text = ""
        txtClasses.text = ""
        txtABCDE.text = ""
    }

    private fun configurarCliques() {
        btnCamera.setOnClickListener {
            showPhotoInstructionDialog()
        }

        btnAnalyze.setOnClickListener {
            val bitmap = currentBitmap

            if (bitmap == null) {
                showError("Tire uma foto antes de analisar.")
                return@setOnClickListener
            }

            if (!modelLoaded) {
                showError("A IA ainda está carregando. Aguarde alguns segundos e tente novamente.")
                return@setOnClickListener
            }

            analyzeImage(bitmap)
        }

        btnExportPdf.setOnClickListener {
            exportPdfReport()
        }

        btnLogout.setOnClickListener {
            confirmarSaida()
        }
    }

    private fun confirmarSaida() {
        AlertDialog.Builder(this)
            .setTitle("Sair da conta")
            .setMessage("Deseja realmente sair e voltar para a tela de login?")
            .setPositiveButton("Sair") { _, _ ->
                auth.signOut()
                Toast.makeText(this, "Você saiu da conta.", Toast.LENGTH_SHORT).show()
                voltarParaLogin()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun voltarParaLogin() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun limparUltimosResultados() {
        lastCancerPercent = 0.0
        lastNaoCancerPercent = 0.0
        lastRiskTitle = ""
        lastDescription = ""
        lastClasses = ""
        lastAbcdeReport = ""
        lastSizeReport = ""

        lastDiameterMm = null
        lastEstimatedAreaMm2 = null
        lastEstimatedAreaPixels = 0
        lastEstimatedImagePercent = 0.0
        lastEstimatedPixelDiameter = 0.0

        pendingPdfBytes = null
    }

    private fun showPhotoInstructionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Foto ideal para análise")
            .setMessage(
                "Para uma análise mais confiável, segure o celular a aproximadamente 10 cm da pele.\n\n" +
                        "Use boa iluminação, evite sombras, mantenha a câmera firme e tente centralizar bem a mancha na imagem.\n\n" +
                        "Evite flash direto, pois ele pode alterar a cor da lesão."
            )
            .setPositiveButton("Entendi, tirar foto") { _, _ ->
                checkCameraPermission()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun checkCameraPermission() {
        val permission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)

        if (permission == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun openCamera() {
        cameraLauncher.launch(null)
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun configurarWebViewIA() {
        webViewIA.setLayerType(View.LAYER_TYPE_SOFTWARE, null)

        webViewIA.settings.javaScriptEnabled = true
        webViewIA.settings.domStorageEnabled = true
        webViewIA.settings.allowFileAccess = true
        webViewIA.settings.allowContentAccess = true
        webViewIA.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        webViewIA.settings.loadsImagesAutomatically = true
        webViewIA.settings.mediaPlaybackRequiresUserGesture = false

        webViewIA.webChromeClient = WebChromeClient()
        webViewIA.addJavascriptInterface(IAResultBridge(), "Android")

        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                
                <script src="https://cdn.jsdelivr.net/npm/@tensorflow/tfjs@3.21.0/dist/tf.min.js"></script>
                <script src="https://cdn.jsdelivr.net/npm/@teachablemachine/image@0.8/dist/teachablemachine-image.min.js"></script>
            </head>
            <body>
                <img id="image" width="224" height="224" style="display:none;" />
                
                <script>
                    const URL_MODELO = "https://teachablemachine.withgoogle.com/models/zceGA0QO5/";
                    let model = null;
                    let isLoaded = false;

                    async function loadModel() {
                        try {
                            const modelURL = URL_MODELO + "model.json";
                            const metadataURL = URL_MODELO + "metadata.json";

                            model = await tmImage.load(modelURL, metadataURL);
                            isLoaded = true;

                            Android.onModelLoaded("Modelo carregado com sucesso.");
                        } catch (error) {
                            Android.onError("Erro ao carregar o modelo: " + error);
                        }
                    }

                    async function predictImage(base64Image) {
                        try {
                            if (!isLoaded || model === null) {
                                Android.onError("Modelo ainda nao carregado.");
                                return;
                            }

                            const img = document.getElementById("image");

                            img.onload = async function () {
                                try {
                                    const prediction = await model.predict(img);
                                    Android.onResult(JSON.stringify(prediction));
                                } catch (error) {
                                    Android.onError("Erro durante a predicao: " + error);
                                }
                            };

                            img.onerror = function () {
                                Android.onError("Erro ao carregar a imagem para analise.");
                            };

                            img.src = "data:image/jpeg;base64," + base64Image;
                        } catch (error) {
                            Android.onError("Erro ao analisar imagem: " + error);
                        }
                    }

                    loadModel();
                </script>
            </body>
            </html>
        """.trimIndent()

        webViewIA.loadDataWithBaseURL(
            "https://teachablemachine.withgoogle.com/",
            html,
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun analyzeImage(bitmap: Bitmap) {
        progress.visibility = View.VISIBLE
        resultCard.visibility = View.GONE

        btnAnalyze.isEnabled = false
        btnAnalyze.alpha = 0.55f

        btnCamera.isEnabled = false
        btnCamera.alpha = 0.55f

        btnExportPdf.isEnabled = false
        btnExportPdf.alpha = 0.55f

        val base64Image = bitmapToBase64(bitmap)

        webViewIA.evaluateJavascript("predictImage('$base64Image');", null)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
        val outputStream = ByteArrayOutputStream()

        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)

        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    inner class IAResultBridge {

        @JavascriptInterface
        fun onModelLoaded(message: String) {
            runOnUiThread {
                modelLoaded = true
                btnAnalyze.isEnabled = currentBitmap != null
                btnAnalyze.alpha = if (currentBitmap != null) 1.0f else 0.55f

                Toast.makeText(
                    this@AnaliseActivity,
                    "IA carregada com sucesso.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        @JavascriptInterface
        fun onResult(jsonResult: String) {
            runOnUiThread {
                progress.visibility = View.GONE

                btnAnalyze.isEnabled = true
                btnAnalyze.alpha = 1.0f

                btnCamera.isEnabled = true
                btnCamera.alpha = 1.0f

                resultCard.visibility = View.VISIBLE
                processResult(jsonResult)
            }
        }

        @JavascriptInterface
        fun onError(error: String) {
            runOnUiThread {
                progress.visibility = View.GONE

                btnAnalyze.isEnabled = currentBitmap != null
                btnAnalyze.alpha = if (currentBitmap != null) 1.0f else 0.55f

                btnCamera.isEnabled = true
                btnCamera.alpha = 1.0f

                btnExportPdf.isEnabled = false
                btnExportPdf.alpha = 0.55f

                showError(error)
            }
        }
    }

    private fun processResult(jsonResult: String) {
        try {
            val array = JSONArray(jsonResult)

            var cancerProbability = 0.0
            var naoCancerProbability = 0.0
            val allResults = StringBuilder()

            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)

                val classNameOriginal = item.getString("className")
                val className = normalizeClassName(classNameOriginal)
                val probability = item.getDouble("probability")
                val percent = probability * 100.0

                allResults.append(classNameOriginal)
                    .append(": ")
                    .append(formatNumber(percent))
                    .append("%\n")

                when (className) {
                    "cancer" -> cancerProbability = probability
                    "naocancer" -> naoCancerProbability = probability
                }
            }

            val cancerPercent = cancerProbability * 100.0
            val naoCancerPercent = naoCancerProbability * 100.0

            lastCancerPercent = cancerPercent
            lastNaoCancerPercent = naoCancerPercent
            lastClasses = allResults.toString()

            txtPercentage.text = "${formatNumber(cancerPercent)}%"

            when {
                cancerPercent >= 70.0 -> {
                    lastRiskTitle = "Risco alto"
                    lastDescription =
                        "A IA identificou alta compatibilidade visual com a classe Cancer. Procure um dermatologista o mais breve possível para avaliação."
                    txtPercentage.setTextColor(Color.parseColor("#D9534F"))
                }

                cancerPercent >= 40.0 -> {
                    lastRiskTitle = "Risco médio"
                    lastDescription =
                        "A IA identificou compatibilidade intermediária com a classe Cancer. Recomenda-se avaliação médica para confirmação."
                    txtPercentage.setTextColor(Color.parseColor("#E6A23C"))
                }

                else -> {
                    lastRiskTitle = "Risco baixo"
                    lastDescription =
                        "A IA identificou baixa compatibilidade com a classe Cancer. Mesmo assim, observe mudanças na pele e procure um médico em caso de dúvida."
                    txtPercentage.setTextColor(Color.parseColor("#5CB85C"))
                }
            }

            txtRiskTitle.text = lastRiskTitle
            txtDescription.text = lastDescription

            lastSizeReport = generateSizeAnalysis(currentBitmap)
            lastAbcdeReport = generateABCDEAnalysis(currentBitmap)

            txtABCDE.text =
                lastAbcdeReport +
                        "\n\nTamanho e diâmetro estimados\n\n" +
                        lastSizeReport

            txtClasses.text =
                "Resultado detalhado da IA:\n\n" +
                        "Cancer: ${formatNumber(cancerPercent)}%\n" +
                        "Nao_Cancer: ${formatNumber(naoCancerPercent)}%\n\n" +
                        "Retorno original:\n$allResults"

            btnExportPdf.isEnabled = true
            btnExportPdf.alpha = 1.0f

        } catch (e: Exception) {
            showError("Erro ao interpretar o resultado da IA: ${e.message}")
        }
    }

    private fun generateSizeAnalysis(bitmap: Bitmap?): String {
        val diameterText = edtDiameter.text.toString().trim()
        val diameter = diameterText.replace(",", ".").toDoubleOrNull()

        lastDiameterMm = diameter

        val estimatedAreaMm2 = if (diameter != null && diameter > 0.0) {
            PI * (diameter / 2.0) * (diameter / 2.0)
        } else {
            null
        }

        lastEstimatedAreaMm2 = estimatedAreaMm2

        if (bitmap == null) {
            return "Não foi possível estimar o tamanho visual sem imagem."
        }

        val resized = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
        val lesionPixels = estimateLesionAreaPixels(resized)
        val totalPixels = resized.width * resized.height

        val imagePercent = (lesionPixels.toDouble() / totalPixels.toDouble()) * 100.0
        val pixelDiameter = 2.0 * sqrt(lesionPixels.toDouble() / PI)

        lastEstimatedAreaPixels = lesionPixels
        lastEstimatedImagePercent = imagePercent
        lastEstimatedPixelDiameter = pixelDiameter

        val diameterClassification = when {
            diameter == null -> "Diâmetro informado: não informado pelo usuário."
            diameter >= 6.0 -> "Diâmetro informado: ${formatNumber(diameter)} mm. Atenção: valor maior ou igual a 6 mm."
            else -> "Diâmetro informado: ${formatNumber(diameter)} mm. Valor menor que 6 mm."
        }

        val areaMmText = if (estimatedAreaMm2 != null) {
            "Área aproximada pela medida informada: ${formatNumber(estimatedAreaMm2)} mm²."
        } else {
            "Área aproximada em mm²: não calculada, pois o diâmetro não foi informado."
        }

        return """
            $diameterClassification
            $areaMmText
            Área visual estimada na foto: $lesionPixels pixels.
            Ocupação aproximada na imagem: ${formatNumber(imagePercent)}%.
            Diâmetro visual estimado na imagem: ${formatNumber(pixelDiameter)} pixels.
        """.trimIndent()
    }

    private fun estimateLesionAreaPixels(bitmap: Bitmap): Int {
        val width = bitmap.width
        val height = bitmap.height

        var totalBrightness = 0.0
        var count = 0

        for (y in 0 until height step 2) {
            for (x in 0 until width step 2) {
                totalBrightness += brightness(bitmap.getPixel(x, y))
                count++
            }
        }

        val averageBrightness = if (count > 0) totalBrightness / count else 0.0
        val threshold = averageBrightness - 20.0

        var lesionPixels = 0

        for (y in 0 until height) {
            for (x in 0 until width) {
                if (brightness(bitmap.getPixel(x, y)) < threshold) {
                    lesionPixels++
                }
            }
        }

        return lesionPixels
    }

    private fun generateABCDEAnalysis(bitmap: Bitmap?): String {
        if (bitmap == null) {
            return "Não foi possível calcular a análise ABCDE sem imagem."
        }

        val resized = Bitmap.createScaledBitmap(bitmap, 224, 224, true)

        val asymmetryScore = calculateAsymmetryScore(resized)
        val borderScore = calculateBorderScore(resized)
        val colorScore = calculateColorVariationScore(resized)

        val diameterText = edtDiameter.text.toString().trim()
        val diameter = diameterText.replace(",", ".").toDoubleOrNull()
        val evolutionDetected = checkEvolution.isChecked

        val asymmetryResult =
            if (asymmetryScore >= 28) "alteração visual relevante (${formatNumber(asymmetryScore)} pontos)"
            else "baixa alteração visual (${formatNumber(asymmetryScore)} pontos)"

        val borderResult =
            if (borderScore >= 35) "bordas visualmente irregulares (${formatNumber(borderScore)} pontos)"
            else "bordas aparentemente regulares (${formatNumber(borderScore)} pontos)"

        val colorResult =
            if (colorScore >= 30) "variação de cor relevante (${formatNumber(colorScore)} pontos)"
            else "baixa variação de cor (${formatNumber(colorScore)} pontos)"

        val diameterResult = when {
            diameter == null -> "não informado"
            diameter >= 6.0 -> "${formatNumber(diameter)} mm, maior ou igual a 6 mm"
            else -> "${formatNumber(diameter)} mm, menor que 6 mm"
        }

        val evolutionResult =
            if (evolutionDetected) "houve relato de mudança/evolução"
            else "sem evolução relatada"

        val score = calculateABCDETotalScore(
            asymmetryScore,
            borderScore,
            colorScore,
            diameter,
            evolutionDetected
        )

        val recommendation = when {
            score >= 4 -> "Atenção alta: recomenda-se procurar um dermatologista o quanto antes."
            score >= 2 -> "Atenção moderada: recomenda-se acompanhar e buscar avaliação profissional."
            else -> "Atenção baixa: manter observação e procurar um médico se houver mudanças."
        }

        return """
            Análise ABCDE
            
            A - Assimetria: $asymmetryResult
            B - Bordas: $borderResult
            C - Cor: $colorResult
            D - Diâmetro: $diameterResult
            E - Evolução: $evolutionResult
            
            Pontuação ABCDE estimada: $score/5
            
            $recommendation
        """.trimIndent()
    }

    private fun calculateABCDETotalScore(
        asymmetryScore: Double,
        borderScore: Double,
        colorScore: Double,
        diameter: Double?,
        evolutionDetected: Boolean
    ): Int {
        var score = 0

        if (asymmetryScore >= 28) score++
        if (borderScore >= 35) score++
        if (colorScore >= 30) score++
        if (diameter != null && diameter >= 6.0) score++
        if (evolutionDetected) score++

        return score
    }

    private fun calculateAsymmetryScore(bitmap: Bitmap): Double {
        val width = bitmap.width
        val height = bitmap.height

        var difference = 0.0
        var count = 0

        for (y in 0 until height) {
            for (x in 0 until width / 2) {
                val leftPixel = bitmap.getPixel(x, y)
                val rightPixel = bitmap.getPixel(width - 1 - x, y)
                difference += colorDistance(leftPixel, rightPixel)
                count++
            }
        }

        return if (count > 0) difference / count else 0.0
    }

    private fun calculateBorderScore(bitmap: Bitmap): Double {
        val width = bitmap.width
        val height = bitmap.height

        var edgeChanges = 0
        var totalChecks = 0

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val center = brightness(bitmap.getPixel(x, y))
                val right = brightness(bitmap.getPixel(x + 1, y))
                val bottom = brightness(bitmap.getPixel(x, y + 1))

                if (abs(center - right) > 45 || abs(center - bottom) > 45) {
                    edgeChanges++
                }

                totalChecks++
            }
        }

        return if (totalChecks > 0) {
            (edgeChanges.toDouble() / totalChecks.toDouble()) * 100.0
        } else {
            0.0
        }
    }

    private fun calculateColorVariationScore(bitmap: Bitmap): Double {
        val width = bitmap.width
        val height = bitmap.height

        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        var count = 0

        for (y in 0 until height step 2) {
            for (x in 0 until width step 2) {
                val pixel = bitmap.getPixel(x, y)
                sumR += Color.red(pixel)
                sumG += Color.green(pixel)
                sumB += Color.blue(pixel)
                count++
            }
        }

        if (count == 0) return 0.0

        val avgR = sumR / count
        val avgG = sumG / count
        val avgB = sumB / count

        var variation = 0.0

        for (y in 0 until height step 2) {
            for (x in 0 until width step 2) {
                val pixel = bitmap.getPixel(x, y)

                val dr = Color.red(pixel) - avgR
                val dg = Color.green(pixel) - avgG
                val db = Color.blue(pixel) - avgB

                variation += sqrt((dr * dr) + (dg * dg) + (db * db))
            }
        }

        return variation / count
    }

    private fun brightness(pixel: Int): Int {
        return ((Color.red(pixel) * 0.299) +
                (Color.green(pixel) * 0.587) +
                (Color.blue(pixel) * 0.114)).roundToInt()
    }

    private fun colorDistance(pixel1: Int, pixel2: Int): Double {
        val r = Color.red(pixel1) - Color.red(pixel2)
        val g = Color.green(pixel1) - Color.green(pixel2)
        val b = Color.blue(pixel1) - Color.blue(pixel2)

        return sqrt((r * r + g * g + b * b).toDouble())
    }

    private fun exportPdfReport() {
        try {
            if (lastRiskTitle.isBlank()) {
                Toast.makeText(this, "Realize uma análise antes de gerar o PDF.", Toast.LENGTH_LONG).show()
                return
            }

            val pdfBytes = generatePdfBytes()
            pendingPdfBytes = pdfBytes

            val fileName = "relatorio_dermaskin_${System.currentTimeMillis()}.pdf"
            createPdfLauncher.launch(fileName)

        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao gerar PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun generatePdfBytes(): ByteArray {
        val pdfDocument = PdfDocument()

        val pageWidth = 595
        val pageHeight = 842

        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)

        drawPdfContent(page.canvas)

        pdfDocument.finishPage(page)

        val outputStream = ByteArrayOutputStream()
        pdfDocument.writeTo(outputStream)
        pdfDocument.close()

        return outputStream.toByteArray()
    }

    private fun drawPdfContent(canvas: Canvas) {
        val titlePaint = Paint().apply {
            color = Color.parseColor("#5A2F20")
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val subtitlePaint = Paint().apply {
            color = Color.parseColor("#8B4A2F")
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val normalPaint = Paint().apply {
            color = Color.parseColor("#2E1B13")
            textSize = 11.5f
            isAntiAlias = true
        }

        val smallPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 10f
            isAntiAlias = true
        }

        val cardPaint = Paint().apply {
            color = Color.parseColor("#FFF8F2")
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val borderPaint = Paint().apply {
            color = Color.parseColor("#D8B6A4")
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }

        val riskPaint = Paint().apply {
            color = when {
                lastCancerPercent >= 70 -> Color.parseColor("#D9534F")
                lastCancerPercent >= 40 -> Color.parseColor("#E6A23C")
                else -> Color.parseColor("#5CB85C")
            }

            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        canvas.drawColor(Color.WHITE)

        canvas.drawText("DermaSkin", 40f, 55f, titlePaint)
        canvas.drawText("Relatório de triagem visual com inteligência artificial", 40f, 80f, subtitlePaint)

        val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date())
        canvas.drawText("Data da análise: $date", 40f, 105f, normalPaint)

        canvas.drawRoundRect(35f, 125f, 560f, 255f, 22f, 22f, cardPaint)
        canvas.drawRoundRect(35f, 125f, 560f, 255f, 22f, 22f, borderPaint)

        canvas.drawText("Resultado principal", 55f, 155f, subtitlePaint)
        canvas.drawText(lastRiskTitle, 55f, 185f, titlePaint)
        canvas.drawText("${formatNumber(lastCancerPercent)}%", 380f, 190f, riskPaint)

        drawMultilineText(
            canvas,
            "Chance estimada para a classe Cancer segundo o modelo treinado.",
            55f,
            220f,
            normalPaint,
            75
        )

        currentBitmap?.let { bitmap ->
            val image = Bitmap.createScaledBitmap(bitmap, 120, 120, true)
            canvas.drawBitmap(image, 415f, 275f, null)
            canvas.drawText("Imagem analisada", 417f, 410f, smallPaint)
        }

        canvas.drawText("Análise ABCDE", 40f, 290f, subtitlePaint)
        drawMultilineText(canvas, lastAbcdeReport, 40f, 315f, normalPaint, 78)

        canvas.drawText("Tamanho e diâmetro", 40f, 505f, subtitlePaint)
        drawMultilineText(canvas, lastSizeReport, 40f, 530f, normalPaint, 82)

        canvas.drawText("Resultado detalhado da IA", 40f, 640f, subtitlePaint)

        val classText =
            "Cancer: ${formatNumber(lastCancerPercent)}%\n" +
                    "Nao_Cancer: ${formatNumber(lastNaoCancerPercent)}%"

        drawMultilineText(canvas, classText, 40f, 665f, normalPaint, 80)

        canvas.drawText("Orientação para captura da imagem", 40f, 710f, subtitlePaint)

        drawMultilineText(
            canvas,
            "A foto deve ser feita com o celular a aproximadamente 10 cm da pele, com boa iluminação, sem sombra e sem flash direto.",
            40f,
            735f,
            normalPaint,
            90
        )

        canvas.drawText("Observação importante", 40f, 770f, subtitlePaint)

        val warning =
            "Este relatório não possui valor diagnóstico e não substitui consulta médica. " +
                    "A análise é apenas uma triagem computacional baseada em imagem e no modelo treinado. " +
                    "Em caso de suspeita, mudança na pele, dor, sangramento, coceira ou crescimento da lesão, procure um dermatologista."

        drawMultilineText(canvas, warning, 40f, 795f, normalPaint, 90)

        canvas.drawText("Gerado pelo aplicativo DermaSkin", 40f, 830f, smallPaint)
    }

    private fun drawMultilineText(
        canvas: Canvas,
        text: String,
        x: Float,
        startY: Float,
        paint: Paint,
        maxCharsPerLine: Int
    ) {
        var y = startY
        val lines = mutableListOf<String>()

        text.split("\n").forEach { paragraph ->
            if (paragraph.length <= maxCharsPerLine) {
                lines.add(paragraph)
            } else {
                var currentLine = ""

                paragraph.split(" ").forEach { word ->
                    val testLine = (currentLine + " " + word).trim()

                    if (testLine.length <= maxCharsPerLine) {
                        currentLine = testLine
                    } else {
                        if (currentLine.isNotBlank()) lines.add(currentLine)
                        currentLine = word
                    }
                }

                if (currentLine.isNotBlank()) lines.add(currentLine)
            }
        }

        for (line in lines) {
            canvas.drawText(line, x, y, paint)
            y += 16f
        }
    }

    private fun normalizeClassName(name: String): String {
        return name
            .lowercase(Locale.ROOT)
            .replace(" ", "")
            .replace("_", "")
            .replace("-", "")
            .replace("ã", "a")
            .replace("á", "a")
            .replace("â", "a")
            .replace("é", "e")
            .replace("ê", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ô", "o")
            .replace("ú", "u")
            .replace("ç", "c")
    }

    private fun formatNumber(value: Double): String {
        return String.format(Locale.US, "%.2f", value)
    }

    private fun showError(message: String) {
        resultCard.visibility = View.VISIBLE

        txtRiskTitle.text = "Atenção"
        txtPercentage.text = "!"
        txtPercentage.setTextColor(Color.parseColor("#D9534F"))
        txtDescription.text = message
        txtClasses.text = ""
        txtABCDE.text = ""

        btnExportPdf.isEnabled = false
        btnExportPdf.alpha = 0.55f
    }
}