package com.johndeweydev.remotecontrolcar

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.PrintWriter
import java.net.Socket

class Car {

  enum class CarCommandCodes {
    ACCELERATE,
    BREAK,
    REVERSE,
    LEFT_TURN,
    RIGHT_TURN,

    FORWARD_LEFT_PRESSED,
    FORWARD_LEFT_RELEASED,
    FORWARD_RIGHT_PRESSED,
    FORWARD_RIGHT_RELEASED,

    GEAR_NEUTRAL,
    GEAR_PRIMERA,
    GEAR_SEGUNDA,
    GEAR_TRESIERA,
    GEAR_QUARTA,

    HORN_PRESSED,
    HORN_RELEASED,
    HEADLIGHTS_ON,
    HEADLIGHTS_OFF,
    SIGNAL_LIGHTS_RIGHT_ON,
    SIGNAL_LIGHTS_RIGHT_OFF,
    SIGNAL_LIGHTS_LEFT_ON,
    SIGNAL_LIGHTS_LEFT_OFF,
    SIGNAL_LIGHTS_HAZARD_ON,
    SIGNAL_LIGHTS_HAZARD_OFF
  }

  interface Event {
    fun onConnectionSuccess()
    fun onConnectionFailed()
    fun onDisconnectionSuccess()
    fun onCarIsDisconnected()
    fun onFailedToSendCommand(command: String)
  }

