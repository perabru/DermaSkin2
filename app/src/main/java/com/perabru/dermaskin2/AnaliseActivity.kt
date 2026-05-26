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
import android.widget.Button
import android.widget.CheckBox
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

    private lateinit var btnCamera: Button
    private lateinit var btnAnalyze: Button
    private lateinit var btnExportPdf: Button
    private lateinit var btnLogout: Button

    private lateinit var progress: ProgressBar

    private lateinit var riskCard: LinearLayout
    private lateinit var sizeCard: LinearLayout
    private lateinit var resultCard: LinearLayout

    private lateinit var txtRiskTitle: TextView
    private lateinit var txtPercentage: TextView
    private lateinit var txtRiskExplanation: TextView
    private lateinit var txtSizeReport: TextView
    private lateinit var txtDescription: TextView
    private lateinit var txtABCDE: TextView
    private lateinit var txtClasses: TextView

    private lateinit var webViewIA: WebView
    private lateinit var checkEvolution: CheckBox

    private var currentBitmap: Bitmap? = null
    private var modelLoaded = false

    private var lastCancerPercent = 0.0
    private var lastNaoCancerPercent = 0.0

    private var lastRiskTitle = ""
    private var lastRiskExplanation = ""
    private var lastDescription = ""
    private var lastAbcdeReport = ""
    private var lastSizeReport = ""
    private var lastClasses = ""

    private var lastAsymmetryScore = 0.0
    private var lastBorderScore = 0.0
    private var lastColorScore = 0.0
    private var lastEvolutionDetected = false

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

                    Toast.makeText(this, "PDF salvo com sucesso!", Toast.LENGTH_LONG).show()

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

                riskCard.visibility = View.GONE
                sizeCard.visibility = View.GONE
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
            if (::webViewIA.isInitialized) {
                webViewIA.removeJavascriptInterface("Android")
                webViewIA.stopLoading()
                webViewIA.loadUrl("about:blank")
                webViewIA.clearHistory()
                webViewIA.destroy()
            }
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

        riskCard = findViewById(R.id.riskCard)
        sizeCard = findViewById(R.id.sizeCard)
        resultCard = findViewById(R.id.resultCard)

        txtRiskTitle = findViewById(R.id.txtRiskTitle)
        txtPercentage = findViewById(R.id.txtPercentage)
        txtRiskExplanation = findViewById(R.id.txtRiskExplanation)
        txtSizeReport = findViewById(R.id.txtSizeReport)
        txtDescription = findViewById(R.id.txtDescription)
        txtABCDE = findViewById(R.id.txtABCDE)
        txtClasses = findViewById(R.id.txtClasses)

        webViewIA = findViewById(R.id.webViewIA)
        checkEvolution = findViewById(R.id.checkEvolution)
    }

    private fun configurarEstadoInicial() {
        progress.visibility = View.GONE

        riskCard.visibility = View.GONE
        sizeCard.visibility = View.GONE
        resultCard.visibility = View.GONE

        btnAnalyze.isEnabled = false
        btnAnalyze.alpha = 0.55f

        btnExportPdf.isEnabled = false
        btnExportPdf.alpha = 0.55f

        btnCamera.isEnabled = true
        btnCamera.alpha = 1.0f

        btnLogout.isEnabled = true
        btnLogout.alpha = 1.0f
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
        lastRiskExplanation = ""
        lastDescription = ""
        lastAbcdeReport = ""
        lastSizeReport = ""
        lastClasses = ""

        lastAsymmetryScore = 0.0
        lastBorderScore = 0.0
        lastColorScore = 0.0
        lastEvolutionDetected = false

        lastEstimatedAreaPixels = 0
        lastEstimatedImagePercent = 0.0
        lastEstimatedPixelDiameter = 0.0

        pendingPdfBytes = null
    }

    private fun showPhotoInstructionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Foto ideal para análise")
            .setMessage(
                "Para melhorar a análise:\n\n" +
                        "• Posicione a mancha no centro da imagem.\n" +
                        "• Use boa iluminação.\n" +
                        "• Evite sombra e flash direto.\n" +
                        "• Mantenha a câmera firme.\n" +
                        "• Aproxime a câmera sem perder o foco.\n\n" +
                        "A marcação circular da tela serve como guia para centralizar a mancha antes da foto.\n\n" +
                        "A análise é apenas uma triagem e não substitui avaliação médica."
            )
            .setPositiveButton("Tirar foto") { _, _ ->
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
                                Android.onError("Modelo ainda não carregado.");
                                return;
                            }

                            const img = document.getElementById("image");

                            img.onload = async function () {
                                try {
                                    const prediction = await model.predict(img);
                                    Android.onResult(JSON.stringify(prediction));
                                } catch (error) {
                                    Android.onError("Erro durante a predição: " + error);
                                }
                            };

                            img.onerror = function () {
                                Android.onError("Erro ao carregar a imagem para análise.");
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

        riskCard.visibility = View.GONE
        sizeCard.visibility = View.GONE
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

            val bitmap = currentBitmap

            if (bitmap != null) {
                lastAbcdeReport = generateABCDEAnalysis(bitmap)
                lastSizeReport = generateSizeAnalysis(bitmap)
            } else {
                lastAbcdeReport = "Não foi possível calcular a análise visual sem imagem."
                lastSizeReport = "Não foi possível calcular o tamanho aproximado sem imagem."
            }

            definirRisco(cancerPercent)

            txtRiskTitle.text = lastRiskTitle
            txtPercentage.text = "${formatNumber(cancerPercent)}%"
            txtRiskExplanation.text = lastRiskExplanation

            txtSizeReport.text = lastSizeReport

            txtDescription.text = lastDescription
            txtABCDE.text = lastAbcdeReport

            txtClasses.text =
                "Resultado detalhado da IA:\n\n" +
                        "Classe Cancer: ${formatNumber(cancerPercent)}%\n" +
                        "Classe Nao_Cancer: ${formatNumber(naoCancerPercent)}%\n\n" +
                        "Retorno original do modelo:\n$allResults"

            riskCard.visibility = View.VISIBLE
            sizeCard.visibility = View.VISIBLE
            resultCard.visibility = View.VISIBLE

            btnExportPdf.isEnabled = true
            btnExportPdf.alpha = 1.0f

        } catch (e: Exception) {
            showError("Erro ao interpretar o resultado da IA: ${e.message}")
        }
    }

    private fun definirRisco(cancerPercent: Double) {
        val evolutionText = if (lastEvolutionDetected) {
            "Além disso, foi marcado que houve mudança recente na mancha, o que aumenta a atenção."
        } else {
            "Não foi marcada evolução recente da mancha, o que reduz um pouco o nível de alerta visual."
        }

        val sizeText = when {
            lastEstimatedImagePercent >= 25.0 ->
                "A mancha também ocupou uma área visual grande dentro da foto."

            lastEstimatedImagePercent >= 10.0 ->
                "A mancha ocupou uma área visual moderada dentro da foto."

            lastEstimatedImagePercent > 0.0 ->
                "A mancha ocupou uma área visual pequena dentro da foto."

            else ->
                "O tamanho visual da mancha não pôde ser estimado com segurança."
        }

        when {
            cancerPercent >= 70.0 -> {
                lastRiskTitle = "Risco alto"

                lastDescription =
                    "A imagem apresentou alta compatibilidade com a classe Cancer no modelo de inteligência artificial. Esse resultado não confirma diagnóstico, mas indica necessidade de avaliação dermatológica."

                lastRiskExplanation =
                    "O risco foi considerado alto porque a IA atribuiu ${formatNumber(cancerPercent)}% de compatibilidade com a classe Cancer. " +
                            "Também foram avaliados sinais visuais como assimetria, bordas, variação de cor, evolução relatada e área aproximada da mancha. " +
                            "$evolutionText $sizeText\n\n" +
                            "Procure um dermatologista o quanto antes para avaliação profissional."

                txtPercentage.setTextColor(Color.parseColor("#D9534F"))
            }

            cancerPercent >= 40.0 -> {
                lastRiskTitle = "Risco médio"

                lastDescription =
                    "A imagem apresentou compatibilidade intermediária com a classe Cancer. Isso significa que existem sinais visuais que merecem atenção, mas que não podem ser interpretados como diagnóstico."

                lastRiskExplanation =
                    "O risco foi considerado médio porque a IA encontrou ${formatNumber(cancerPercent)}% de compatibilidade com a classe Cancer. " +
                            "Esse percentual indica dúvida visual do modelo, principalmente quando combinado com alterações de cor, borda, assimetria, evolução ou tamanho visual da mancha. " +
                            "$evolutionText $sizeText\n\n" +
                            "Recomenda-se acompanhar a lesão e buscar avaliação médica."

                txtPercentage.setTextColor(Color.parseColor("#E6A23C"))
            }

            else -> {
                lastRiskTitle = "Risco baixo"

                lastDescription =
                    "A imagem apresentou baixa compatibilidade com a classe Cancer no modelo de inteligência artificial. Mesmo assim, qualquer mudança na pele deve ser observada."

                lastRiskExplanation =
                    "O risco foi considerado baixo porque a IA atribuiu apenas ${formatNumber(cancerPercent)}% de compatibilidade com a classe Cancer. " +
                            "A análise complementar observou assimetria, bordas, cor, evolução e tamanho visual aproximado. " +
                            "$evolutionText $sizeText\n\n" +
                            "Se a mancha mudar, coçar, sangrar ou crescer, procure um dermatologista."

                txtPercentage.setTextColor(Color.parseColor("#5CB85C"))
            }
        }
    }

    private fun generateSizeAnalysis(bitmap: Bitmap): String {
        val resized = Bitmap.createScaledBitmap(bitmap, 224, 224, true)

        val lesionPixels = estimateLesionAreaPixels(resized)
        val totalPixels = resized.width * resized.height

        val imagePercent = if (totalPixels > 0) {
            (lesionPixels.toDouble() / totalPixels.toDouble()) * 100.0
        } else {
            0.0
        }

        val pixelDiameter = if (lesionPixels > 0) {
            2.0 * sqrt(lesionPixels.toDouble() / PI)
        } else {
            0.0
        }

        lastEstimatedAreaPixels = lesionPixels
        lastEstimatedImagePercent = imagePercent
        lastEstimatedPixelDiameter = pixelDiameter

        val classification = when {
            imagePercent >= 25.0 ->
                "A mancha ocupa uma área grande dentro da imagem capturada."

            imagePercent >= 10.0 ->
                "A mancha ocupa uma área moderada dentro da imagem capturada."

            imagePercent > 0.0 ->
                "A mancha ocupa uma área pequena dentro da imagem capturada."

            else ->
                "Não foi possível estimar a área da mancha com segurança."
        }

        return """
            Área visual estimada: $lesionPixels pixels.
            Ocupação aproximada na foto: ${formatNumber(imagePercent)}%.
            Diâmetro visual aproximado: ${formatNumber(pixelDiameter)} pixels.
            
            Interpretação:
            $classification
            
            Observação:
            Esse cálculo é aproximado e depende da distância da câmera, iluminação, foco e centralização da imagem. Para medir em milímetros com precisão, seria necessário usar uma régua, moeda ou objeto de referência na foto.
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

        val averageBrightness = if (count > 0) {
            totalBrightness / count
        } else {
            0.0
        }

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

    private fun generateABCDEAnalysis(bitmap: Bitmap): String {
        val resized = Bitmap.createScaledBitmap(bitmap, 224, 224, true)

        val asymmetryScore = calculateAsymmetryScore(resized)
        val borderScore = calculateBorderScore(resized)
        val colorScore = calculateColorVariationScore(resized)
        val evolutionDetected = checkEvolution.isChecked

        lastAsymmetryScore = asymmetryScore
        lastBorderScore = borderScore
        lastColorScore = colorScore
        lastEvolutionDetected = evolutionDetected

        val asymmetryResult =
            if (asymmetryScore >= 28) {
                "sinal visual de assimetria relevante"
            } else {
                "baixa assimetria visual"
            }

        val borderResult =
            if (borderScore >= 35) {
                "bordas visualmente irregulares"
            } else {
                "bordas aparentemente regulares"
            }

        val colorResult =
            if (colorScore >= 30) {
                "variação de cor relevante"
            } else {
                "baixa variação de cor"
            }

        val evolutionResult =
            if (evolutionDetected) {
                "houve relato de mudança recente"
            } else {
                "sem mudança recente relatada"
            }

        val score = calculateABCDETotalScore(
            asymmetryScore,
            borderScore,
            colorScore,
            evolutionDetected
        )

        val recommendation = when {
            score >= 4 ->
                "Atenção alta: a combinação dos sinais visuais sugere buscar avaliação dermatológica o quanto antes."

            score >= 2 ->
                "Atenção moderada: acompanhe a lesão e considere procurar um dermatologista."

            else ->
                "Atenção baixa: mantenha observação e procure um médico se houver mudanças."
        }

        return """
            Análise visual complementar
            
            A - Assimetria: $asymmetryResult.
            B - Bordas: $borderResult.
            C - Cor: $colorResult.
            D - Diâmetro: estimado visualmente no card de tamanho.
            E - Evolução: $evolutionResult.
            
            Pontuação visual estimada: $score/4
            
            $recommendation
        """.trimIndent()
    }

    private fun calculateABCDETotalScore(
        asymmetryScore: Double,
        borderScore: Double,
        colorScore: Double,
        evolutionDetected: Boolean
    ): Int {
        var score = 0

        if (asymmetryScore >= 28) score++
        if (borderScore >= 35) score++
        if (colorScore >= 30) score++
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
        val pageWidth = 595f

        val titlePaint = Paint().apply {
            color = Color.parseColor("#4A2A1A")
            textSize = 30f
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
            textSize = 10.5f
            isAntiAlias = true
        }

        val smallPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 9f
            isAntiAlias = true
        }

        val whitePaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val creamPaint = Paint().apply {
            color = Color.parseColor("#FFF8F2")
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val borderPaint = Paint().apply {
            color = Color.parseColor("#E0BFAE")
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

            textSize = 38f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        canvas.drawColor(Color.parseColor("#F7EFE8"))

        canvas.drawRoundRect(30f, 30f, pageWidth - 30f, 115f, 24f, 24f, whitePaint)
        canvas.drawText("DermaSkin", 50f, 66f, titlePaint)
        canvas.drawText("Relatório profissional de triagem visual com inteligência artificial", 50f, 92f, subtitlePaint)

        val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date())
        canvas.drawText("Data: $date", 420f, 66f, smallPaint)

        canvas.drawRoundRect(30f, 135f, pageWidth - 30f, 255f, 24f, 24f, creamPaint)
        canvas.drawRoundRect(30f, 135f, pageWidth - 30f, 255f, 24f, 24f, borderPaint)

        canvas.drawText("Chance de risco", 50f, 165f, subtitlePaint)
        canvas.drawText(lastRiskTitle, 50f, 200f, titlePaint)
        canvas.drawText("${formatNumber(lastCancerPercent)}%", 390f, 205f, riskPaint)

        drawMultilineText(
            canvas,
            lastRiskExplanation,
            50f,
            225f,
            normalPaint,
            92
        )

        canvas.drawRoundRect(30f, 275f, pageWidth - 30f, 425f, 24f, 24f, whitePaint)
        canvas.drawRoundRect(30f, 275f, pageWidth - 30f, 425f, 24f, 24f, borderPaint)

        canvas.drawText("Tamanho aproximado da mancha", 50f, 305f, subtitlePaint)

        val sizePdfText =
            "Área visual estimada: $lastEstimatedAreaPixels pixels.\n" +
                    "Ocupação aproximada na foto: ${formatNumber(lastEstimatedImagePercent)}%.\n" +
                    "Diâmetro visual aproximado: ${formatNumber(lastEstimatedPixelDiameter)} pixels.\n" +
                    "Observação: estimativa dependente de distância, iluminação, foco e centralização."

        drawMultilineText(
            canvas,
            sizePdfText,
            50f,
            330f,
            normalPaint,
            88
        )

        currentBitmap?.let { bitmap ->
            val image = Bitmap.createScaledBitmap(bitmap, 105, 105, true)
            canvas.drawBitmap(image, 430f, 305f, null)
            canvas.drawText("Imagem analisada", 430f, 420f, smallPaint)
        }

        canvas.drawRoundRect(30f, 445f, pageWidth - 30f, 620f, 24f, 24f, creamPaint)
        canvas.drawRoundRect(30f, 445f, pageWidth - 30f, 620f, 24f, 24f, borderPaint)

        canvas.drawText("Resultado da análise", 50f, 475f, subtitlePaint)

        drawMultilineText(
            canvas,
            lastDescription,
            50f,
            500f,
            normalPaint,
            88
        )

        canvas.drawText("Análise visual complementar", 50f, 565f, subtitlePaint)

        drawMultilineText(
            canvas,
            lastAbcdeReport,
            50f,
            590f,
            normalPaint,
            88
        )

        canvas.drawRoundRect(30f, 640f, pageWidth - 30f, 735f, 24f, 24f, whitePaint)
        canvas.drawRoundRect(30f, 640f, pageWidth - 30f, 735f, 24f, 24f, borderPaint)

        canvas.drawText("Resultado detalhado do modelo", 50f, 670f, subtitlePaint)

        val detailText =
            "Classe Cancer: ${formatNumber(lastCancerPercent)}% | " +
                    "Classe Nao_Cancer: ${formatNumber(lastNaoCancerPercent)}%\n" +
                    "Assimetria: ${formatNumber(lastAsymmetryScore)} pontos | " +
                    "Bordas: ${formatNumber(lastBorderScore)} pontos | " +
                    "Cor: ${formatNumber(lastColorScore)} pontos\n" +
                    "Evolução relatada: ${if (lastEvolutionDetected) "sim" else "não"}"

        drawMultilineText(
            canvas,
            detailText,
            50f,
            695f,
            normalPaint,
            90
        )

        canvas.drawRoundRect(30f, 755f, pageWidth - 30f, 815f, 24f, 24f, creamPaint)
        canvas.drawRoundRect(30f, 755f, pageWidth - 30f, 815f, 24f, 24f, borderPaint)

        canvas.drawText("Observação importante", 50f, 780f, subtitlePaint)

        val warning =
            "Este relatório não possui valor diagnóstico e não substitui consulta médica. " +
                    "Em caso de suspeita, mudança, dor, sangramento, coceira ou crescimento da lesão, procure um dermatologista."

        drawMultilineText(
            canvas,
            warning,
            50f,
            802f,
            normalPaint,
            92
        )

        canvas.drawText("Gerado pelo aplicativo DermaSkin", 40f, 835f, smallPaint)
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
            y += 14f
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
        progress.visibility = View.GONE

        riskCard.visibility = View.VISIBLE
        sizeCard.visibility = View.GONE
        resultCard.visibility = View.VISIBLE

        txtRiskTitle.text = "Atenção"
        txtPercentage.text = "!"
        txtPercentage.setTextColor(Color.parseColor("#D9534F"))
        txtRiskExplanation.text = "Não foi possível concluir a análise."
        txtDescription.text = message
        txtABCDE.text = ""
        txtClasses.text = ""

        btnExportPdf.isEnabled = false
        btnExportPdf.alpha = 0.55f
    }
}