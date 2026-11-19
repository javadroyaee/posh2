package com.jrweb.wcmonitor

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.json.JSONObject
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager
    private lateinit var adapter: EventsAdapter
    private val events = mutableListOf<StoreEvent>()
    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var layoutConfig: LinearLayout
    private lateinit var etSiteUrl: EditText
    private lateinit var etApiKey: EditText
    private lateinit var tvStatus: TextView
    private lateinit var tvHeaderTitle: TextView
    private lateinit var swipeRefresh: SwipeRefreshLayout

    private val qrLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            try {
                val json = JSONObject(result.contents)
                val url = json.optString("url")
                val key = json.optString("key")
                val name = json.optString("name", "فروشگاه من")
                if (url.isNotEmpty() && key.isNotEmpty()) {
                    etSiteUrl.setText(url)
                    etApiKey.setText(key)
                    prefs.siteName = name
                    connectAndSave()
                } else {
                    Toast.makeText(this, "کد QR نامعتبر است", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "فرمت QR پشتیبانی نمی‌شود", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val notifPermLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            reloadListFromPrefs()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = PrefsManager(this)
        layoutConfig = findViewById(R.id.layoutConfig)
        etSiteUrl = findViewById(R.id.etSiteUrl)
        etApiKey = findViewById(R.id.etApiKey)
        tvStatus = findViewById(R.id.tvStatus)
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle)
        swipeRefresh = findViewById(R.id.swipeRefresh)

        val recycler = findViewById<RecyclerView>(R.id.recyclerEvents)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = EventsAdapter(events) { event, action ->
            handleOrderAction(event, action)
        }
        recycler.adapter = adapter

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (prefs.isConfigured()) {
            etSiteUrl.setText(prefs.siteUrl)
            etApiKey.setText(prefs.apiKey)
            tvHeaderTitle.text = prefs.siteName
            tvStatus.text = "متصل به: "
            layoutConfig.visibility = View.GONE
            PollingService.start(this)
        }

        findViewById<Button>(R.id.btnToggleConfig).setOnClickListener {
            layoutConfig.visibility = if (layoutConfig.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        findViewById<Button>(R.id.btnScanQr).setOnClickListener {
            val opts = ScanOptions().apply {
                setPrompt("کد QR داخل پیشخوان وردپرس را اسکن کنید")
                setBeepEnabled(true)
                setOrientationLocked(false)
            }
            qrLauncher.launch(opts)
        }

        findViewById<Button>(R.id.btnSaveConnect).setOnClickListener {
            connectAndSave()
        }

        swipeRefresh.setOnRefreshListener {
            executor.execute {
                PollingService.pollNow(this)
                runOnUiThread {
                    reloadListFromPrefs()
                    swipeRefresh.isRefreshing = false
                }
            }
        }

        reloadListFromPrefs()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(PollingService.ACTION_NEW_EVENTS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(updateReceiver, filter)
        }
        executor.execute {
            PollingService.pollNow(this)
            runOnUiThread { reloadListFromPrefs() }
        }
    }

    override fun onPause() {
        unregisterReceiver(updateReceiver)
        super.onPause()
    }

    private fun connectAndSave() {
        val url = etSiteUrl.text.toString().trim()
        val key = etApiKey.text.toString().trim()
        if (url.isEmpty() || key.isEmpty()) {
            Toast.makeText(this, "آدرس سایت و کلید API را وارد کنید", Toast.LENGTH_SHORT).show()
            return
        }
        tvStatus.text = "در حال بررسی اتصال..."
        executor.execute {
            val (ok, msg) = ApiClient.ping(url, key)
            runOnUiThread {
                if (ok) {
                    prefs.siteUrl = url
                    prefs.apiKey = key
                    prefs.siteName = msg
                    tvHeaderTitle.text = msg
                    tvStatus.text = "متصل به: "
                    layoutConfig.visibility = View.GONE
                    Toast.makeText(this, "اتصال با موفقیت برقرار شد!", Toast.LENGTH_SHORT).show()
                    PollingService.start(this)
                    executor.execute {
                        PollingService.pollNow(this)
                        runOnUiThread { reloadListFromPrefs() }
                    }
                } else {
                    tvStatus.text = msg
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun reloadListFromPrefs() {
        events.clear()
        events.addAll(prefs.getSavedEvents())
        adapter.notifyDataSetChanged()
    }

    private fun handleOrderAction(event: StoreEvent, action: String) {
        Toast.makeText(this, "در حال ارسال دستور به فروشگاه...", Toast.LENGTH_SHORT).show()
        executor.execute {
            val (ok, msg) = ApiClient.sendOrderAction(prefs.siteUrl, prefs.apiKey, event.orderId, action)
            runOnUiThread {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                if (ok) {
                    event.actionable = false
                    event.orderStatus = if (action == "approve") "processing" else "cancelled"
                    prefs.saveEvents(events)
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }
}

class EventsAdapter(
    private val items: List<StoreEvent>,
    private val onAction: (StoreEvent, String) -> Unit
) : RecyclerView.Adapter<EventsAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvBadge: TextView = v.findViewById(R.id.tvBadge)
        val tvTime: TextView = v.findViewById(R.id.tvTime)
        val tvTitle: TextView = v.findViewById(R.id.tvTitle)
        val tvMessage: TextView = v.findViewById(R.id.tvMessage)
        val layoutActions: LinearLayout = v.findViewById(R.id.layoutActions)
        val btnApprove: Button = v.findViewById(R.id.btnApprove)
        val btnReject: Button = v.findViewById(R.id.btnReject)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_event, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvTitle.text = item.title
        holder.tvMessage.text = item.message
        holder.tvTime.text = item.createdAt
        holder.tvBadge.text = when (item.eventType) {
            "new_order" -> "🛒 سفارش جدید"
            "order_status" -> "📦 وضعیت سفارش"
            "order_failed" -> "⚠️ لغو / خطای سفارش"
            "low_stock" -> "📉 کمبود موجودی"
            "out_of_stock" -> "🚨 اتمام موجودی"
            "plugin_update", "core_update" -> "🔄 بروزرسانی"
            "email_sent" -> "✉️ ایمیل"
            "sms_sent" -> "💬 پیامک"
            "login_failed", "admin_login" -> "🔐 امنیت ورود"
            else -> "🔔 اعلان"
        }

        if (item.orderId > 0 && item.actionable) {
            holder.layoutActions.visibility = View.VISIBLE
            holder.btnApprove.setOnClickListener { onAction(item, "approve") }
            holder.btnReject.setOnClickListener { onAction(item, "reject") }
        } else {
            holder.layoutActions.visibility = View.GONE
        }
    }
}
