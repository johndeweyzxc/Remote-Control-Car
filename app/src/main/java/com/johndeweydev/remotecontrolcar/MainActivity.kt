package com.johndeweydev.remotecontrolcar

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.slider.Slider
import com.johndeweydev.remotecontrolcar.databinding.ActivityMainBinding
import java.util.Locale

class MainActivity : AppCompatActivity() {

  private val requestSpeechRecognitionCode = 102
  private lateinit var binding: ActivityMainBinding
  private lateinit var speechRecognizer: SpeechRecognizer

  private val carEventHandler = object: Car.Event {
    @SuppressLint("SetTextI18n")
    override fun onConnectionSuccess() {
      binding.buttonConnectDisconnectFromCarMain.text = "disconnect"
      binding.textViewConnectionStatusMain.text = "${Car.GATEWAY}:${Car.PORT}"
      enableControls(true)
    }

    @SuppressLint("SetTextI18n")
    override fun onConnectionFailed() {
      binding.buttonConnectDisconnectFromCarMain.text = "connect"
      binding.textViewConnectionStatusMain.text = "Disconnected"
      enableControls(false)
    }

    @SuppressLint("SetTextI18n")
    override fun onDisconnectionSuccess() {
      binding.buttonConnectDisconnectFromCarMain.text = "connect"
      binding.textViewConnectionStatusMain.text = "Disconnected"
      enableControls(false)
    }

    @SuppressLint("SetTextI18n")
    override fun onCarIsDisconnected() {
      binding.buttonConnectDisconnectFromCarMain.text = "connect"
      binding.textViewConnectionStatusMain.text = "Disconnected"
      Toast.makeText(this@MainActivity, "No connection found", Toast.LENGTH_LONG).show()
      enableControls(false)
    }

    @SuppressLint("SetTextI18n")
    override fun onFailedToSendCommand(command: String) {
      Car.disconnect(lifecycleScope)
      Toast.makeText(this@MainActivity, "Failed to send command: $command",
        Toast.LENGTH_LONG).show()
    }
  }

  private val speechRecognitionListener = object : RecognitionListener {
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onError(error: Int) {}
    @SuppressLint("SetTextI18n")
    override fun onResults(results: Bundle?) {
      val data = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
      binding.textViewVoiceInputCommandStatusMain.text = "Voice: ${data?.get(0)}"
      val voiceCommand = data?.get(0).toString().uppercase()
      Car.sendVoiceCommand(lifecycleScope, voiceCommand, this@MainActivity)
    }
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
  }

