package com.yelp.android.bento.core

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.yelp.android.bento.componentcontrollers.RecyclerViewComponentController.constructViewHolder
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.Executors


/**
 * This acts as a bridge between RecyclerViewComponentController and the underlying
 * RecyclerView.Adapter. It intercepts the addComponent() calls and inflates views with
 * [BentoAsyncLayoutInflater]. Components are added to the RecyclerView when the inflation
 * is finished.
 */
internal class AsyncInflationBridge(val recyclerView: RecyclerView) {

    private companion object {
        private val lock = Mutex()
        private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    }

    private val viewHolderMap = mutableMapOf<Class<out ComponentViewHolder<*, *>>,
            MutableList<ComponentViewHolder<*, *>>>()
    private val viewMap = mutableMapOf<ComponentViewHolder<*, *>, View?>()

    /**
     * Inflates the views and creates the view holder for [component] on a background thread,
     * getting them nice and ready for RecyclerViewComponentController to use them. The
     */
    fun asyncInflateViewsForComponent(component: Component, addComponentCallback: () -> Unit) {
        GlobalScope.launch(dispatcher) {
            val deferredInflations = (0 until component.count).map { i ->
                async {
                    val viewHolderType = component.getHolderType(i)
                    val viewHolder: ComponentViewHolder<*, *> = constructViewHolder(viewHolderType)
                    addViewHolder(viewHolder, viewHolderType)
                    val pair = BentoAsyncLayoutInflater.inflate(viewHolder, recyclerView)
                    viewMap[viewHolder] = pair.second
                }
            }
            lock.withLock {
                deferredInflations.awaitAll()
            }
            withContext(Dispatchers.Main) {
                try {
                    addComponentCallback()
                } catch (e: IllegalArgumentException) {
                    // TODO: handle crash if component is already added.
                }
            }
        }
    }

    /**
     * Retrieves a previously async inflated view if there is one.
     */
    @Synchronized
    fun getView(viewHolder: ComponentViewHolder<*, *>) = viewMap[viewHolder]

    /**
     * Retrieves a previously created view holder of
     */
    fun getViewHolder(
            viewHolderType: Any? // Class<out ComponentViewHolder<*, *>> doesn't work with Java.
    ): ComponentViewHolder<*, *>? {
        if (viewHolderMap.containsKey(viewHolderType)) {
            val viewHolderList = viewHolderMap[viewHolderType]
            viewHolderList?.let {
                if (viewHolderList.isNotEmpty()) {
                    return viewHolderList.removeAt(0)
                }
            }
        }
        return null
    }

    private fun addViewHolder(
            viewHolder: ComponentViewHolder<*, *>,
            viewHolderType: Class<out ComponentViewHolder<*, *>>
    ) {
        var viewHolders = viewHolderMap[viewHolderType]
        if (viewHolders == null) {
            viewHolders = mutableListOf()
        }
        viewHolders.add(viewHolder)
        viewHolderMap[viewHolderType] = viewHolders
    }
}
