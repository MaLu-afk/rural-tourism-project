package yupay.turismo.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

class NsdHelper(context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val serviceType = "_yupay_sync._tcp."
    private val serviceName = "YupaySync_${android.os.Build.MODEL}"

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun registerService(port: Int) {
        if (registrationListener != null) return

        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = this@NsdHelper.serviceName
            this.serviceType = this@NsdHelper.serviceType
            this.port = port
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                Log.d("NsdHelper", "Service registered: ${NsdServiceInfo.serviceName}")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e("NsdHelper", "Registration failed: $errorCode")
                registrationListener = null
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                registrationListener = null
            }
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                registrationListener = null
            }
        }

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    fun discoverServices(onServiceFound: (NsdServiceInfo) -> Unit) {
        if (discoveryListener != null) {
            Log.d("NsdHelper", "Discovery already in progress, stopping old one first")
            stopDiscovery()
        }

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e("NsdHelper", "Discovery failed: $errorCode")
                stopDiscovery()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e("NsdHelper", "Stop discovery failed: $errorCode")
                stopDiscovery()
            }

            override fun onDiscoveryStarted(serviceType: String) {
                Log.d("NsdHelper", "Discovery started")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d("NsdHelper", "Discovery stopped")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d("NsdHelper", "Service found: ${serviceInfo.serviceName}")
                if (serviceInfo.serviceType.contains(serviceType.removeSuffix("."))) {
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Log.e("NsdHelper", "Resolve failed for ${serviceInfo.serviceName}: $errorCode")
                            // Reintentar si falla la resolución (común en algunos routers)
                            if (errorCode == NsdManager.FAILURE_ALREADY_ACTIVE) {
                                // Ignorar si ya está activo
                            }
                        }

                        override fun onServiceResolved(resolvedServiceInfo: NsdServiceInfo) {
                            Log.d("NsdHelper", "Service resolved: ${resolvedServiceInfo.host.hostAddress}:${resolvedServiceInfo.port}")
                            onServiceFound(resolvedServiceInfo)
                        }
                    })
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d("NsdHelper", "Service lost: ${serviceInfo.serviceName}")
            }
        }

        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun stop() {
        stopRegistration()
        stopDiscovery()
    }

    fun stopRegistration() {
        registrationListener?.let { 
            try { nsdManager.unregisterService(it) } catch (e: Exception) { Log.e("NsdHelper", "Unregister failed", e) }
        }
        registrationListener = null
    }

    fun stopDiscovery() {
        discoveryListener?.let { 
            try { nsdManager.stopServiceDiscovery(it) } catch (e: Exception) { Log.e("NsdHelper", "Stop discovery failed", e) }
        }
        discoveryListener = null
    }
}
