package com.peak.salut.demo

import android.app.PendingIntent.getActivity
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.bluelinelabs.logansquare.LoganSquare
import com.pascalwelsch.arrayadapter.ArrayAdapter
import com.peak.salut.Callbacks.SalutCallback
import com.peak.salut.Callbacks.SalutDataCallback
import com.peak.salut.Callbacks.SalutDeviceCallback
import com.peak.salut.Salut
import com.peak.salut.SalutDataReceiver
import com.peak.salut.SalutDevice
import com.peak.salut.SalutServiceData
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*

class MainActivity : AppCompatActivity() {

    enum class UiState {
        DEFAULT,
        HOST_MODE,
        CLIENT_MODE
    }

    class MySalut(dataReceiver: SalutDataReceiver?, salutServiceData: SalutServiceData?, deviceNotSupported: SalutCallback?) : Salut(dataReceiver, salutServiceData, deviceNotSupported) {
        override fun serialize(o: Any?): String {
            return LoganSquare.serialize(o)
        }
    }

    private lateinit var salut: Salut
    private var uiState = UiState.DEFAULT

    private lateinit var connectedDevicesAdapter: SimpleAdapter

    companion object {
        const val TAG = "SalutDemo"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val dataReceiver = SalutDataReceiver(this, SalutDataCallback {

        })

        val instanceName = "Demo ID ${Random().nextInt(200)}"

        instanceNameLabel.text = "My Instance Name: $instanceName"

        val serviceData = SalutServiceData("sas", 50489, instanceName)

        salut = MySalut(dataReceiver, serviceData, SalutCallback {
            Log.e(TAG, "Device does not support WiFi P2P")
        })

        list.layoutManager = LinearLayoutManager(this)
        connectedDevicesAdapter = SimpleAdapter()
        list.adapter = connectedDevicesAdapter

        startService.setOnClickListener { _ ->
            updateUiState(UiState.HOST_MODE)

            salut.startNetworkService({
                connectedDevicesAdapter.add(it)
                Toast.makeText(this.baseContext, it.readableName + " connected.", Toast.LENGTH_SHORT).show();
            }, {
                Log.d(TAG, "Network service started")
            }, {
                Log.e(TAG, "Can not start network service")
            })
        }

        connectService.setOnClickListener { _ ->
            updateUiState(UiState.CLIENT_MODE)

            connectedDevicesAdapter.setAction("Connect") { position ->
                val salutDevice = connectedDevicesAdapter.getItem(position)
                salut.registerWithHost(salutDevice, {
                    Log.d(TAG, "Successfully registered")
                    Toast.makeText(this.baseContext, "Successfully connected to " + this.salut.registeredHost.readableName, Toast.LENGTH_SHORT).show();
                }, {
                    Log.e(TAG, "Error registering")
                })
            }

            salut.discoverNetworkServices(SalutDeviceCallback {
                connectedDevicesAdapter.add(it)


            }, true)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        MenuInflater(this).inflate(R.menu.menu_demo, menu)

        if (uiState == UiState.DEFAULT) {
            menu?.removeItem(R.id.stop)
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item?.itemId == R.id.stop) {
            if (uiState == UiState.HOST_MODE) {
                salut.stopNetworkService(false)
                updateUiState(UiState.DEFAULT)
            } else if (uiState == UiState.CLIENT_MODE) {
                salut.stopServiceDiscovery(true)
                updateUiState(UiState.DEFAULT)
            }
            return true
        }

        return false
    }

    private fun updateUiState(mode: UiState) {
        this.uiState = mode
        startService.visibility = if (mode == UiState.HOST_MODE) View.VISIBLE else View.GONE
        connectService.visibility = if (mode == UiState.CLIENT_MODE) View.VISIBLE else View.GONE

        invalidateOptionsMenu()
    }

    class SimpleAdapter : ArrayAdapter<SalutDevice, SimpleViewHolder>() {

        private var actionTitle: String? = null
        private var action: ((index: Int) -> Unit)? = null

        fun setAction(title: String, action: ((index: Int) -> Unit)) {
            actionTitle = title
            this.action = action

            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SimpleViewHolder {
            val view = LayoutInflater.from(parent.context)
            return SimpleViewHolder(view.inflate(R.layout.list_item_device, parent, false))
        }

        override fun getItemId(item: SalutDevice): Any? {
            return item.macAddress
        }

        override fun onBindViewHolder(viewHolder: SimpleViewHolder, position: Int) {
            viewHolder.titleView.text = getItem(position)?.instanceName

            viewHolder.action.visibility = if (action != null) View.VISIBLE else View.GONE
            if (action != null) {
                viewHolder.action.text = actionTitle
                viewHolder.action.setOnClickListener { _ ->
                    action?.let { it -> it(position) }
                }
            }
        }
    }

    class SimpleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleView: TextView = itemView.findViewById(R.id.title)
        val action: Button = itemView.findViewById(R.id.action)
    }
}
