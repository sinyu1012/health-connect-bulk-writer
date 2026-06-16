package com.sinyu.healthconnectmasswriter

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.Locale

class MainActivity : ComponentActivity() {
    private data class Profile(
        val title: String,
        val specs: List<MassRecordSpec>,
    )

    private val repository by lazy { HealthConnectRepository(this) }
    private val writer by lazy { MassHealthConnectWriter(repository.client) }
    private val profiles = listOf(
        Profile("核心压测", MassRecordSpecs.default),
        Profile("全类型模拟", MassRecordSpecs.allSynthetic),
    )
    private val scales = listOf(10_000, 100_000, 1_000_000, 2_000_000, 5_000_000, 10_000_000)

    private lateinit var statusText: TextView
    private lateinit var permissionText: TextView
    private lateinit var planText: TextView
    private lateinit var progressText: TextView
    private lateinit var resultText: TextView
    private lateinit var profileSpinner: Spinner
    private lateinit var scaleSpinner: Spinner
    private lateinit var batchSizeEdit: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var installButton: Button
    private lateinit var permissionButton: Button
    private lateinit var refreshButton: Button
    private lateinit var openButton: Button
    private lateinit var startButton: Button
    private lateinit var cancelButton: Button

    private var writeJob: Job? = null

    private val permissionLauncher =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) {
            refreshState()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContentView())
        updatePlanSummary()
        refreshState()
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    private fun buildContentView(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(28))
        }

        content.addView(title("Health Connect 海量写入", 24f))
        statusText = body()
        permissionText = body()
        planText = body()
        progressText = body()
        resultText = body()
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = 0
        }

        content.addView(statusText)
        content.addView(permissionText)

        profileSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                profiles.map { it.title },
            )
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    onPlanSelectionChanged()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        content.addView(label("写入模式"))
        content.addView(profileSpinner)

        scaleSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                scales.map { "${formatCount(it)} (${MassDataPlan.formatScale(it)})" },
            )
            setSelection(1)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    onPlanSelectionChanged()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        content.addView(label("心率规模"))
        content.addView(scaleSpinner)

        batchSizeEdit = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("500")
            setSingleLine(true)
        }
        content.addView(label("批量大小"))
        content.addView(batchSizeEdit)
        content.addView(planText)

        installButton = button("安装 HC") { repository.installHealthConnect() }
        permissionButton = button("授权全部权限") { requestAllPermissions() }
        refreshButton = button("刷新") { refreshState() }
        openButton = button("打开 HC") { repository.openHealthConnect() }
        startButton = button("开始写入") { startWriting() }
        cancelButton = button("取消") { writeJob?.cancel() }
        cancelButton.isEnabled = false

        content.addView(buttonRow(installButton, permissionButton))
        content.addView(buttonRow(refreshButton, openButton))
        content.addView(startButton)
        content.addView(cancelButton)
        content.addView(progressBar)
        content.addView(progressText)
        content.addView(resultText)

        return ScrollView(this).apply { addView(content) }
    }

    private fun refreshState() {
        lifecycleScope.launch {
            if (!repository.isAvailable()) {
                statusText.text = "Health Connect 不可用"
                permissionText.text = "请先安装或更新 Google Health Connect。"
                installButton.isEnabled = true
                permissionButton.isEnabled = false
                openButton.isEnabled = false
                startButton.isEnabled = false
                cancelButton.isEnabled = writeJob != null
                return@launch
            }

            installButton.isEnabled = false
            openButton.isEnabled = true
            val total = repository.allRuntimePermissions().size
            val grantedPermissions = runCatching { repository.grantedPermissions() }.getOrElse { emptySet() }
            val missing = repository.allRuntimePermissions().filterNotTo(linkedSetOf()) { it in grantedPermissions }
            val missingWrite = PermissionPolicy.missingWritePermissions(
                plan = selectedPlan(),
                supportedRecordTypes = repository.supportedRecordTypes(),
                grantedPermissions = grantedPermissions,
            )
            val granted = total - missing.size
            statusText.text = "Health Connect 已就绪"
            permissionText.text = buildPermissionStatusText(granted, total, missing, missingWrite)
            permissionButton.isEnabled = missing.isNotEmpty() && writeJob == null
            startButton.isEnabled = missingWrite.isEmpty() && writeJob == null
            cancelButton.isEnabled = writeJob != null
        }
    }

    private fun requestAllPermissions() {
        if (!repository.isAvailable()) {
            repository.installHealthConnect()
            return
        }
        permissionLauncher.launch(repository.allRuntimePermissions())
    }

    private fun startWriting() {
        if (writeJob != null) return
        if (!repository.isAvailable()) {
            repository.installHealthConnect()
            return
        }

        writeJob = lifecycleScope.launch {
            val grantedPermissions = repository.grantedPermissions()
            val missingWrite = PermissionPolicy.missingWritePermissions(
                plan = selectedPlan(),
                supportedRecordTypes = repository.supportedRecordTypes(),
                grantedPermissions = grantedPermissions,
            )
            if (missingWrite.isNotEmpty()) {
                resultText.text = "缺少当前写入所需权限：${formatPermissions(missingWrite)}"
                permissionLauncher.launch(repository.allRuntimePermissions())
                writeJob = null
                return@launch
            }

            val plan = selectedPlan()
            setRunning(true)
            progressBar.progress = 0
            progressText.text = "准备写入..."
            resultText.text = ""

            try {
                val supportedTypes = repository.supportedRecordTypes()
                val result = withContext(Dispatchers.IO) {
                    writer.write(
                        plan = plan,
                        supportedRecordTypes = supportedTypes,
                        anchor = Instant.now(),
                    ) { progress ->
                        runOnUiThread { showProgress(progress) }
                    }
                }
                progressBar.progress = 1000
                resultText.text = buildResultText(result)
            } catch (_: CancellationException) {
                resultText.text = "已取消"
            } catch (t: Throwable) {
                resultText.text = "写入失败：${t.message ?: t::class.java.simpleName}"
            } finally {
                writeJob = null
                setRunning(false)
                refreshState()
            }
        }
    }

    private fun selectedPlan(): MassDataPlan {
        val profile = profiles[profileSpinner.selectedItemPosition.coerceAtLeast(0)]
        val heartRateCount = scales[scaleSpinner.selectedItemPosition.coerceAtLeast(0)]
        val batchSize = batchSizeEdit.text.toString().toIntOrNull() ?: 500
        return MassDataPlan.fromHeartRateCount(
            specs = profile.specs,
            heartRateCount = heartRateCount,
            batchSize = batchSize,
        )
    }

    private fun onPlanSelectionChanged() {
        updatePlanSummary()
        if (::startButton.isInitialized) refreshState()
    }

    private fun updatePlanSummary() {
        if (!::planText.isInitialized || !::profileSpinner.isInitialized) return
        val plan = selectedPlan()
        val nonZero = plan.counts.filterValues { it > 0 }
        val preview = nonZero.entries
            .take(8)
            .joinToString(separator = "\n") { (spec, count) -> "${spec.label}: ${formatCount(count)}" }
        val tail = if (nonZero.size > 8) "\n... +${nonZero.size - 8} types" else ""
        planText.text = "预计写入 ${formatCount(plan.totalRecords)} 条，覆盖 ${nonZero.size} 种类型\n$preview$tail"
    }

    private fun showProgress(progress: MassWriteProgress) {
        val ratio = if (progress.totalPlanned == 0) 0.0 else {
            progress.totalInserted.toDouble() / progress.totalPlanned.toDouble()
        }
        progressBar.progress = (ratio.coerceIn(0.0, 1.0) * 1000).toInt()
        progressText.text = "[${progress.specIndex + 1}/${progress.specCount}] ${progress.label} " +
            "${formatCount(progress.doneForSpec)} / ${formatCount(progress.totalForSpec)} · " +
            "合计 ${formatCount(progress.totalInserted)} / ${formatCount(progress.totalPlanned)}"
    }

    private fun buildResultText(result: MassWriteResult): String {
        val counts = result.recordCounts.entries.joinToString(separator = "\n") {
            "${it.key}: ${formatCount(it.value)}"
        }
        val failures = if (result.failures.isEmpty()) {
            ""
        } else {
            "\n跳过/失败：\n${result.failures.joinToString(separator = "\n")}"
        }
        return "已写入 ${formatCount(result.totalInserted)} 条\n$counts$failures"
    }

    private fun buildPermissionStatusText(
        granted: Int,
        total: Int,
        missing: Set<String>,
        missingWrite: Set<String>,
    ): String {
        val base = "权限 $granted / $total"
        val missingText = if (missing.isEmpty()) "" else "\n缺少：${formatPermissions(missing)}"
        val writeText = if (missingWrite.isEmpty()) {
            "\n当前模式写入权限已满足，可以开始写入。"
        } else {
            "\n当前模式缺少写入权限：${formatPermissions(missingWrite)}"
        }
        return base + missingText + writeText
    }

    private fun formatPermissions(permissions: Set<String>): String =
        permissions.take(3).joinToString(separator = "、") { PermissionPolicy.displayName(it) } +
            if (permissions.size > 3) " 等 ${permissions.size} 项" else ""

    private fun setRunning(running: Boolean) {
        profileSpinner.isEnabled = !running
        scaleSpinner.isEnabled = !running
        batchSizeEdit.isEnabled = !running
        permissionButton.isEnabled = !running
        refreshButton.isEnabled = !running
        openButton.isEnabled = !running
        startButton.isEnabled = !running
        cancelButton.isEnabled = running
    }

    private fun title(text: String, size: Float): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setPadding(0, 0, 0, dp(12))
        }

    private fun body(): TextView =
        TextView(this).apply {
            textSize = 14f
            setPadding(0, dp(4), 0, dp(8))
        }

    private fun label(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 12f
            setPadding(0, dp(14), 0, dp(4))
        }

    private fun button(text: String, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            setOnClickListener { onClick() }
            isAllCaps = false
        }

    private fun buttonRow(left: Button, right: Button): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(right, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun formatCount(value: Int): String =
        String.format(Locale.US, "%,d", value)
}
