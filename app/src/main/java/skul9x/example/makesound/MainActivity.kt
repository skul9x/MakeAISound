package skul9x.example.makesound

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import skul9x.example.makesound.engine.OnnxSessionManager
import skul9x.example.makesound.engine.SeaG2P
import skul9x.example.makesound.engine.VieNeuOnnxEngine
import skul9x.example.makesound.engine.VieNeuStudioSynthesizer
import skul9x.example.makesound.player.AudioPlayerManager
import skul9x.example.makesound.ui.MainStudioScreen
import skul9x.example.makesound.ui.MakeAiSoundViewModel
import skul9x.example.makesound.ui.theme.MakeAiSoundTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MakeAiSoundViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                // Initialize SeaG2P assets if needed
                try {
                    SeaG2P.init(applicationContext)
                } catch (_: Exception) {}

                // Initialize ONNX engine and synthesizer safely
                val synthesizer = try {
                    val engine = VieNeuOnnxEngine.create(applicationContext)
                    VieNeuStudioSynthesizer(engine)
                } catch (_: Exception) {
                    null
                }
                val playerManager = AudioPlayerManager()
                val voiceSampleManager = skul9x.example.makesound.engine.VoiceSampleManager(applicationContext)
                val voiceSamplePlayer = skul9x.example.makesound.player.VoiceSamplePlayer()

                return MakeAiSoundViewModel(
                    synthesizer = synthesizer,
                    playerManager = playerManager,
                    voiceSampleManager = voiceSampleManager,
                    voiceSamplePlayer = voiceSamplePlayer
                ) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MakeAiSoundTheme {
                MainStudioScreen(viewModel = viewModel)
            }
        }
    }
}