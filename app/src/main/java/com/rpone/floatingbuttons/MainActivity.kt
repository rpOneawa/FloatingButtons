package com.rpone.floatingbuttons

import android.annotation.SuppressLint
import android.app.Service
import android.content.*
import android.content.res.Resources
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.*
import android.provider.Settings
import android.text.Html
import android.text.TextUtils
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.lang.Process

class MainActivity : AppCompatActivity() {
    companion object {
        // 声明悬浮窗所需的变量
        private lateinit var floatingWindowManager: WindowManager
        private lateinit var floatingWindow: View
        private lateinit var params: WindowManager.LayoutParams
    }

    // 定义需要的按钮点击状态变量
    private var catchUpKeyClicked = false
    private var catchDownKeyClicked = false
    private var catchScreenClicked = false

    // 获取运行时
    private val runtime: Runtime = Runtime.getRuntime()

    private var stopCatchUp = false
    private var stopCatchDown = false
    private var stopCatchScreen = false

    private var screenEventNumber = -1
    private var keyUpID = -1
    private var keyUpEventNumber = -1
    private var keyDownID = -1
    private var keyDownEventNumber = -1

    // 声明悬浮窗上按钮所需的变量
    private lateinit var upButton: Button
    private lateinit var downButton: Button

    // 声明界面上按钮所需的变量
    private lateinit var screenCatchButton: Button
    private lateinit var upKeyCatchButton: Button
    private lateinit var downKeyCatchButton: Button

    // 声明界面上文本框所需的变量
    private lateinit var upKeyEventEditText: EditText
    private lateinit var upKeyIdEditText: EditText
    private lateinit var downKeyEventEditText: EditText
    private lateinit var downKeyIdEditText: EditText

    private lateinit var sharedPref: SharedPreferences

    // 悬浮窗后台服务类
    class FloatingWindowService : Service() {
        companion object {
            const val SERVICE_READY_ACTION = "SERVICE_READY_ACTION"
        }

        override fun onCreate() {
            Log.d("floating window service", "started")
            super.onCreate()
            // 创建悬浮窗
            val params = WindowManager.LayoutParams(
                100, // 宽度
                200, // 高度
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, // 窗口类型
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, // 不可获得焦点
                PixelFormat.TRANSLUCENT // 半透明
            )
            // 设置悬浮窗的位置
            params.gravity = Gravity.TOP or Gravity.END
            // 位于屏幕最右侧
            params.x = 0
            // 位于 1/5 的高度处
            params.y = (Resources.getSystem().displayMetrics.heightPixels / 5)
            // 显示悬浮窗
            floatingWindowManager.addView(floatingWindow, params)
            // 发送广播，表示服务已准备就绪
            val intent = Intent(SERVICE_READY_ACTION)
            sendBroadcast(intent)
        }

        override fun onDestroy() {
            super.onDestroy()
            // 移除悬浮窗
            floatingWindowManager.removeView(floatingWindow)
        }

        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            return START_STICKY
        }

