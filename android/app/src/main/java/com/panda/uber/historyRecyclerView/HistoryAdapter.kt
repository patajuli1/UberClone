package com.panda.uber.historyRecyclerView

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import com.panda.uber.R

/**
 * Created by manel on 03/04/2017.
 */

class HistoryAdapter(private val itemList: List<HistoryObject>, private val context: Context) : RecyclerView.Adapter<HistoryViewHolders>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolders {

        val layoutView = LayoutInflater.from(parent.context).inflate(R.layout.item_history, null, false)
        val lp = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        layoutView.layoutParams = lp
        return HistoryViewHolders(layoutView)
    }

    override fun onBindViewHolder(holder: HistoryViewHolders, position: Int) {
        holder.rideId.text = itemList[position].rideId
        if (itemList[position].time != null) {
            holder.time.text = itemList[position].time
        }
    }

    override fun getItemCount(): Int {
        return this.itemList.size
    }

}