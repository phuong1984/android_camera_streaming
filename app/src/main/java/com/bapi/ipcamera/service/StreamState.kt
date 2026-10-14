package com.bapi.ipcamera.service

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/** Trạng thái chia sẻ giữa Service và UI trong cùng process. */
object StreamState {
    val running = MutableLiveData(false)
    val clients = MutableLiveData(0)
    val address = MutableLiveData("")
    val torchOn = MutableLiveData(false)
    val nightMode = MutableLiveData(false)
    val frontCamera = MutableLiveData(false)

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
