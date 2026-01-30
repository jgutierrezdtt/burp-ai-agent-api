package com.six2dez.burp.aiagent.ui.panels

import burp.api.montoya.MontoyaApi
import com.six2dez.burp.aiagent.context.ContextCollector
import com.six2dez.burp.aiagent.context.ContextOptions
import com.six2dez.burp.aiagent.context.openapi.OpenApiParser
import com.six2dez.burp.aiagent.context.openapi.OpenApiSpec
import com.six2dez.burp.aiagent.context.openapi.SensitivityLevel
import com.six2dez.burp.aiagent.redact.PrivacyMode
import com.six2dez.burp.aiagent.supervisor.AgentSupervisor
import com.six2dez.burp.aiagent.ui.ChatPanel
import com.six2dez.burp.aiagent.ui.UiTheme
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.io.File
import javax.swing.*
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.table.DefaultTableModel
import kotlin.concurrent.thread

/**
 * UI panel for analyzing OpenAPI specifications.
 */
class ApiAnalysisPanel(
    private val api: MontoyaApi,
    private val supervisor: AgentSupervisor,
    private val contextCollector: ContextCollector,
    private val chatPanel: ChatPanel
) {
    private val parser = OpenApiParser()
    private var currentSpec: OpenApiSpec? = null
    
    val root = JPanel(BorderLayout()).apply {
        background = UiTheme.Colors.surface
    }
    
    private val specTitleLabel = JLabel("No spec loaded").apply {
        font = UiTheme.Typography.mono
        foreground = UiTheme.Colors.onSurface
    }
    
    private val tableModel = DefaultTableModel(
        arrayOf("Path", "Method", "Auth", "Sensitivity", "Parameters"),
        0
    )
    
    private val endpointTable = JTable(tableModel).apply {
        font = UiTheme.Typography.mono
        background = UiTheme.Colors.surface
        foreground = UiTheme.Colors.onSurface
        selectionBackground = UiTheme.Colors.primary
        gridColor = UiTheme.Colors.outline
        rowHeight = 24
        
        // Make table read-only
        setDefaultEditor(Object::class.java, null)
    }
    
    private val detailArea = JTextArea().apply {
        font = UiTheme.Typography.mono
        background = UiTheme.Colors.surface
        foreground = UiTheme.Colors.onSurface
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
    }
    
    private val progressBar = JProgressBar().apply {
        isIndeterminate = false
        isVisible = false
    }
    
    private val statusLabel = JLabel("Ready").apply {
        foreground = UiTheme.Colors.onSurface
    }
    
    init {
        buildUI()
        setupListeners()
    }
    
    private fun buildUI() {
        // Top panel: input controls
        val topPanel = JPanel(GridBagLayout()).apply {
            background = UiTheme.Colors.surface
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        }
        
        val gbc = GridBagConstraints().apply {
            insets = Insets(5, 5, 5, 5)
            fill = GridBagConstraints.HORIZONTAL
        }
        
        // Row 1: Load from file
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.weightx = 0.0
        topPanel.add(JLabel("OpenAPI File:").apply { foreground = UiTheme.Colors.onSurface }, gbc)
        
        gbc.gridx = 1
        gbc.weightx = 1.0
        val filePathField = JTextField().apply {
            font = UiTheme.Typography.mono
            isEditable = false
        }
        topPanel.add(filePathField, gbc)
        
        gbc.gridx = 2
        gbc.weightx = 0.0
        val browseButton = JButton("Browse...").apply {
            addActionListener {
                val chooser = JFileChooser().apply {
                    fileFilter = FileNameExtensionFilter("OpenAPI files (*.yaml, *.json)", "yaml", "yml", "json")
                }
                if (chooser.showOpenDialog(root) == JFileChooser.APPROVE_OPTION) {
                    filePathField.text = chooser.selectedFile.absolutePath
                    loadSpecFromFile(chooser.selectedFile)
                }
            }
        }
        topPanel.add(browseButton, gbc)
        
        // Row 2: Load from URL
        gbc.gridx = 0
        gbc.gridy = 1
        gbc.weightx = 0.0
        topPanel.add(JLabel("OpenAPI URL:").apply { foreground = UiTheme.Colors.onSurface }, gbc)
        
        gbc.gridx = 1
        gbc.weightx = 1.0
        val urlField = JTextField().apply {
            font = UiTheme.Typography.mono
        }
        topPanel.add(urlField, gbc)
        
        gbc.gridx = 2
        gbc.weightx = 0.0
        val loadUrlButton = JButton("Load URL").apply {
            addActionListener {
                val url = urlField.text.trim()
                if (url.isNotEmpty()) {
                    loadSpecFromUrl(url)
                }
            }
        }
        topPanel.add(loadUrlButton, gbc)
        
        // Row 3: Spec info
        gbc.gridx = 0
        gbc.gridy = 2
        gbc.gridwidth = 3
        gbc.weightx = 1.0
        topPanel.add(specTitleLabel, gbc)
        
        // Row 4: Action buttons
        gbc.gridx = 0
        gbc.gridy = 3
        gbc.gridwidth = 3
        val buttonPanel = JPanel().apply {
            background = UiTheme.Colors.surface
            add(JButton("Analyze with AI").apply {
                addActionListener { analyzeWithAI() }
            })
            add(JButton("Clear").apply {
                addActionListener { clearSpec() }
            })
        }
        topPanel.add(buttonPanel, gbc)
        
        // Center panel: split pane with table and detail
        val tableScroll = JScrollPane(endpointTable).apply {
            preferredSize = Dimension(800, 300)
        }
        
        val detailScroll = JScrollPane(detailArea).apply {
            preferredSize = Dimension(800, 150)
        }
        
        val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, detailScroll).apply {
            resizeWeight = 0.7
            dividerLocation = 350
        }
        
        // Bottom panel: progress and status
        val bottomPanel = JPanel(BorderLayout()).apply {
            background = UiTheme.Colors.surface
            border = BorderFactory.createEmptyBorder(5, 10, 5, 10)
            add(progressBar, BorderLayout.NORTH)
            add(statusLabel, BorderLayout.SOUTH)
        }
        
        // Assemble
        root.add(topPanel, BorderLayout.NORTH)
        root.add(splitPane, BorderLayout.CENTER)
        root.add(bottomPanel, BorderLayout.SOUTH)
    }
    
    private fun setupListeners() {
        endpointTable.selectionModel.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) {
                val selectedRow = endpointTable.selectedRow
                if (selectedRow >= 0) {
                    showEndpointDetail(selectedRow)
                }
            }
        }
    }
    
    private fun loadSpecFromFile(file: File) {
        // TODO: Añadir comprobación de número de peticiones por usuario/IP (rate limiting)
        // Ver Security.md y vulns.md
        setProgress(true, "Loading ${file.name}...")
        thread {
            val result = parser.parseFromFile(file.absolutePath)
            SwingUtilities.invokeLater {
                result.onSuccess { spec ->
                    displaySpec(spec)
                    setStatus("Loaded: ${spec.title}")
                }.onFailure { error ->
                    setStatus("Error: ${error.message}")
                    // TODO: Mostrar mensajes de error genéricos al usuario y registrar detalles solo en logs internos.
                    // Referencia: Security.md (sección 6), vulns.md (vulnerabilidad 4)
                    JOptionPane.showMessageDialog(
                        root,
                        "Failed to parse OpenAPI spec:\n${error.message}",
                        "Parse Error",
                        JOptionPane.ERROR_MESSAGE
                    )
                }
                setProgress(false)
            }
        }
    }
    
    private fun loadSpecFromUrl(url: String) {
        setProgress(true, "Loading from URL...")
        thread {
            val result = parser.parseFromUrl(url)
            SwingUtilities.invokeLater {
                result.onSuccess { spec ->
                    displaySpec(spec)
                    setStatus("Loaded: ${spec.title}")
                }.onFailure { error ->
                    setStatus("Error: ${error.message}")
                    JOptionPane.showMessageDialog(
                        root,
                        "Failed to load OpenAPI spec from URL:\n${error.message}",
                        "Load Error",
                        JOptionPane.ERROR_MESSAGE
                    )
                }
                setProgress(false)
            }
        }
    }
    
    private fun displaySpec(spec: OpenApiSpec) {
        currentSpec = spec
        specTitleLabel.text = "${spec.title} (v${spec.version}) - ${spec.endpoints.size} endpoints"
        
        // Clear existing rows
        tableModel.rowCount = 0
        
        // Populate table
        spec.endpoints.sortedByDescending { it.dataSensitivity }.forEach { endpoint ->
            tableModel.addRow(arrayOf(
                endpoint.path,
                endpoint.method,
                if (endpoint.security.isNotEmpty()) "✓" else "✗",
                endpoint.dataSensitivity.name,
                endpoint.parameters.size
            ))
        }
        
        detailArea.text = "Select an endpoint to view details"
    }
    
    private fun showEndpointDetail(rowIndex: Int) {
        val spec = currentSpec ?: return
        val endpoint = spec.endpoints
            .sortedByDescending { it.dataSensitivity }
            .getOrNull(rowIndex) ?: return
        
        val detail = buildString {
            appendLine("Endpoint: ${endpoint.method} ${endpoint.path}")
            appendLine()
            
            if (endpoint.summary != null) {
                appendLine("Summary: ${endpoint.summary}")
            }
            
            if (endpoint.description != null) {
                appendLine("Description: ${endpoint.description}")
                appendLine()
            }
            
            appendLine("Sensitivity: ${endpoint.dataSensitivity}")
            appendLine()
            
            if (endpoint.security.isNotEmpty()) {
                appendLine("Security:")
                endpoint.security.forEach { secReq ->
                    secReq.forEach { (scheme, scopes) ->
                        appendLine("  - $scheme: ${scopes.joinToString(", ")}")
                    }
                }
                appendLine()
            }
            
            if (endpoint.parameters.isNotEmpty()) {
                appendLine("Parameters:")
                endpoint.parameters.forEach { param ->
                    val classification = param.classification?.let { " [${it.category}]" } ?: ""
                    appendLine("  - ${param.name} (${param.location}): ${param.schema.type}${classification}")
                }
                appendLine()
            }
            
            if (endpoint.requestBody != null) {
                appendLine("Request Body:")
                endpoint.requestBody!!.content.forEach { (mediaType, content) ->
                    appendLine("  - $mediaType: ${content.schema?.type ?: "unknown"}")
                }
                appendLine()
            }
            
            if (endpoint.classification.isNotEmpty()) {
                appendLine("Data Classifications:")
                endpoint.classification.sortedByDescending { it.sensitivity }.forEach { classification ->
                    appendLine("  - ${classification.fieldPath}: ${classification.category} (${classification.sensitivity})")
                    appendLine("    Reason: ${classification.reason}")
                }
            }
        }
        
        detailArea.text = detail
        detailArea.caretPosition = 0
    }
    
    private fun analyzeWithAI() {
        // TODO: Limitar la frecuencia de análisis por usuario/IP (rate limiting)
        // Ver Security.md y vulns.md
        setProgress(true, "Analyzing with AI...")
        thread {
            try {
                val options = ContextOptions(
                    privacyMode = PrivacyMode.BALANCED,
                    deterministic = true,
                    hostSalt = "api-analysis"
                )
                val context = contextCollector.fromApiSpec(spec, options)
                val prompt = buildString {
                    appendLine("Analyze this OpenAPI specification for security vulnerabilities and design issues.")
                    appendLine()
                    appendLine("Focus on:")
                    appendLine("- Endpoints without authentication that should require it")
                    appendLine("- Potential IDOR vulnerabilities (IDs in paths without ownership validation)")
                    appendLine("- Privilege escalation risks")
                    appendLine("- Sensitive data exposure")
                    appendLine("- Missing rate limiting on critical operations")
                    appendLine("- Mass assignment vulnerabilities")
                    appendLine()
                    appendLine("Provide specific findings with severity levels and remediation steps.")
                }
                SwingUtilities.invokeLater {
                    chatPanel.loadContext(context)
                    setProgress(false)
                    setStatus("Analysis ready - use Chat to send")
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    setProgress(false)
                    setStatus("Error: ${e.message}")
                    api.logging().logToError("API analysis error: ${e.message}")
                }
            }
        }
    }
    
    private fun clearSpec() {
        currentSpec = null
        specTitleLabel.text = "No spec loaded"
        tableModel.rowCount = 0
        detailArea.text = ""
        setStatus("Ready")
    }
    
    private fun setProgress(visible: Boolean, message: String = "") {
        progressBar.isVisible = visible
        progressBar.isIndeterminate = visible
        if (message.isNotEmpty()) {
            setStatus(message)
        }
    }
    
    private fun setStatus(message: String) {
        statusLabel.text = message
        api.logging().logToOutput("[ApiAnalysis] $message")
    }
}
