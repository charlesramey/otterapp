package com.example.otterenrichment

import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

/**
 * WiFi network data class
 */
data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val signal: Int,
    val isSecured: Boolean,
    val capabilities: String
) {
    fun getSignalStrength(): String {
        return when {
            signal >= -50 -> "Excellent"
            signal >= -60 -> "Good"
            signal >= -70 -> "Fair"
            else -> "Weak"
        }
    }
}

/**
 * Adapter for displaying WiFi networks in RecyclerView
 */
class WifiNetworkAdapter(
    private val onNetworkClick: (WifiNetwork) -> Unit
) : ListAdapter<WifiNetwork, WifiNetworkAdapter.WifiViewHolder>(WifiDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WifiViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return WifiViewHolder(view, onNetworkClick)
    }

    override fun onBindViewHolder(holder: WifiViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class WifiViewHolder(
        itemView: View,
        private val onNetworkClick: (WifiNetwork) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val text1: TextView = itemView.findViewById(android.R.id.text1)
        private val text2: TextView = itemView.findViewById(android.R.id.text2)

        fun bind(network: WifiNetwork) {
            text1.text = network.ssid
            text2.text = "${network.getSignalStrength()} (${network.signal} dBm) • ${if (network.isSecured) "Secured" else "Open"}"

            itemView.setOnClickListener {
                onNetworkClick(network)
            }
        }
    }
}

/**
 * DiffUtil callback for efficient list updates
 */
class WifiDiffCallback : DiffUtil.ItemCallback<WifiNetwork>() {
    override fun areItemsTheSame(oldItem: WifiNetwork, newItem: WifiNetwork): Boolean {
        return oldItem.bssid == newItem.bssid
    }

    override fun areContentsTheSame(oldItem: WifiNetwork, newItem: WifiNetwork): Boolean {
        return oldItem == newItem
    }
}

/**
 * Extension function to convert ScanResult to WifiNetwork
 */
fun ScanResult.toWifiNetwork(): WifiNetwork {
    return WifiNetwork(
        ssid = this.SSID,
        bssid = this.BSSID,
        signal = this.level,
        isSecured = this.capabilities.contains("WPA") || this.capabilities.contains("WEP"),
        capabilities = this.capabilities
    )
}