        override fun onBind(intent: Intent?): IBinder? {
            return null
        }
    }

    // 重写 ArrayAdapter，以实现修改列表当前项目的背景颜色
    class MyArrayAdapter(context: Context, items: Array<String>) :
        ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, items) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getView(position, convertView, parent)
            if (position == 0) {
                view.setBackgroundColor(Color.parseColor("#BBDEFB"))
            }
            return view
        }
    }

    // 显示右上角菜单
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    // 右上角菜单中 item 点击动作
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_about -> {
                val link = "<a href='https://github.com/rpOneawa/FloatingButtons'>https://github.com/rpOneawa/FloatingButtons</a>"
                val message = Html.fromHtml(link)

                val builder = AlertDialog.Builder(this)
                    .setTitle("关于")
                    .setMessage(message)
                    .setPositiveButton("确定", null)

                val dialog = builder.create()
                dialog.show()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    // 接收器，内容是收到悬浮窗创建完成后的广播后执行的操作
    private val serviceReadyReceiver = object : BroadcastReceiver() {
        @SuppressLint("ClickableViewAccessibility")
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("receiver", "received")

            // 初始化按钮所需的变量
            upButton = floatingWindow.findViewById(R.id.up_button)
            downButton = floatingWindow.findViewById(R.id.down_button)

            val runtime = Runtime.getRuntime()
            // 请求 Root 权限
            val proc = runtime.exec("su")

            // 设置按钮的监听器，以在触摸按钮时模拟键盘按键
            upButton.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        try {
                            // 按下按钮时模拟按下键盘上键
                            val os = DataOutputStream(proc.outputStream)
                            // 发送上键的 Down 事件
                            os.writeBytes("sendevent /dev/input/event$keyUpEventNumber 1 $keyUpID 1\n")
                            // sync 状态
                            os.writeBytes("sendevent /dev/input/event$keyUpEventNumber 0 0 0\n")
                            // 刷新输出流
                            os.flush()
                        } catch (e: Exception) {
                            Toast.makeText(applicationContext, "出现错误，可能是没有 Root 权限", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        try {
                            // 松开按钮时模拟松开键盘上键
                            val os = DataOutputStream(proc.outputStream)
                            // 发送上键的 Up 事件
                            os.writeBytes("sendevent /dev/input/event$keyUpEventNumber 1 $keyUpID 0\n")
                            // sync 状态
                            os.writeBytes("sendevent /dev/input/event$keyUpEventNumber 0 0 0\n")
                            // 刷新输出流
                            os.flush()
                        } catch (e: Exception) {
                            Toast.makeText(applicationContext, "出现错误，可能是没有 Root 权限", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                }
                true
            }

            downButton.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        try {
                            // 松开按钮时模拟按下键盘下键
                            val os = DataOutputStream(proc.outputStream)
                            // 发送下键的 Down 事件
                            os.writeBytes("sendevent /dev/input/event$keyDownEventNumber 1 $keyDownID 1\n")
                            //sync 状态
                            os.writeBytes("sendevent /dev/input/event$keyDownEventNumber 0 0 0\n")
                            // 刷新输出流
                            os.flush()
                        } catch (e: Exception) {
                            Toast.makeText(applicationContext, "出现错误，可能是没有 Root 权限", Toast.LENGTH_SHORT)
                                .show()
                        }

                    }
                    MotionEvent.ACTION_UP -> {
                        try {
                            // 按下按钮时模拟松开键盘下键
                            val os = DataOutputStream(proc.outputStream)
                            // 发送下键的 Up 事件
                            os.writeBytes("sendevent /dev/input/event$keyDownEventNumber 1 $keyDownID 0\n")
                            //sync 状态
                            os.writeBytes("sendevent /dev/input/event$keyDownEventNumber 0 0 0\n")
                            // 刷新输出流
                            os.flush()
                        } catch (e: Exception) {
                            Toast.makeText(applicationContext, "出现错误，可能是没有 Root 权限", Toast.LENGTH_SHORT)
                                .show()
                        }

                    }
                }
                true
            }

        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 获取 DrawerLayout、Toolbar 和 ListView 的实例
        val drawerLayout =
            findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawer_layout)
        val navList = findViewById<ListView>(R.id.nav_list)
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)

        // 初始化 Toolbar
        setSupportActionBar(toolbar)

        // 设置 ListView 的适配器
        val navItems = arrayOf("主页", "布局文件编辑")
        navList.adapter = MyArrayAdapter(this, navItems)

        // 设置 ListView 点击事件
        navList.setOnItemClickListener { _, _, position, _ ->
            // 根据点击的位置打开对应的 Activity
            when (position) {
                2 -> {
                    val intent = Intent(this, FileEditActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }
            // 关闭侧边栏
            drawerLayout.closeDrawers()
        }
        // 使用 ActionBarDrawerToggle 为 Toolbar 添加打开和关闭侧边栏的功能
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.nav_drawer_open, R.string.nav_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // 设置 toolbar 上图标的资源文件
        toolbar.setNavigationIcon(R.drawable.menu_white_24dp)
        toolbar.overflowIcon = ContextCompat.getDrawable(this, R.drawable.more_horiz_white_24dp)

        val headerView = View(this)
        headerView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            32
        )
        navList.addHeaderView(headerView)

        // 初始化文本框
        upKeyEventEditText = findViewById(R.id.key_up_event)
        upKeyIdEditText = findViewById(R.id.key_up_id)
        downKeyEventEditText = findViewById(R.id.key_down_event)
        downKeyIdEditText = findViewById(R.id.key_down_id)

        sharedPref = getSharedPreferences("setting_prefs", Context.MODE_PRIVATE)

        // 该 Switch 用于控制悬浮窗的显示
        val switch = findViewById<Switch>(R.id.start_switch)

        // 获取悬浮窗及窗口管理器
        floatingWindow = View.inflate(this, R.layout.floating_window, null)
        floatingWindowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // 启用时调用读取设置方法
        loadSettings()
        // 启动时调用屏幕捕获方法
        getScreenEvent()
        // 启动时调用上键捕获方法
        catchUpKey()
        // 启动时调用下键捕获方法
        catchDownKey()
        // 启动时调用保存设置方法
        saveSettings()

        switch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // 点击 Switch 后检查是否已授予悬浮窗权限
                if (hasOverlayPermission()) {
                    // 如果已授予权限，判断是否配置完成（四个输入框均不为空）
                    if ((!TextUtils.isEmpty(upKeyEventEditText.text))
                        && (!TextUtils.isEmpty(upKeyIdEditText.text))
                        && (!TextUtils.isEmpty(downKeyEventEditText.text))
                        && (!TextUtils.isEmpty(downKeyIdEditText.text))
                    ) {
                        // 将输入框内容赋值给对应变量
                        keyUpEventNumber = upKeyEventEditText.text.toString().toInt()
                        keyUpID = upKeyIdEditText.text.toString().toInt()
                        keyDownEventNumber = downKeyEventEditText.text.toString().toInt()
                        keyDownID = downKeyIdEditText.text.toString().toInt()

                        // 保存设置
                        saveSettings()

                        // 显示悬浮窗
                        showFloatingWindow()
                    } else {
                        switch.isChecked = false
                        Toast.makeText(applicationContext, "请先完成配置", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // 如果未授予权限，请求权限并弹出 Toast
                    switch.isChecked = false
                    requestOverlayPermission()
                    Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Switch 关闭时，隐藏悬浮窗
                hideFloatingWindow()
            }
        }
    }

    // 判断是否已授予悬浮窗权限的方法
    private fun hasOverlayPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
    }

    // 请求悬浮窗权限的方法
    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            startActivityForResult(intent, 0)
        }
    }

    private fun saveValues(key: String, value: Int) {
        val editor = sharedPref.edit()
        editor.putInt(key, value)
        editor.apply()
    }

    private fun loadSettings() {
        // 如果缓存中存在对应键值
        if ((sharedPref.contains("key-up-event"))
            && (sharedPref.contains("key-up-id"))
            && (sharedPref.contains("key-down-event"))
            && (sharedPref.contains("key-down-id"))
        ) {
            // 将值赋给对应变量
            keyUpEventNumber = sharedPref.getInt("key-up-event", 0)
            keyUpID = sharedPref.getInt("key-up-id", 0)
            keyDownEventNumber = sharedPref.getInt("key-down-event", 0)
            keyDownID = sharedPref.getInt("key-down-id", 0)

            // 填入文本框
            upKeyEventEditText.setText(keyUpEventNumber.toString())
            upKeyIdEditText.setText(keyUpID.toString())
            downKeyEventEditText.setText(keyDownEventNumber.toString())
            downKeyIdEditText.setText(keyDownID.toString())
        }
    }

    private fun saveSettings() {
        val saveSettingsButton = findViewById<Button>(R.id.save_btn)
        saveSettingsButton.setOnClickListener {
            if ((!TextUtils.isEmpty(upKeyEventEditText.text))
                && (!TextUtils.isEmpty(upKeyIdEditText.text))
                && (!TextUtils.isEmpty(downKeyEventEditText.text))
                && (!TextUtils.isEmpty(downKeyIdEditText.text))
            ) {
                // 将输入框内容赋值给对应变量
                keyUpEventNumber = upKeyEventEditText.text.toString().toInt()
                keyUpID = upKeyIdEditText.text.toString().toInt()
                keyDownEventNumber = downKeyEventEditText.text.toString().toInt()
                keyDownID = downKeyIdEditText.text.toString().toInt()

                // 写入变量进缓存
                saveValues("key-up-event", keyUpEventNumber)
                saveValues("key-up-id", keyUpID)
                saveValues("key-down-event", keyDownEventNumber)
                saveValues("key-down-id", keyDownID)

                Toast.makeText(application, "设置已保存", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(applicationContext, "请先完成配置", Toast.LENGTH_SHORT).show()
            }
        }
    }

    //屏幕捕获方法
    private fun getScreenEvent() {
        var resultList = mutableListOf<String>()
        // 创建消息处理器
        val handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                if (msg.obj is String) {
                    // 将读取到的结果添加到 resultList 中
                    resultList.add(msg.obj as String)
                }
                Log.d("catch-screen", msg.obj as String)
            }
        }

        screenCatchButton = findViewById(R.id.get_screen_event)
        screenCatchButton.setOnClickListener {
            if (!catchScreenClicked) {
                resultList.clear()
                Toast.makeText(applicationContext, "请点击“停止获取”", Toast.LENGTH_SHORT).show()
                screenCatchButton.text = getString(R.string.stop_catching)
                catchScreenClicked = true

                val catching_proc: Process = runtime.exec("su")
                // getevent 的 shell 输出列表

                val os = DataOutputStream(catching_proc.outputStream)
                // 运行 getevent 命令
                os.writeBytes("getevent\n")
                os.flush()

                val reader = BufferedReader(InputStreamReader(catching_proc.inputStream))

                stopCatchScreen = false
                // 创建新线程来执行读取操作
                Thread {
                    var line: String
                    while (!stopCatchScreen) {
                        line = reader.readLine()
                        // 将读取到的结果发送到 UI 线程
                        handler.sendMessage(Message.obtain(handler, 0, line))
                    }
                    catching_proc.destroy()
                    catching_proc.waitFor()
                }.start()
            } else {
                screenCatchButton.text = getString(R.string.get_screen_event)
                catchScreenClicked = false

                val lastLine = resultList.lastOrNull()
                val regex = "event(\\d+):".toRegex()
                val matchResult = lastLine?.let { it1 -> regex.find(it1) }
                if (matchResult != null) {
                    screenEventNumber = matchResult.groupValues[1].toInt()
                }

                stopCatchScreen = true
                Log.d("screen-event-id", screenEventNumber.toString())

                Thread.currentThread().interrupt() // 中断线程

                Toast.makeText(applicationContext, "获取成功", Toast.LENGTH_SHORT).show()
            }
        }

    }

    // 上键捕获方法
    private fun catchUpKey() {
        var resultList = mutableListOf<String>()
        // 创建消息处理器
        val handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                if (msg.obj is String) {
                    // 将读取到的结果添加到 resultList 中
                    resultList.add(msg.obj as String)
                }
                Log.d("catch-up", msg.obj as String)
            }
        }

        // 定义变量
        upKeyCatchButton = findViewById(R.id.key_up_sync_btn)

        upKeyCatchButton.setOnClickListener {
            if (screenEventNumber != -1) {
                if (!catchUpKeyClicked) {
                    resultList.clear()
                    Toast.makeText(applicationContext, "请单击需要模拟的按键", Toast.LENGTH_SHORT).show()
                    upKeyCatchButton.text = getString(R.string.stop_catching)
                    catchUpKeyClicked = true

                    val catching_proc: Process = runtime.exec("su")
                    // getevent 的 shell 输出列表

                    val os = DataOutputStream(catching_proc.outputStream)
                    // 运行 getevent 命令
                    os.writeBytes("getevent\n")
                    os.flush()

                    val reader = BufferedReader(InputStreamReader(catching_proc.inputStream))

                    stopCatchUp = false

                    // 创建新线程来执行读取操作
                    Thread {
                        var line: String
                        while (reader.readLine().also { line = it } != null && !stopCatchUp) {
                            // 将读取到的结果发送到 UI 线程
                            handler.sendMessage(Message.obtain(handler, 0, line))
                        }
                        catching_proc.destroy()
                        catching_proc.waitFor()
                    }.start()
                } else {
                    upKeyCatchButton.text = getString(R.string.catch_key)
                    catchUpKeyClicked = false

                    stopCatchUp = true

                    Thread.currentThread().interrupt() // 中断线程

                    resultList.removeAll { it.contains("event$screenEventNumber") }
                    resultList.removeAll { it.contains("0000 0000 00000000") }
                    val lastLine = resultList.lastOrNull()
                    if (lastLine != null) {
                        Log.d("key-up-last-line", lastLine)
                    }

                    val regex = "([0-9a-fA-F]{4}) ([0-9a-fA-F]{4})".toRegex()
                    val matchResult = lastLine?.let { it1 -> regex.find(it1) }
                    if (matchResult != null) {
                        keyUpID = matchResult.groupValues[2].toInt(16)
                        upKeyIdEditText.setText(keyUpID.toString())
                        Log.d("key-up-id", keyUpID.toString())
                    }

                    val regex1 = "event(\\d+):".toRegex()
                    val matchResult1 = lastLine?.let { it1 -> regex1.find(it1) }
                    if (matchResult1 != null) {
                        keyUpEventNumber = matchResult1.groupValues[1].toInt()
                        upKeyEventEditText.setText(keyUpEventNumber.toString())
                        Log.d("key-up-event", keyUpEventNumber.toString())
                    }


                }
            } else {
                Toast.makeText(applicationContext, "请先获取屏幕的 Event ID", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 下键捕获方法
    private fun catchDownKey() {
        var resultList = mutableListOf<String>()
        // 创建消息处理器
        val handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                if (msg.obj is String) {
                    // 将读取到的结果添加到 resultList 中
                    resultList.add(msg.obj as String)
                }
                Log.d("catch-down", msg.obj as String)
            }
        }

        // 定义变量
        downKeyCatchButton = findViewById(R.id.key_down_sync_btn)

        downKeyCatchButton.setOnClickListener {
            if (screenEventNumber != -1) {
                if (!catchDownKeyClicked) {
                    resultList.clear()
                    Toast.makeText(applicationContext, "请单击需要模拟的按键", Toast.LENGTH_SHORT).show()
                    downKeyCatchButton.text = getString(R.string.stop_catching)
                    catchDownKeyClicked = true

                    val catching_proc: Process = runtime.exec("su")
                    // getevent 的 shell 输出列表

                    val os = DataOutputStream(catching_proc.outputStream)
                    // 运行 getevent 命令
                    os.writeBytes("getevent\n")
                    os.flush()

                    val reader = BufferedReader(InputStreamReader(catching_proc.inputStream))

                    stopCatchDown = false

                    // 创建新线程来执行读取操作
                    Thread {
                        var line: String
                        while (reader.readLine().also { line = it } != null && !stopCatchDown) {
                            // 将读取到的结果发送到 UI 线程
                            handler.sendMessage(Message.obtain(handler, 0, line))
                        }
                        catching_proc.destroy()
                        catching_proc.waitFor()
                    }.start()
                } else {
                    downKeyCatchButton.text = getString(R.string.catch_key)
                    catchDownKeyClicked = false

                    stopCatchDown = true

                    Thread.currentThread().interrupt() // 中断线程

                    resultList.removeAll { it.contains("event$screenEventNumber") }
                    resultList.removeAll { it.contains("0000 0000 00000000") }
                    val lastLine = resultList.lastOrNull()
                    if (lastLine != null) {
                        Log.d("key-down-last-line", lastLine)
                    }

                    val regex = "([0-9a-fA-F]{4}) ([0-9a-fA-F]{4})".toRegex()
                    val matchResult = lastLine?.let { it1 -> regex.find(it1) }
                    if (matchResult != null) {
                        keyDownID = matchResult.groupValues[2].toInt(16)
                        downKeyIdEditText.setText(keyDownID.toString())
                        Log.d("key-down-id", keyDownID.toString())
                    }

                    val regex1 = "event(\\d+):".toRegex()
                    val matchResult1 = lastLine?.let { it1 -> regex1.find(it1) }
                    if (matchResult1 != null) {
                        keyDownEventNumber = matchResult1.groupValues[1].toInt()
                        downKeyEventEditText.setText(keyDownEventNumber.toString())
                        Log.d("key-down-event", keyDownEventNumber.toString())
                    }


                }
            } else {
                Toast.makeText(applicationContext, "请先获取屏幕的 Event ID", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 显示悬浮窗的方法
    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingWindow() {
        // 注册广播接收器
        val filter = IntentFilter(FloatingWindowService.SERVICE_READY_ACTION)
        registerReceiver(serviceReadyReceiver, filter)

        // 启动服务
        val intent = Intent(this, FloatingWindowService::class.java)
        startService(intent)
    }

    // 隐藏悬浮窗
    private fun hideFloatingWindow() {
        val intent = Intent(this, FloatingWindowService::class.java)
        stopService(intent)
    }
}