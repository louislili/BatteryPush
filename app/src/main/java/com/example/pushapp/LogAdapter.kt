package com.example.pushapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LogAdapter(private val logs: List<String>) : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconView: ImageView = itemView.findViewById(R.id.icon_log)
        val titleView: TextView = itemView.findViewById(R.id.text_log_title)
        val contentView: TextView = itemView.findViewById(R.id.text_log_content)
        val timeView: TextView = itemView.findViewById(R.id.text_log_time)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val log = logs[position]
        
        // Parse log entry
        val parts = log.split(" ", limit = 3)
        val time = if (parts.size >= 2) "${parts[0]} ${parts[1]}" else ""
        val action = if (parts.size >= 3) parts[2] else log
        
        // Set time
        holder.timeView.text = time
        
        // Determine log type and set appropriate icon and title
        when {
            action.contains("通知") -> {
                holder.iconView.setImageResource(R.drawable.ic_notification)
                holder.iconView.setColorFilter(holder.itemView.context.getColor(R.color.color_notification))
                holder.titleView.text = "电量通知"
                holder.contentView.text = action.replace("通知 ", "")
            }
            action.contains("拨打电话") -> {
                holder.iconView.setImageResource(R.drawable.ic_call)
                holder.iconView.setColorFilter(holder.itemView.context.getColor(R.color.color_call))
                holder.titleView.text = "拨打电话"
                holder.contentView.text = action.replace("拨打电话 ", "")
            }
            action.contains("发送短信") -> {
                holder.iconView.setImageResource(R.drawable.ic_sms)
                holder.iconView.setColorFilter(holder.itemView.context.getColor(R.color.color_sms))
                holder.titleView.text = "发送短信"
                holder.contentView.text = action.replace("发送短信 ", "")
            }
            action.contains("Webhook") -> {
                holder.iconView.setImageResource(R.drawable.ic_webhook)
                holder.iconView.setColorFilter(holder.itemView.context.getColor(R.color.color_webhook))
                holder.titleView.text = "Webhook"
                if (action.contains("成功")) {
                    holder.contentView.text = "推送成功"
                } else if (action.contains("失败")) {
                    holder.contentView.text = "推送失败"
                } else {
                    holder.contentView.text = "推送错误"
                }
            }
            else -> {
                holder.iconView.setImageResource(R.drawable.ic_info)
                holder.iconView.setColorFilter(holder.itemView.context.getColor(R.color.color_info))
                holder.titleView.text = "系统信息"
                holder.contentView.text = action
            }
        }
    }

    override fun getItemCount(): Int = logs.size
}