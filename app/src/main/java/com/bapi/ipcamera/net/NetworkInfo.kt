package com.bapi.ipcamera.net

import android.content.Context
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkInfo {

    /** IPv4 hiện tại của interface wlan0 (hoặc interface có IPv4 đầu tiên). */
    fun currentIpv4(): String? {
        return try {
            val ifaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (iface in ifaces) {
                if (!iface.isUp || iface.isLoopback) continue
                if (iface.name != "wlan0") continue
                for (addr in iface.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) return addr.hostAddress
                }
            }
            for (iface in ifaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) return addr.hostAddress
                }
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    /** MAC của Wi-Fi (có thể là 02:00:00:00:00:00 trên Android 6+ nếu hệ thống ẩn). */
    fun wifiMac(context: Context): String? {
        return try {
            @Suppress("DEPRECATION")
            val wm = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wm.connectionInfo?.macAddress
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Android không cung cấp API công khai để đặt IP tĩnh cho mạng đang kết nối
     * (bị ẩn/deprecated từ Android 10). Vì vậy app KHÔNG tự đổi IP; dùng DHCP
     * reservation theo MAC trên router. Hàm này luôn trả false để UI hiển thị
     * hướng dẫn thay vì giả vờ thành công.
     */
    fun canSetStaticIp(): Boolean = false
}
