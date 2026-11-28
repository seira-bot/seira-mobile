package bot.seira.companion.ui

import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import bot.seira.companion.R
import java.util.concurrent.Executors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull

/**
 * The torch of the open camera. It knows nothing until [attach] runs, so a phone
 * with no flash unit never shows the button.
 */
internal class ScannerTorch {
    var hasFlash by mutableStateOf(false)
        private set
    var on by mutableStateOf(false)
        private set

    private var camera: Camera? = null
    private var source: LiveData<Int>? = null
    private val observer = Observer<Int> { on = it == TorchState.ON }

    fun attach(bound: Camera) {
        detach()
        camera = bound
        hasFlash = bound.cameraInfo.hasFlashUnit()
        source = bound.cameraInfo.torchState.also { it.observeForever(observer) }
    }

    fun toggle() {
        camera?.cameraControl?.enableTorch(!on)
    }

    fun detach() {
        source?.removeObserver(observer)
        source = null
        camera = null
        hasFlash = false
        on = false
    }
}

/**
 * QR scanning inside the pairing viewfinder — the port of `ios/App/PairingScanner.swift`.
 *
 * The scanner only recognizes QR codes and never pairs by itself. A valid payload
 * goes back to [PairingScreen], which shows the computer name and address for
 * explicit confirmation: an attacker-controlled deep link or QR may prefill a
 * target, but it cannot silently authorize that target.
 *
 * @param validate returns null to accept, or a human-readable message to show
 *   while scanning continues. The one-time credential never lives here.
 */
@Composable
internal fun BoxScope.QrScannerView(
    torch: ScannerTorch,
    validate: (String) -> String?,
    onValidationError: (String?) -> Unit,
) {
    val environment = LocalCompanion.current
    val access by environment.camera.access.collectAsState()
    var cameraFailure by remember { mutableStateOf<String?>(null) }

    LifecycleResumeEffect(Unit) {
        environment.camera.refresh()
        onPauseOrDispose {}
    }

    when {
        cameraFailure != null -> PairViewfinderMessage(
            title = stringResource(R.string.mobile_scanner_unavailable_cc42e33b),
            body = cameraFailure!!,
        )

        access == CameraAccess.UNKNOWN -> PairViewfinderMessage(
            title = stringResource(R.string.pair_camera_starting),
            body = stringResource(R.string.mobile_camera_permission_description),
        )

        access == CameraAccess.DENIED -> PairViewfinderMessage(
            title = stringResource(R.string.pair_camera_off_title),
            body = stringResource(R.string.pair_camera_off_body),
        )

        else -> ScannerSurface(
            torch = torch,
            validate = validate,
            onValidationError = onValidationError,
            onCameraFailure = { cameraFailure = it },
        )
    }
}