  @Deprecated("Deprecated in Java")
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)

    if (requestCode == requestSpeechRecognitionCode && resultCode == Activity.RESULT_OK) {
      val result = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
      val voiceCommand = result?.get(0).toString().uppercase()
      Toast.makeText(this, voiceCommand, Toast.LENGTH_LONG).show()
      Car.sendVoiceCommand(lifecycleScope, voiceCommand, this)
    }
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == requestSpeechRecognitionCode) {
      if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        Toast.makeText(this,
          "Audio record permission granted", Toast.LENGTH_SHORT).show()
      } else {
        Toast.makeText(this,
          "Audio record permission denied", Toast.LENGTH_SHORT).show()
      }
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
      != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(this,
        arrayOf(Manifest.permission.RECORD_AUDIO), requestSpeechRecognitionCode)
    }
    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
    speechRecognizer.setRecognitionListener(speechRecognitionListener)

    enableControls(false)
    Car.setEventHandler(carEventHandler)

    setupMainControlsManual()

    binding.buttonForwardLeftMain.setOnTouchListener(Car.buttonHold(
      lifecycleScope, Car.CarCommandCodes.FORWARD_LEFT_PRESSED,
      Car.CarCommandCodes.FORWARD_LEFT_RELEASED))
    binding.buttonForwardRightMain.setOnTouchListener(Car.buttonHold(
      lifecycleScope, Car.CarCommandCodes.FORWARD_RIGHT_PRESSED,
      Car.CarCommandCodes.FORWARD_RIGHT_RELEASED))

    setupPeripherals()

    binding.buttonBreakMain.setOnClickListener {
      Car.sendCommand(lifecycleScope, Car.CarCommandCodes.BREAK)
    }
    binding.discreteSliderGearShifterMain.addOnChangeListener(gearShift())
    binding.buttonConnectDisconnectFromCarMain.setOnClickListener {
      if (!Car.isConnected()) {
        Car.connect(lifecycleScope)
      } else {
        Car.disconnect(lifecycleScope)
      }
    }
    binding.switchAutoMain.setOnCheckedChangeListener { _, isChecked ->
      binding.discreteSliderGearShifterMain.value = 0F
      if (isChecked) {
        setupMainControlsAuto()
      } else {
        setupMainControlsManual()
      }
    }
    binding.buttonActivateVoiceInputMain.setOnClickListener {
      if (!SpeechRecognizer.isRecognitionAvailable(this)) {
        Log.d("dev-log", "Speech recognition is not available")
      }

      val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
      intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
      intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
      intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Listening for command")
      startActivityForResult(intent, requestSpeechRecognitionCode)
    }
    binding.switchRealtimeVoiceInputCommandMain.setOnCheckedChangeListener { _, isChecked ->
      if (isChecked) {
        // Start listening
        val speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        speechRecognizer.startListening(speechRecognizerIntent)
      } else {
        // Stop listening
        speechRecognizer.stopListening()
      }
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun setupMainControlsManual() {
    binding.buttonAccelerateMain.setOnTouchListener(Car.buttonHold(
      lifecycleScope, Car.CarCommandCodes.ACCELERATE, Car.CarCommandCodes.BREAK))
    binding.buttonReverseMain.setOnTouchListener(Car.buttonHold(
      lifecycleScope, Car.CarCommandCodes.REVERSE, Car.CarCommandCodes.BREAK))
    binding.buttonLeftTurnMain.setOnTouchListener(Car.buttonHold(
      lifecycleScope, Car.CarCommandCodes.LEFT_TURN, Car.CarCommandCodes.BREAK))
    binding.buttonRightTurnMain.setOnTouchListener(Car.buttonHold(
      lifecycleScope, Car.CarCommandCodes.RIGHT_TURN, Car.CarCommandCodes.BREAK))

    binding.buttonBreakMain.isEnabled = false
    binding.buttonForwardRightMain.isEnabled = false
    binding.buttonForwardLeftMain.isEnabled = false

    binding.buttonAccelerateMain.setOnClickListener(null)
    binding.buttonReverseMain.setOnClickListener(null)
    binding.buttonLeftTurnMain.setOnClickListener(null)
    binding.buttonRightTurnMain.setOnClickListener(null)
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun setupMainControlsAuto() {
    binding.buttonAccelerateMain.setOnTouchListener(null)
    binding.buttonReverseMain.setOnTouchListener(null)
    binding.buttonLeftTurnMain.setOnTouchListener(null)
    binding.buttonRightTurnMain.setOnTouchListener(null)

    binding.buttonBreakMain.isEnabled = true
    binding.buttonForwardRightMain.isEnabled = true
    binding.buttonForwardLeftMain.isEnabled = true

    binding.buttonAccelerateMain.setOnClickListener {
      Car.sendCommand(lifecycleScope, Car.CarCommandCodes.ACCELERATE)
    }
    binding.buttonReverseMain.setOnClickListener {
      Car.sendCommand(lifecycleScope, Car.CarCommandCodes.REVERSE)
    }
    binding.buttonRightTurnMain.setOnClickListener {
      Car.sendCommand(lifecycleScope, Car.CarCommandCodes.RIGHT_TURN)
    }
    binding.buttonLeftTurnMain.setOnClickListener {
      Car.sendCommand(lifecycleScope, Car.CarCommandCodes.LEFT_TURN)
    }
  }


  @SuppressLint("ClickableViewAccessibility")
  private fun setupPeripherals() {
    binding.buttonHornMain.setOnTouchListener(Car.buttonHold(
      lifecycleScope, Car.CarCommandCodes.HORN_PRESSED, Car.CarCommandCodes.HORN_RELEASED))
    binding.switchRightTurnSignalMain.setOnCheckedChangeListener { _, isChecked ->
      binding.switchLeftTurnSignalMain.isEnabled = !isChecked
      Car.toggleRightTurnSignal(lifecycleScope, isChecked)
    }
    binding.switchLeftTurnSignalMain.setOnCheckedChangeListener { _, isChecked ->
      binding.switchRightTurnSignalMain.isEnabled = !isChecked
      Car.toggleLeftTurnSignal(lifecycleScope, isChecked)
    }
    binding.switchHeadLightsMain.setOnCheckedChangeListener { _, isChecked ->
      Car.toggleHeadLights(lifecycleScope, isChecked)
    }
    binding.switchHazardLightsMain.setOnCheckedChangeListener { _, isChecked ->
      binding.switchLeftTurnSignalMain.isEnabled = !isChecked
      binding.switchRightTurnSignalMain.isEnabled = !isChecked
      Car.toggleHazardLights(lifecycleScope, isChecked)
    }
  }

  private fun enableControls(value: Boolean) {
    binding.discreteSliderGearShifterMain.isEnabled = value
    binding.switchAutoMain.isEnabled = value
    binding.switchHazardLightsMain.isEnabled = value
    binding.switchHeadLightsMain.isEnabled = value
    binding.switchLeftTurnSignalMain.isEnabled = value
    binding.switchRightTurnSignalMain.isEnabled = value
  }

  private fun gearShift(): Slider.OnChangeListener {
    return Slider.OnChangeListener {
        slider, _, _ ->
      var gearStatus = "N"
      var commandToSend = Car.CarCommandCodes.GEAR_NEUTRAL

      when (slider.value.toInt()) {
        0 ->  {
          commandToSend = Car.CarCommandCodes.GEAR_NEUTRAL
          gearStatus = "N"
        }
        1 ->  {
          commandToSend = Car.CarCommandCodes.GEAR_PRIMERA
          gearStatus = "1"
        }
        2 -> {
          commandToSend = Car.CarCommandCodes.GEAR_SEGUNDA
          gearStatus = "2"
        }
        3 -> {
          commandToSend = Car.CarCommandCodes.GEAR_TRESIERA
          gearStatus = "3"
        }
        4 -> {
          commandToSend = Car.CarCommandCodes.GEAR_QUARTA
          gearStatus = "4"
        }
      }
      binding.textViewGearStatus.text = gearStatus
      Car.sendCommand(lifecycleScope, commandToSend)
    }
  }
}