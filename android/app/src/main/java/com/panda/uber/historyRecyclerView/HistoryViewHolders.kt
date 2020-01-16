package com.panda.uber.historyRecyclerView

import android.content.Intent
import android.os.Bundle
import androidx.recyclerview.widget.RecyclerView
import android.view.View
import android.widget.TextView

import com.panda.uber.HistorySingleActivity
import com.panda.uber.R

/**
 * Created by manel on 10/10/2017.
 */

class HistoryViewHolders(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

    var rideId: TextView
    var time: TextView

    init {
        itemView.setOnClickListener(this)

        rideId = itemView.findViewById<View>(R.id.rideId) as TextView
        time = itemView.findViewById<View>(R.id.time) as TextView
    }


    override fun onClick(v: View) {
        val intent = Intent(v.context, HistorySingleActivity::class.java)
        val b = Bundle()
        b.putString("rideId", rideId.text.toString())
        intent.putExtras(b)
        v.context.startActivity(intent)
    }
}
