package cz.crabhi.dragons.app.car

import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.car.app.CarContext
import androidx.car.app.media.CarAudioRecord
import cz.crabhi.dragons.app.speech.AudioStream
import java.io.FileOutputStream
import java.io.IOException

/** Streams the car microphone (Car App API level 5+) into a pipe that SpeechRecognizer can read. */
class CarMicStream
    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    constructor(
        carContext: CarContext,
    ) : AudioStream {
        private val record = CarAudioRecord.create(carContext)
        private val pipe = ParcelFileDescriptor.createPipe()
        override val fd: ParcelFileDescriptor = pipe[0]
        override val sampleRate: Int = CarAudioRecord.AUDIO_CONTENT_SAMPLING_RATE

        @Volatile private var running = true

        init {
            Thread {
                try {
                    record.startRecording()
                    val buffer = ByteArray(CarAudioRecord.AUDIO_CONTENT_BUFFER_SIZE)
                    FileOutputStream(pipe[1].fileDescriptor).use { out ->
                        while (running) {
                            val n = record.read(buffer, 0, buffer.size)
                            if (n <= 0) break
                            out.write(buffer, 0, n)
                        }
                    }
                } catch (e: IOException) {
                    Log.d(TAG, "Car mic pipe closed")
                } catch (e: Exception) {
                    Log.w(TAG, "Car mic failed", e)
                } finally {
                    runCatching { record.stopRecording() }
                }
            }.apply {
                name = "car-mic"
                start()
            }
        }

        override fun close() {
            running = false
            runCatching { pipe[1].close() }
            runCatching { pipe[0].close() }
        }

        companion object {
            private const val TAG = "CarMicStream"
        }
    }
