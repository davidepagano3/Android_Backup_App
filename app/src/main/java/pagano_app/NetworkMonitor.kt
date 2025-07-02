package org.pagano.backup

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.nfc.Tag
import android.util.Log
import android.os.Handler
import android.os.Looper
import java.io.IOException
import java.net.URL
import javax.net.ssl.*


class NetworkMonitor(private val context: Context, private val callback: Callback, private val onConnectedToDigos: () -> Unit) {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val TAG = "PAGANO_APP"
    val testUrl = "https://192.168.1.226/alive.html"
    val networkRequest: NetworkRequest.Builder? = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
    private var wifiSignalStrength: Int = 0

    interface Callback {
        fun autoRsyncTask()
        fun isIdleState(): Boolean
    }


    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            val nw = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(nw)
            when {
                capabilities == null -> {
                    Log.d(TAG, "No network connection")
                }
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    Log.d(TAG, "Connected to Wi-Fi")
                }
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    Log.d(TAG, "Connected to Mobile Data")
                }
                else -> {
                    Log.d(TAG, "Other network type")
                }
            }
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            if (callback.isIdleState()) {
                Log.d(TAG, "Is idle state")
            } else {
                Log.d(TAG, "sync is running")
                return
            }

            if (checkIfUrlExists()) {
                Log.d(TAG, "URL EXISTS")
            } else {
                Log.d(TAG, "URL DOES NOT EXIST")
                return
            }

            Handler(Looper.getMainLooper()).post {
                Log.d(TAG, "Launch autoRsyncTask " )
                callback.autoRsyncTask()
            }
        }
    }

    fun startMonitoring() {
        val request = NetworkRequest.Builder()
            .addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    fun stopMonitoring() {
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    fun checkIfUrlExists(): Boolean {
        var responseCode = 0
        val exists = try {
            trustAllHosts()

            val url = URL(testUrl)
            val connection = url.openConnection() as HttpsURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.connect()
            responseCode = connection.responseCode
            responseCode in 200..399
        } catch (e: IOException) {
            e.printStackTrace()
            Log.d(TAG, "URL DOES NOT EXIST [ $responseCode ]: $testUrl")
            return false
        }

        Log.d(TAG, "$exists URL EXISTS [ $responseCode ]: $testUrl")
        return exists
    }

    private fun trustAllHosts() {
        try {
            val trustAllCerts = arrayOf<TrustManager>(
                object : X509TrustManager {
                    override fun checkClientTrusted(
                        chain: Array<java.security.cert.X509Certificate>,
                        authType: String
                    ) {}

                    override fun checkServerTrusted(
                        chain: Array<java.security.cert.X509Certificate>,
                        authType: String
                    ) {}

                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> =
                        arrayOf()
                }
            )

            val sc = SSLContext.getInstance("SSL")
            sc.init(null, trustAllCerts, java.security.SecureRandom())
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
            HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}