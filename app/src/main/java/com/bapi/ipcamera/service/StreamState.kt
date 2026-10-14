package com.bapi.ipcamera.service

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.bapi.ipcamera.stream.FrameBroadcaster

/** Trạng thái chia sẻ giữa Service và UI trong cùng process. */
object StreamState {
    val running = MutableLiveData(false)
    val clients = MutableLiveData(0)
    val address = MutableLiveData("")
    val torchOn = MutableLiveData(false)
    val nightMode = MutableLiveData(false)
    val frontCamera = MutableLiveData(false)

    /** Preview trong app đang bật → giữ camera sống dù không có client HTTP. */
    val previewActive = MutableLiveData(false)

    /** Broadcaster của service hiện tại để MainActivity đọc frame cho preview. */
    @Volatile
    var broadcaster: FrameBroadcaster? = null

    fun notifyClients(count: Int) {
        clients.postValue(count)
    }

    fun notifyRunning(value: Boolean) {
        running.postValue(value)
    }

    fun notifyAddress(value: String) {
        address.postValue(value)
    }

    fun notifyNight(value: Boolean) {
        nightMode.postValue(value)
    }

    fun observeClients(): LiveData<Int> = clients
}
