package com.yelp.android.bento.core

import android.util.Log
import android.view.View
import android.view.ViewGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors


private const val TAG = "BentoAsyncInflater"

/**
 * Inflates a componentViewHolder's views on one of ten lucky background threads.
 */
internal object BentoAsyncLayoutInflater {

    private val dispatcher = Executors.newFixedThreadPool(10).asCoroutineDispatcher()

    suspend fun inflate(
            viewHolder: ComponentViewHolder<*, *>,
            parent: ViewGroup
    ): Pair<ComponentViewHolder<*, *>, View> =
            withContext(dispatcher) {
                val view = try {
                    viewHolder.inflate(parent)
                } catch (exception: RuntimeException) {
                    // Probably a Looper failure, retry on the UI thread
                    Log.w(
                            TAG, "Failed to inflate resource in the background!" +
                            " Retrying on the UI thread",
                            exception
                    )
                    withContext(Dispatchers.Main) {
                        viewHolder.inflate(parent)
                    }
                }
                Pair(viewHolder, view)
            }
}
