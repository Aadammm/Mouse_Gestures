package com.mousegesture.configs

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.mousegesture.models.GestureDirection
import com.mousegesture.models.MouseGesture
import com.mousegesture.services.GestureManagerService
import com.mousegesture.services.GestureOrchestratorService
import java.awt.*
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.util.*
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

class GestureSettingsConfig : Configurable {

    private val managerService get() = ApplicationManager.getApplication().service<GestureManagerService>()
    private val orchestratorService get() = ApplicationManager.getApplication().service<GestureOrchestratorService>()

    private lateinit var tableModel: GestureTableModel
    private lateinit var table: JBTable

    private lateinit var nameField: JBTextField
    private lateinit var patternLabel: JLabel
    private lateinit var recordButton: JButton
    private lateinit var duplicateWarningLabel: JLabel
    private lateinit var actionSearchField: JBTextField
    private lateinit var actionListModel: DefaultListModel<ActionItem>
    private lateinit var actionList: JList<ActionItem>
    private lateinit var actionIdField: JBTextField
    private lateinit var enabledCheckBox: JBCheckBox

    private lateinit var showTrailCheck: JBCheckBox
    private lateinit var showDirectionsCheck: JBCheckBox
    private lateinit var trailColorPicker: ColorPickerField
    private lateinit var matchColorPicker: ColorPickerField
    private lateinit var trailThicknessSpinner: JSpinner

    private var allActions: List<ActionItem> = emptyList()
    private val workingGestures = mutableListOf<MouseGesture>()

    private var isRecording = false
    private var suppressListeners = false
    private var suppressTableListener = false

    override fun getDisplayName() = "Mouse Gestures"

    override fun createComponent(): JComponent {
        allActions = loadIntellijActions()
        workingGestures.clear()
        workingGestures.addAll(managerService.getGestures().map { it.copy() })
        orchestratorService.setSettingsOpen(true)
        return buildMainPanel()
    }

    private fun loadIntellijActions(): List<ActionItem> {
        return try {
            val manager = ActionManager.getInstance()
            manager.getActionIds("").mapNotNull { id ->
                val text = manager.getAction(id)?.templatePresentation?.text
                    ?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                ActionItem(id, text)
            }.sortedBy { it.displayName }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun buildMainPanel(): JPanel {
        val panel = JPanel(BorderLayout(8, 8))
        panel.border = JBUI.Borders.empty(10)

        val leftPanel = JPanel(BorderLayout(4, 4))
        leftPanel.add(buildToolbar(), BorderLayout.NORTH)
        leftPanel.add(buildTable(), BorderLayout.CENTER)
        leftPanel.minimumSize = Dimension(300, 0)

        val rightPanel = JPanel(BorderLayout(4, 4))
        rightPanel.add(buildEditPanel(), BorderLayout.CENTER)
        rightPanel.add(buildVisualizationPanel(), BorderLayout.SOUTH)

        val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel)
        split.resizeWeight = 0.4
        split.isContinuousLayout = true

        panel.add(split, BorderLayout.CENTER)
        return panel
    }

    private fun buildTable(): JComponent {
        tableModel = GestureTableModel(workingGestures)
        table = JBTable(tableModel)
        table.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        table.columnModel.getColumn(0).preferredWidth = 120
        table.columnModel.getColumn(1).preferredWidth = 70
        table.columnModel.getColumn(2).preferredWidth = 130
        table.columnModel.getColumn(3).preferredWidth = 30

        table.columnModel.getColumn(3).cellRenderer = object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                t: JTable,
                value: Any?,
                sel: Boolean,
                focus: Boolean,
                row: Int,
                col: Int
            ): Component {
                val lbl = super.getTableCellRendererComponent(t, value, sel, focus, row, col) as JLabel
                lbl.text = if (value == true) "✓" else "✗"
                lbl.horizontalAlignment = SwingConstants.CENTER
                return lbl
            }
        }

