package yupay.turismo.sync

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import yupay.turismo.data.local.SerializationHelper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

class SyncManager(
    private val onMessageReceived: (SyncMessage) -> Unit,
    private val onConnectionStatusChanged: (Boolean) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var writer: PrintWriter? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    private val json = SerializationHelper.json

    fun isServerRunning(): Boolean = serverSocket != null && !serverSocket!!.isClosed
    fun isConnected(): Boolean = clientSocket != null && clientSocket!!.isConnected && !clientSocket!!.isClosed

    fun startServer(port: Int) {
        if (isRunning && serverSocket != null) return
        isRunning = true
        scope.launch {
            try {
                serverSocket = ServerSocket(port)
                Log.d("SyncManager", "Server started on port $port")
                while (isRunning) {
                    val socket = serverSocket?.accept() ?: break
                    handleConnection(socket)
                }
            } catch (e: Exception) {
                Log.e("SyncManager", "Server error", e)
            }
        }
    }

    fun connectTo(ip: String, port: Int) {
        if (isRunning && clientSocket?.isConnected == true) return
        isRunning = true
        scope.launch {
            try {
                Log.d("SyncManager", "Connecting to $ip:$port...")
                val socket = Socket()
                socket.connect(java.net.InetSocketAddress(ip, port), 5000)
                handleConnection(socket)
            } catch (e: Exception) {
                Log.e("SyncManager", "Connection error", e)
                onConnectionStatusChanged(false)
            }
        }
    }

    private fun handleConnection(socket: Socket) {
        // Cerrar conexión previa si existe
        if (clientSocket != null && clientSocket != socket) {
            try { clientSocket?.close() } catch (e: Exception) {}
        }
        
        clientSocket = socket
        writer = PrintWriter(socket.getOutputStream(), true)
        onConnectionStatusChanged(true)
        
        scope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                var line: String?
                while (socket.isConnected && isRunning) {
                    line = reader.readLine() ?: break
                    try {
                        val message = json.decodeFromString<SyncMessage>(line)
                        onMessageReceived(message)
                    } catch (e: Exception) {
                        Log.e("SyncManager", "Error decoding message: $line", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("SyncManager", "Read error", e)
            } finally {
                // Solo notificar desconexión si este socket sigue siendo el activo
                if (clientSocket == socket) {
                    onConnectionStatusChanged(false)
                    disconnect()
                }
            }
        }
    }

    fun sendMessage(message: SyncMessage) {
        scope.launch {
            try {
                val line = json.encodeToString(message)
                writer?.println(line)
            } catch (e: Exception) {
                Log.e("SyncManager", "Send error", e)
            }
        }
    }

    private fun disconnect() {
        try {
            clientSocket?.close()
        } catch (e: Exception) {
            Log.e("SyncManager", "Client socket disconnect error", e)
        }
        clientSocket = null
        writer = null
    }

    /**
     * Cierra SÓLO la conexión actual con el peer (socket de datos), dejando el [serverSocket]
     * escuchando. Se usa para rechazar a un peer con token de emparejamiento inválido sin tumbar el
     * servidor, de modo que el cliente legítimo todavía pueda conectarse.
     */
    fun dropCurrentConnection() {
        onConnectionStatusChanged(false)
        disconnect()
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        onConnectionStatusChanged(false)
        disconnect()
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e("SyncManager", "Server socket stop error", e)
        }
        serverSocket = null
        // Cancelar todos los hijos pero mantener el scope vivo para futuras conexiones
        scope.coroutineContext.job.cancelChildren()
    }
}