  companion object {
    const val GATEWAY = "192.168.4.1"
    const val PORT = 8080

    private var carSocketServer: Socket? = null
    private var eventHandler: Event? = null

    fun setEventHandler(event: Event) {
      eventHandler = event
    }

    fun isConnected(): Boolean {
      return carSocketServer != null
    }

    fun connect(scope: LifecycleCoroutineScope) {
      scope.launch(Dispatchers.IO) {
        try {
          carSocketServer = Socket(GATEWAY, PORT)
          withContext(Dispatchers.Main) {
            eventHandler?.onConnectionSuccess()
          }
        } catch (e: IOException) {
          withContext(Dispatchers.Main) {
            eventHandler?.onConnectionFailed()
          }
        }
      }
    }

    fun disconnect(scope: LifecycleCoroutineScope) {
      scope.launch(Dispatchers.IO) {
        if (carSocketServer == null) {
          Log.d("dev-log", "Car.disconnect: Socket is null")
          withContext(Dispatchers.Main) {
            eventHandler?.onDisconnectionSuccess()
          }
          return@launch
        }

        if (carSocketServer?.isClosed == true) {
          Log.d("dev-log", "Car.disconnect: Socket is already close")
          carSocketServer = null
          withContext(Dispatchers.Main) {
            eventHandler?.onDisconnectionSuccess()
          }
          return@launch
        }

        carSocketServer?.close()
        carSocketServer = null
        withContext(Dispatchers.Main) {
          eventHandler?.onDisconnectionSuccess()
        }
      }
    }

    fun sendCommand(scope:LifecycleCoroutineScope, command: CarCommandCodes) {
      if (!isConnected()) {
        eventHandler?.onCarIsDisconnected()
      }

      Log.d("dev-log", "Car.sendCommandToCar: Sending command -> $command")
      scope.launch(Dispatchers.IO) {
        try {
          val outputStream = carSocketServer?.getOutputStream()
          if (outputStream != null) {
            PrintWriter(outputStream, true).println(command)
          } else {
            withContext(Dispatchers.Main) {
              eventHandler?.onFailedToSendCommand(command.toString())
            }
          }
        } catch (e: IOException) {
          withContext(Dispatchers.Main) {
            eventHandler?.onFailedToSendCommand(command.toString())
          }
        }
      }
    }

    fun sendVoiceCommand(scope: LifecycleCoroutineScope, command: String, context: Context) {
      val processedCommand = command.replace(" ", "_")
      val listCarCommandCodes = CarCommandCodes.values().map { it.name }
      Log.d("dev-log", "Car.sendVoiceCommand: $processedCommand")

      var finalCommand = processedCommand
      when (processedCommand) {
        "BRAKE" -> {
          finalCommand = "BREAK"
        }
        "REVERSE_ACTIVATE" -> {
          finalCommand = "REVERSE"
        }

        "FORWARD_LEFT_TURN_ON" -> {
          finalCommand = "FORWARD_LEFT_PRESSED"
        }
        "FORWARD_LEFT_TURN_OFF" -> {
          finalCommand = "FORWARD_LEFT_RELEASED"
        }
        "FORWARD_RIGHT_TURN_ON" -> {
          finalCommand = "FORWARD_RIGHT_PRESSED"
        }
        "FORWARD_RIGHT_TURN_OFF" -> {
          finalCommand = "FORWARD_RIGHT_RELEASED"
        }

        "FIRST" -> {
          finalCommand = "GEAR_PRIMERA"
        }
        "SECOND" -> {
          finalCommand = "GEAR_SEGUNDA"
        }
        "THIRD" -> {
          finalCommand = "GEAR_TRESIERA"
        }
        "FOURTH" -> {
          finalCommand = "GEAR_QUARTA"
        }

        "HORN_TURN_ON" -> {
          finalCommand = "HORN_PRESSED"
        }
        "HORN_TURN_OFF" -> {
          finalCommand = "HORN_RELEASED"
        }
        "HEADLIGHTS_TURN_ON" -> {
          finalCommand = "HEADLIGHTS_ON"
        }
        "HEADLIGHTS_TURN_OFF" -> {
          finalCommand = "HEADLIGHTS OFF"
        }
        "SIGNAL_LEFT_TURN_ON" -> {
          finalCommand = "SIGNAL_LIGHTS_LEFT_ON"
        }
        "SIGNAL_LEFT_TURN_OFF" -> {
          finalCommand = "SIGNAL_LIGHTS_LEFT_OFF"
        }
        "SIGNAL_RIGHT_TURN_ON" -> {
          finalCommand = "SIGNAL_LIGHTS_RIGHT_ON"
        }
        "SIGNAL_RIGHT_TURN_OFF" -> {
          finalCommand = "SIGNAL_LIGHTS_RIGHT_OFF"
        }
        "HAZARD_TURN_ON" -> {
          finalCommand = "HAZARD_ON"
        }
        "HAZARD_TURN_OFF" -> {
          finalCommand = "HAZARD OFF"
        }
      }

      if (finalCommand in listCarCommandCodes) {
        Toast.makeText(context, "Command -> $finalCommand", Toast.LENGTH_LONG).show()
        // Get the corresponding index of the code from the enum then send the final command
        val indexOfCorrespondingCode = listCarCommandCodes.indexOf(finalCommand)
        sendCommand(scope, CarCommandCodes.values()[indexOfCorrespondingCode])
      }
    }

    fun buttonHold(
      scope: LifecycleCoroutineScope,
      pressedCode: CarCommandCodes,
      releasedCode: CarCommandCodes
    ): View.OnTouchListener {
      return object : View.OnTouchListener {
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
          when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
              sendCommand(scope, pressedCode)
              return true
            }
            MotionEvent.ACTION_UP -> {
              sendCommand(scope, releasedCode)
              return true
            }
          }
          return false
        }
      }
    }
    
    fun toggleLeftTurnSignal(scope: LifecycleCoroutineScope, toggled: Boolean) {
      if (toggled) {
        sendCommand(scope, CarCommandCodes.SIGNAL_LIGHTS_LEFT_ON)
        sendCommand(scope, CarCommandCodes.SIGNAL_LIGHTS_RIGHT_OFF)
      } else {
        sendCommand(scope, CarCommandCodes.SIGNAL_LIGHTS_LEFT_OFF)
      }
    }

    fun toggleRightTurnSignal(scope: LifecycleCoroutineScope, toggled: Boolean) {
      if (toggled) {
        sendCommand(scope, CarCommandCodes.SIGNAL_LIGHTS_RIGHT_ON)
        sendCommand(scope, CarCommandCodes.SIGNAL_LIGHTS_LEFT_OFF)
      } else {
        sendCommand(scope, CarCommandCodes.SIGNAL_LIGHTS_RIGHT_OFF)
      }
    }

    fun toggleHeadLights(scope: LifecycleCoroutineScope, toggled: Boolean) {
      if (toggled) {
        sendCommand(scope, CarCommandCodes.HEADLIGHTS_ON)
      } else {
        sendCommand(scope, CarCommandCodes.HEADLIGHTS_OFF)
      }
    }

    fun toggleHazardLights(scope: LifecycleCoroutineScope, toggled: Boolean) {
      if (toggled) {
        sendCommand(scope, CarCommandCodes.SIGNAL_LIGHTS_HAZARD_ON)
      } else {
        sendCommand(scope, CarCommandCodes.SIGNAL_LIGHTS_HAZARD_OFF)
      }
    }
  }
}