        // suppressTableListener zabraňuje aby refreshTable() spúšťal loadGestureIntoEditPanel()
        // a prerušoval kliknutie na nové akcie v zozname
        table.selectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting && !suppressTableListener) loadGestureIntoEditPanel()
        }

        return JBScrollPane(table)
    }

    private fun buildToolbar(): JComponent {
        val bar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        val addBtn = JButton("+ Add")
        val delBtn = JButton("− Remove")
        val resetBtn = JButton("↺ Reset to Defaults")
        addBtn.addActionListener { addGesture() }
        delBtn.addActionListener { deleteGesture() }
        resetBtn.addActionListener { resetToDefaults() }
        bar.add(addBtn); bar.add(delBtn); bar.add(resetBtn)
        return bar
    }

    private fun buildEditPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.border = BorderFactory.createTitledBorder("Edit Gesture")
        val g = GridBagConstraints().apply { insets = Insets(3, 6, 3, 6); anchor = GridBagConstraints.WEST }

        g.gridx = 0; g.gridy = 0; g.weightx = 0.0; g.fill = GridBagConstraints.NONE
        panel.add(JBLabel("Name:"), g)
        nameField = JBTextField()
        g.gridx = 1; g.weightx = 1.0; g.fill = GridBagConstraints.HORIZONTAL
        panel.add(nameField, g)
        nameField.addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent?) = saveNameToGesture()
        })

        g.gridx = 0; g.gridy = 1; g.weightx = 0.0; g.fill = GridBagConstraints.NONE
        panel.add(JBLabel("Pattern:"), g)
        val patternRow = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
        patternLabel = JLabel("—")
        patternLabel.font = patternLabel.font.deriveFont(Font.BOLD, 15f)
        patternLabel.preferredSize = Dimension(180, patternLabel.preferredSize.height)
        recordButton = JButton("⏺ Record Gesture")
        recordButton.addActionListener { toggleRecording() }
        patternRow.add(patternLabel); patternRow.add(recordButton)
        g.gridx = 1; g.weightx = 1.0; g.fill = GridBagConstraints.HORIZONTAL
        panel.add(patternRow, g)

        duplicateWarningLabel = JLabel(" ")
        duplicateWarningLabel.foreground = JBColor.RED
        duplicateWarningLabel.font = duplicateWarningLabel.font.deriveFont(11f)
        g.gridx = 1; g.gridy = 2
        panel.add(duplicateWarningLabel, g)

        g.gridx = 0; g.gridy = 3; g.gridwidth = 2; g.fill = GridBagConstraints.HORIZONTAL
        panel.add(JSeparator(), g); g.gridwidth = 1

        g.gridx = 0; g.gridy = 4; g.weightx = 0.0; g.fill = GridBagConstraints.NONE
        panel.add(JBLabel("Search action:"), g)
        actionSearchField = JBTextField()
        actionSearchField.toolTipText = "Type action name or ID (e.g. comment, reformat, build...)"
        g.gridx = 1; g.weightx = 1.0; g.fill = GridBagConstraints.HORIZONTAL
        panel.add(actionSearchField, g)
        actionSearchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = filterActions()
            override fun removeUpdate(e: DocumentEvent?) = filterActions()
            override fun changedUpdate(e: DocumentEvent?) {}
        })

        actionListModel = DefaultListModel()
        actionList = JList(actionListModel)
        actionList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        actionList.cellRenderer = ActionListRenderer()
        actionList.addListSelectionListener {
            if (!it.valueIsAdjusting && !suppressListeners) applySelectedAction()
        }
        val listScroll = JBScrollPane(actionList)
        listScroll.preferredSize = Dimension(0, 160)
        g.gridx = 0; g.gridy = 5; g.gridwidth = 2
        g.fill = GridBagConstraints.BOTH; g.weighty = 1.0
        panel.add(listScroll, g)
        g.gridwidth = 1; g.weighty = 0.0

        g.gridx = 0; g.gridy = 6; g.fill = GridBagConstraints.NONE; g.weightx = 0.0
        panel.add(JBLabel("Action ID:"), g)
        actionIdField = JBTextField()
        actionIdField.toolTipText = "Action ID – filled automatically when selecting from the list"
        g.gridx = 1; g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1.0
        panel.add(actionIdField, g)
        actionIdField.addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent?) = saveActionIdToGesture()
        })

        g.gridx = 0; g.gridy = 7; g.fill = GridBagConstraints.NONE; g.weightx = 0.0
        panel.add(JBLabel("Enabled:"), g)
        enabledCheckBox = JBCheckBox()
        enabledCheckBox.addActionListener { saveEnabledToGesture() }
        g.gridx = 1; panel.add(enabledCheckBox, g)

        setEditPanelEnabled(false)
        populateActionList(allActions)
        return panel
    }

    private fun buildVisualizationPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.border = BorderFactory.createTitledBorder("Gesture Visualization")
        val g = GridBagConstraints().apply { insets = Insets(3, 6, 3, 6); anchor = GridBagConstraints.WEST }
        val vis = managerService.visualizationSettings

        showTrailCheck = JBCheckBox("Show gesture trail", vis.showTrail)
        showDirectionsCheck = JBCheckBox("Show direction arrows", vis.showDirections)
        trailColorPicker = ColorPickerField(vis.trailColor)
        matchColorPicker = ColorPickerField(vis.matchColor)
        trailThicknessSpinner = JSpinner(SpinnerNumberModel(vis.trailThickness.toDouble(), 1.0, 10.0, 0.5))

        g.gridx = 0; g.gridy = 0; g.gridwidth = 2; panel.add(showTrailCheck, g)
        g.gridy = 1; panel.add(showDirectionsCheck, g)

        g.gridy = 2; g.gridwidth = 1; g.fill = GridBagConstraints.NONE; g.weightx = 0.0
        panel.add(JBLabel("Trail color:"), g)
        g.gridx = 1; g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1.0
        panel.add(trailColorPicker, g)

        g.gridx = 0; g.gridy = 3; g.fill = GridBagConstraints.NONE; g.weightx = 0.0
        panel.add(JBLabel("Match color:"), g)
        g.gridx = 1; g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1.0
        panel.add(matchColorPicker, g)

        g.gridx = 0; g.gridy = 5; g.fill = GridBagConstraints.NONE; g.weightx = 0.0
        panel.add(JBLabel("Trail thickness:"), g)
        g.gridx = 1; g.fill = GridBagConstraints.HORIZONTAL
        panel.add(trailThicknessSpinner, g)

        return panel
    }

    private fun filterActions() {
        val q = actionSearchField.text.trim().lowercase()
        populateActionList(
            if (q.isEmpty()) allActions
            else allActions.filter { it.displayName.lowercase().contains(q) || it.actionId.lowercase().contains(q) }
        )
    }

    private fun populateActionList(items: List<ActionItem>) {
        suppressListeners = true
        actionListModel.clear()
        items.forEach { actionListModel.addElement(it) }
        suppressListeners = false
    }

    private fun applySelectedAction() {
        val item = actionList.selectedValue ?: return
        actionIdField.text = item.actionId
        saveActionIdToGesture(item.actionId, item.displayName)
    }

    private fun saveNameToGesture() {
        val gest = selectedGesture() ?: return
        gest.name = nameField.text.trim()
        refreshTable()
    }

    private fun saveActionIdToGesture(
        id: String = actionIdField.text.trim(),
        name: String = allActions.firstOrNull { it.actionId == id }?.displayName ?: id
    ) {
        val gest = selectedGesture() ?: return
        gest.actionId = id; gest.actionName = name
        refreshTable()
    }

    private fun saveEnabledToGesture() {
        val gest = selectedGesture() ?: return
        gest.isEnabled = enabledCheckBox.isSelected
        refreshTable()
    }

    /**
     * Prekreslí tabuľku bez spustenia loadGestureIntoEditPanel().
     * suppressTableListener zabraňuje aby setSelectionInterval() spustilo
     * loadGestureIntoEditPanel() a tým prerušilo kliknutie na nové akcie v zozname.
     */
    private fun refreshTable() {
        val row = table.selectedRow
        suppressTableListener = true
        tableModel.fireTableDataChanged()
        if (row >= 0) table.selectionModel.setSelectionInterval(row, row)
        suppressTableListener = false
    }

    private fun loadGestureIntoEditPanel() {
        val gesture = selectedGesture() ?: run { setEditPanelEnabled(false); return }
        setEditPanelEnabled(true)
        suppressListeners = true
        nameField.text = gesture.name
        patternLabel.text = gesture.patternDescription.ifEmpty { "—" }
        actionIdField.text = gesture.actionId
        enabledCheckBox.isSelected = gesture.isEnabled
        duplicateWarningLabel.text = " "
        for (i in 0 until actionListModel.size) {
            if (actionListModel.getElementAt(i).actionId == gesture.actionId) {
                actionList.selectedIndex = i
                actionList.ensureIndexIsVisible(i)
                break
            }
        }
        suppressListeners = false
    }

    private fun addGesture() {
        val g = MouseGesture(id = UUID.randomUUID().toString(), name = "New Gesture")
        workingGestures.add(g)
        tableModel.fireTableDataChanged()
        val row = workingGestures.size - 1
        table.selectionModel.setSelectionInterval(row, row)
        loadGestureIntoEditPanel()
        nameField.requestFocus(); nameField.selectAll()
    }

    private fun deleteGesture() {
        val row = table.selectedRow.takeIf { it >= 0 } ?: return
        workingGestures.removeAt(row)
        tableModel.fireTableDataChanged()
        setEditPanelEnabled(false)
    }

    private fun resetToDefaults() {
        if (Messages.showYesNoDialog(
                null, "This will delete all custom gestures and restore the defaults. Continue?",
                "Reset to Defaults", Messages.getWarningIcon()
            ) == Messages.YES
        ) {
            managerService.resetToDefaults()
            workingGestures.clear()
            workingGestures.addAll(managerService.getGestures().map { it.copy() })
            tableModel.fireTableDataChanged()
            setEditPanelEnabled(false)
            // Obnov aj vizualizačné UI komponenty na predvolené hodnoty
            val vis = managerService.visualizationSettings
            showTrailCheck.isSelected = vis.showTrail
            showDirectionsCheck.isSelected = vis.showDirections
            trailColorPicker.hex = vis.trailColor
            matchColorPicker.hex = vis.matchColor
            trailThicknessSpinner.value = vis.trailThickness.toDouble()
        }
    }

    private fun toggleRecording() {
        if (isRecording) cancelRecording() else startRecording()
    }

    private fun startRecording() {
        setParentWindowOpacity(false) // dialóg sa stane priehľadným, vidno IDE a overlay pod ním
        isRecording = true
        recordButton.text = "⏹ Cancel"
        patternLabel.text = "Draw gesture..."
        duplicateWarningLabel.text = " "
        orchestratorService.startGestureRecording { pattern ->
            SwingUtilities.invokeLater {
                setParentWindowOpacity(true) // Zobrazí okno

                // Focus späť na okno nastavení
                val window = SwingUtilities.getWindowAncestor(recordButton)
                window?.toFront()
                window?.requestFocus()
                onGestureRecorded(pattern)
            }
        }
    }

    private fun cancelRecording() {
        setParentWindowOpacity(true)
        isRecording = false
        recordButton.text = "⏺ Record Gesture"
        orchestratorService.stopGestureRecording()
        loadGestureIntoEditPanel()
    }

    private fun onGestureRecorded(pattern: List<GestureDirection>) {
        setParentWindowOpacity(true)
        isRecording = false
        recordButton.text = "⏺ Record Gesture"
        val gesture = selectedGesture() ?: return
        if (pattern.isEmpty()) {
            loadGestureIntoEditPanel(); return
        }
        val duplicate = workingGestures.firstOrNull { it.id != gesture.id && it.matchesPattern(pattern) }
        gesture.pattern = pattern
        patternLabel.text = gesture.patternDescription
        refreshTable()
        duplicateWarningLabel.text = if (duplicate != null) "⚠ Pattern already used by '${duplicate.name}'" else " "
    }

    private fun selectedGesture(): MouseGesture? {
        val row = table.selectedRow
        return if (row in workingGestures.indices) workingGestures[row] else null
    }

    private fun setEditPanelEnabled(enabled: Boolean) {
        listOf<JComponent>(nameField, recordButton, actionSearchField, actionList, actionIdField, enabledCheckBox)
            .forEach { it.isEnabled = enabled }
        if (!enabled) {
            nameField.text = ""; patternLabel.text = "—"
            actionSearchField.text = ""; actionIdField.text = ""
            enabledCheckBox.isSelected = false; duplicateWarningLabel.text = " "
            actionList.clearSelection()
        }
    }

    /** Nastaví priehľadnosť okna settings – pri nahrávaní 15% opacity aby bola vidná stopa */
    private fun setParentWindowOpacity(visible: Boolean) {
        // Namiesto opacity budeme ovládať viditeľnosť celého okna
        val window = SwingUtilities.getWindowAncestor(recordButton) ?: return
        window.isVisible = visible
    }

    override fun isModified(): Boolean {
        val orig = managerService.getGestures()
        if (orig.size != workingGestures.size) return true
        orig.forEachIndexed { i, o ->
            val w = workingGestures.getOrNull(i) ?: return true
            if (o.name != w.name || o.pattern != w.pattern || o.actionId != w.actionId || o.isEnabled != w.isEnabled) return true
        }
        val vis = managerService.visualizationSettings
        return vis.showTrail != showTrailCheck.isSelected ||
                vis.showDirections != showDirectionsCheck.isSelected ||
                vis.trailColor != trailColorPicker.hex ||
                vis.matchColor != matchColorPicker.hex ||
                vis.trailThickness != (trailThicknessSpinner.value as Double).toFloat()
    }

    override fun apply() {
        val savedIds = managerService.getGestures().map { it.id }.toSet()
        workingGestures.filter { it.id !in savedIds }.forEach { managerService.addGesture(it) }
        savedIds.filter { id -> workingGestures.none { it.id == id } }.forEach { managerService.removeGesture(it) }
        workingGestures.forEach { managerService.updateGesture(it) }
        val vis = managerService.visualizationSettings
        vis.showTrail = showTrailCheck.isSelected
        vis.showDirections = showDirectionsCheck.isSelected
        vis.trailColor = trailColorPicker.hex
        vis.matchColor = matchColorPicker.hex
        vis.trailThickness = (trailThicknessSpinner.value as Double).toFloat()
    }

    override fun reset() {
        workingGestures.clear()
        workingGestures.addAll(managerService.getGestures().map { it.copy() })
        tableModel.fireTableDataChanged()
        val vis = managerService.visualizationSettings
        showTrailCheck.isSelected = vis.showTrail
        showDirectionsCheck.isSelected = vis.showDirections
        trailColorPicker.hex = vis.trailColor
        matchColorPicker.hex = vis.matchColor
        trailThicknessSpinner.value = vis.trailThickness.toDouble()
        setEditPanelEnabled(false)
    }

    override fun disposeUIResources() {
        if (isRecording) cancelRecording()
        orchestratorService.setSettingsOpen(false)
    }

    // ── Inner classes ─────────────────────────────────────────────────────

    inner class GestureTableModel(private val data: List<MouseGesture>) : AbstractTableModel() {
        private val cols = arrayOf("Name", "Pattern", "Action", "✓")
        override fun getRowCount() = data.size
        override fun getColumnCount() = cols.size
        override fun getColumnName(col: Int) = cols[col]
        override fun getColumnClass(col: Int) = if (col == 3) Boolean::class.javaObjectType else String::class.java
        override fun isCellEditable(row: Int, col: Int) = false
        override fun getValueAt(row: Int, col: Int): Any {
            val g = data[row]
            return when (col) {
                0 -> g.name; 1 -> g.patternDescription; 2 -> g.actionName.ifEmpty { g.actionId }; 3 -> g.isEnabled; else -> ""
            }
        }
    }

    inner class ActionListRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            val lbl = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
            val item = value as? ActionItem
            if (item != null) lbl.text =
                "<html><b>${item.displayName}</b>&nbsp;<font color='gray'><i>${item.actionId}</i></font></html>"
            return lbl
        }
    }

    data class ActionItem(val actionId: String, val displayName: String) {
        override fun toString() = displayName
    }

    /**
     * Farebné tlačidlo = jeden klik zobrazí výber farieb.
     * Zobrazuje len Swatches tab – žiadne HSL/RGB/CMYK panely.
     */
    inner class ColorPickerField(initialHex: String) : JButton() {
        var hex: String = initialHex
            set(value) {
                field = value; refreshDisplay()
            }

        init {
            preferredSize = Dimension(50, 22)
            isFocusPainted = false
            toolTipText = "Click to choose color"
            refreshDisplay()
            addActionListener { pickColor() }
        }

        private fun refreshDisplay() {
            background = runCatching { java.awt.Color.decode(hex) }.getOrDefault(java.awt.Color.GRAY)
            text = ""
        }

        private fun pickColor() {
            val current = runCatching { java.awt.Color.decode(hex) }.getOrDefault(java.awt.Color.WHITE)
            val chooser = JColorChooser(current)
            // Zobraz len Swatches tab – odstráni HSL, RGB, CMYK, HSV panely
            val swatches = chooser.chooserPanels.firstOrNull {
                it.displayName.lowercase().let { n -> n.contains("swatch") || n.contains("palet") }
            }
            if (swatches != null) chooser.chooserPanels = arrayOf(swatches)
            chooser.previewPanel = JPanel()  // skryje preview lištu

            JColorChooser.createDialog(
                this, "Choose Color", true, chooser,
                { hex = "#%02X%02X%02X".format(chooser.color.red, chooser.color.green, chooser.color.blue) },
                null
            ).isVisible = true
        }
    }
}
