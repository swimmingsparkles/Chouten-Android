package com.chouten.app.data

import android.util.Log
import androidx.lifecycle.MutableLiveData

class DataLayer() {
    var snackbarQueue: MutableLiveData<List<SnackbarVisualsWithError>> =
        MutableLiveData(listOf())
        private set
    private var _snackbarDeleteBuffer = 0

    fun enqueueSnackbar(content: SnackbarVisualsWithError) {
        if (_snackbarDeleteBuffer > 0) {
            // Force it to pop however many need to be
            popSnackbarQueue(true)
        }

        snackbarQueue.postValue(snackbarQueue.value?.plus(content))
    }

    fun popSnackbarQueue(force: Boolean = false) {
        if (!force && _snackbarDeleteBuffer < snackbarDeleteBufferMax) {
            _snackbarDeleteBuffer += 1; return
        }

        val amountToRemove =
            if (force) _snackbarDeleteBuffer else snackbarDeleteBufferMax

        if (snackbarQueue.value == null || snackbarQueue.value?.size == 0) return

        // TODO: Fix snackbar.value
        // Using postValue causes some weird issues where the snackbar
        // doesn't ever actually clear the values off meaning that the messages
        // from before are displayed along with the new one.
        // Using `.value = <....>` can be an issue because when the app is
        // in the background there may not be a state to alter and the
        // operation will error out.
        try {
            snackbarQueue.value =
                snackbarQueue.value?.takeLast(snackbarQueue.value!!.size - amountToRemove)
        } catch(e: Exception) {
            Log.d("CHOUTEN/SNACKBAR", e.localizedMessage ?: "Snackbar Error")
        }

        _snackbarDeleteBuffer = 0
    }

    companion object {
        const val snackbarDeleteBufferMax = 5
    }
}