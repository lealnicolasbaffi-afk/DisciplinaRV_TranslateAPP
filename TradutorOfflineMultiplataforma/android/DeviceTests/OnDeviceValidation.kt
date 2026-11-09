package br.edu.tradutoroffline.tests

import android.content.Context
import android.net.ConnectivityManager
import android.os.Bundle
import android.provider.Settings
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.edu.tradutoroffline.app.MainActivity
import br.edu.tradutoroffline.domain.Direction
import br.edu.tradutoroffline.services.speech.LocalSpeech
import br.edu.tradutoroffline.services.synthesis.LocalSynthesis
import br.edu.tradutoroffline.services.translation.LocalTranslation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit opt-in operations prepare the real app and verify its services without adding measurements. */
@RunWith(AndroidJUnit4::class)
class OnDeviceValidation {
    @Test fun prepareRealAppResources() {
        assumeTrue("Execute com -e prepare true para preparar os recursos reais do app.",
            InstrumentationRegistry.getArguments().getString("prepare") == "true")
        ActivityScenario.launch(MainActivity::class.java).use {
            runBlocking {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                LocalSpeech(context).use { speech ->
                    withTimeout(600_000) { speech.downloadMissingModels { status -> report(status) } }
                    withTimeout(90_000) { speech.prepare() }
                    report(speech.modelStatus())
                }
                LocalTranslation().use { translation ->
                    withTimeout(180_000) { translation.prepare() }
                    assertTrue(translation.isReady())
                    report("Pacote de tradução PT/EN instalado no armazenamento real do app.")
                }
                LocalSynthesis(context).use { synthesis ->
                    report(withTimeout(45_000) { synthesis.prepare() })
                }
            }
        }
    }

    @Test fun translatesAndSpeaksBothDirectionsInAirplaneMode() {
        assumeTrue("Execute com -e offline true após preparar os recursos e desligar a rede.",
            InstrumentationRegistry.getArguments().getString("offline") == "true")
        ActivityScenario.launch(MainActivity::class.java).use {
            runBlocking {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                assertEquals("Ative modo avião antes do ensaio.", 1,
                    Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0))
                val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                assertNull("Desligue Wi-Fi, dados e tethering antes do ensaio.", connectivity.activeNetwork)
                LocalSpeech(context).use { speech ->
                    withTimeout(90_000) { speech.prepare() }
                    report(speech.modelStatus())
                }
                LocalTranslation().use { translation ->
                    assertTrue("O pacote de tradução deve ter sido instalado antes.", translation.isReady())
                    LocalSynthesis(context).use { synthesis ->
                        report(withTimeout(45_000) { synthesis.prepare() })
                        val phrases = listOf(Direction.PT_EN to "Bom dia. Onde fica a estação?",
                            Direction.EN_PT to "The station is two blocks away.")
                        for ((direction, input) in phrases) {
                            val output = withTimeout(45_000) { translation.translate(input, direction) }
                            assertTrue(output.isNotBlank())
                            assertNotEquals(input, output)
                            report("${direction.label}: $input => $output; tradução ${translation.lastTranslationMS} ms")
                            var started = false
                            withTimeout(45_000) { synthesis.speak(output, direction) { started = true } }
                            assertTrue("O motor deve confirmar o início da síntese.", started)
                            report("${direction.label}: síntese iniciou e terminou sem rede ativa.")
                        }
                    }
                }
                assertNull("A rede deve continuar desligada ao terminar.", connectivity.activeNetwork)
            }
        }
    }
}

private fun report(message: String) {
    InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("stream", "$message\n") })
}