@Composable
private fun ScannerSurface(
    torch: ScannerTorch,
    validate: (String) -> String?,
    onValidationError: (String?) -> Unit,
    onCameraFailure: (String) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraUnavailable = stringResource(R.string.mobile_camera_unavailable)
    // A camera reports the same QR on many consecutive frames. One payload is
    // latched at a time; a rejected one re-arms after the message has had time
    // to be read.
    val latched = remember { MutableStateFlow<String?>(null) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val currentValidate by rememberUpdatedState(validate)
    val cameraLifecycle = remember { CameraLifecycle() }

    LaunchedEffect(latched) {
        latched.filterNotNull().collect { payload ->
            val problem = currentValidate(payload)
            if (problem == null) return@collect
            onValidationError(problem)
            delay(800)
            latched.value = null
        }
    }

    // Cancel and a successful scan both remove this composable, so one disposal
    // path covers both. Without it the use cases stayed bound to the Activity's
    // lifecycle and ML Kit kept decoding frames behind the pairing form.
    DisposableEffect(Unit) {
        onDispose {
            cameraLifecycle.release()
            torch.detach()
            analysisExecutor.shutdown()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { viewContext ->
            val previewView = PreviewView(viewContext)
            val providerFuture = ProcessCameraProvider.getInstance(viewContext)
            providerFuture.addListener(
                {
                    try {
                        val provider = providerFuture.get()
                        val started = cameraLifecycle.startAnalyzing(
                            createAnalyzer = {
                                QrCodeAnalyzer { payload ->
                                    latched.compareAndSet(null, payload)
                                }
                            },
                            releaseAnalyzer = { analyzer ->
                                runCatching { provider.unbindAll() }
                                analyzer.close()
                            },
                            bind = { analyzer ->
                                torch.attach(
                                    bindCamera(
                                        provider = provider,
                                        lifecycleOwner = lifecycleOwner,
                                        previewView = previewView,
                                        executor = analysisExecutor,
                                        analyzer = analyzer,
                                    ),
                                )
                            },
                            onFailure = { error ->
                                Log.w("PairingScanner", "could not bind the camera", error)
                            },
                        )
                        if (!started) onCameraFailure(cameraUnavailable)
                    } catch (error: Exception) {
                        Log.w("PairingScanner", "camera unavailable", error)
                        onCameraFailure(cameraUnavailable)
                    }
                },
                ContextCompat.getMainExecutor(viewContext),
            )
            previewView
        },
    )
}

/**
 * Everything the scanner holds open, released exactly once.
 *
 * The camera provider arrives on a future, and the user can leave before it
 * lands — a disposal that happens first must still tear down whatever binds
 * afterwards, or the camera stays live with nothing on screen. Main-thread only:
 * both [bound] and [release] run on the Activity's main executor.
 */
internal class CameraLifecycle {
    private var pendingRelease: (() -> Unit)? = null
    private var released = false

    fun bound(release: () -> Unit) {
        if (released) {
            release()
            return
        }
        pendingRelease?.invoke()
        pendingRelease = release
    }

    /** Idempotent: a second call releases nothing a second time. */
    fun release() {
        released = true
        val pending = pendingRelease
        pendingRelease = null
        pending?.invoke()
    }
}

/**
 * Build the analyzer, hand it to the lifecycle, *then* bind.
 *
 * The order is the point. Binding can throw — a camera another app holds, a
 * device with no back lens — and an analyzer created but never registered owns
 * an open ML Kit detector that nothing will ever close. Registering first means
 * every exit, including the throwing one, runs the same release.
 *
 * @return false when binding failed; the analyzer has already been released.
 */
internal fun <A> CameraLifecycle.startAnalyzing(
    createAnalyzer: () -> A,
    releaseAnalyzer: (A) -> Unit,
    bind: (A) -> Unit,
    onFailure: (Exception) -> Unit = {},
): Boolean {
    val analyzer = createAnalyzer()
    bound { releaseAnalyzer(analyzer) }
    return try {
        bind(analyzer)
        true
    } catch (error: Exception) {
        release()
        onFailure(error)
        false
    }
}

private fun bindCamera(
    provider: ProcessCameraProvider,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    executor: java.util.concurrent.Executor,
    analyzer: ImageAnalysis.Analyzer,
): Camera {
    val preview = Preview.Builder().build().apply {
        surfaceProvider = previewView.surfaceProvider
    }
    val analysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()
    analysis.setAnalyzer(executor, analyzer)
    provider.unbindAll()
    return provider.bindToLifecycle(
        lifecycleOwner,
        CameraSelector.DEFAULT_BACK_CAMERA,
        preview,
        analysis,
    )
}

/** QR only — the pairing payload is the sole thing this app reads from a camera. */
private class QrCodeAnalyzer(private val onPayload: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build(),
    )

    /** Releases ML Kit's detector; safe to call more than once. */
    fun close() {
        runCatching { scanner.close() }
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val frame = image.image
        if (frame == null) {
            image.close()
            return
        }
        scanner.process(InputImage.fromMediaImage(frame, image.imageInfo.rotationDegrees))
            .addOnSuccessListener { codes ->
                codes.firstNotNullOfOrNull { it.rawValue }?.let(onPayload)
            }
            .addOnCompleteListener { image.close() }
    }
}