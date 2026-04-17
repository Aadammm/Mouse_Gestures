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
import java.awt.geom.RoundRectangle2D
import java.util.*
import com.intellij.ui.components.JBTabbedPane
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
    private var recordingGestureId: String? = null
    private var recordingRowIndex: Int = -1
    private var savedWindowBounds: java.awt.Rectangle? = null

    private var isRevertMode = false
    private var revertPattern: List<GestureDirection>? = null
    private var revertGestureId: String? = null

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

        val tabs =  JBTabbedPane()
        tabs.addTab("Gestures", buildGesturesTab())
        tabs.addTab("Help / About", buildHelpPanel())

        panel.add(tabs, BorderLayout.CENTER)
        return panel
    }

    private fun buildGesturesTab(): JComponent {
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
        return split
    }

    private fun buildHelpPanel(): JComponent {
        val panel = JPanel(BorderLayout(8, 8))
        panel.border = JBUI.Borders.empty(16)

        val text = """
            <html>
            <h2>Mouse Gestures Plugin</h2>
            <p><b>Version:</b> 1.0.0</p>
            <p><b>Author:</b> TODO – fill in manually</p>
            <br>
            <h3>How to use</h3>
            <ul>
              <li>Hold the <b>right mouse button</b> and drag to draw a gesture.</li>
              <li>Release to execute the assigned action.</li>
              <li>A short right-click (no drag) still shows the context menu.</li>
            </ul>
            <br>
            <h3>Default gestures</h3>
            <table border="1" cellpadding="4" cellspacing="0">
              <tr><th>Gesture</th><th>Action</th></tr>
              <tr><td>← Left</td><td>Navigate Backward</td></tr>
              <tr><td>→ Right</td><td>Navigate Forward</td></tr>
              <tr><td>↓ Down</td><td>Comment/Uncomment Line</td></tr>
              <tr><td>↓ Down → Right</td><td>Close Tab</td></tr>
            </table>
            <br>
            <h3>Adding / editing gestures</h3>
            <ol>
              <li>Click <b>+ Add</b> or select an existing gesture in the list.</li>
              <li>Enter a name and click <b>⏺ Record Gesture</b>.</li>
              <li>Draw the gesture with the right mouse button – the settings window hides temporarily.</li>
              <li>Select an action from the list or type an action ID directly.</li>
              <li>Click <b>OK</b> to save.</li>
            </ol>
            <br>
            <p><i>TODO: add your own notes here.</i></p>
            </html>
        """.trimIndent()

        val label = JBLabel(text)
        label.verticalAlignment = SwingConstants.TOP
        panel.add(JBScrollPane(label), BorderLayout.CENTER)
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

        g.gridx = 0; g.gridy = 0; g.gridwidth = 5; panel.add(showTrailCheck, g)
        g.gridy = 1; panel.add(showDirectionsCheck, g)
        g.gridwidth = 1

        val defaultInsets = Insets(3, 6, 3, 6)
        val gapInsets    = Insets(3, 14, 3, 6)
        g.gridy = 2; g.gridx = 0; g.fill = GridBagConstraints.NONE; g.weightx = 0.0
        g.insets = defaultInsets; panel.add(JBLabel("Trail:"), g)
        g.gridx = 1; panel.add(trailColorPicker, g)
        g.gridx = 2; g.insets = gapInsets; panel.add(JBLabel("Match:"), g)
        g.gridx = 3; g.insets = defaultInsets; panel.add(matchColorPicker, g)
        // prázdny filler aby ostatné stĺpce neboli roztiahnuté
        g.gridx = 4; g.weightx = 1.0; g.fill = GridBagConstraints.HORIZONTAL
        panel.add(JPanel().also { it.isOpaque = false }, g)

        g.gridx = 0; g.gridy = 3; g.fill = GridBagConstraints.NONE; g.weightx = 0.0
        g.insets = defaultInsets; panel.add(JBLabel("Trail thickness:"), g)
        g.gridx = 1; g.fill = GridBagConstraints.HORIZONTAL; g.gridwidth = 3
        panel.add(trailThicknessSpinner, g)
        g.gridwidth = 1

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
        if (isRevertMode && gesture.id != revertGestureId) {
            isRevertMode = false; revertPattern = null; revertGestureId = null
            recordButton.text = "⏺ Record Gesture"
        }
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
        when {
            isRecording   -> cancelRecording()
            isRevertMode  -> revertRecording()
            else          -> startRecording()
        }
    }

    private fun startRecording() {
        val gesture = selectedGesture() ?: return
        // Ulož aktuálny vzor pre prípad revertu
        isRevertMode = false
        revertPattern = gesture.pattern.toList()
        revertGestureId = gesture.id

        recordingGestureId = gesture.id
        recordingRowIndex = table.selectedRow

        setParentWindowOpacity(false)
        isRecording = true
        recordButton.text = "⏹ Cancel"
        patternLabel.text = "Draw gesture..."
        duplicateWarningLabel.text = " "

        orchestratorService.startGestureRecording { pattern ->
            SwingUtilities.invokeLater {
                setParentWindowOpacity(true)
                val window = SwingUtilities.getWindowAncestor(recordButton)
                window?.toFront()
                window?.requestFocus()
                SwingUtilities.invokeLater {
                    if (recordingRowIndex >= 0 && recordingRowIndex < workingGestures.size) {
                        suppressTableListener = true
                        table.selectionModel.setSelectionInterval(recordingRowIndex, recordingRowIndex)
                        suppressTableListener = false
                    }
                    onGestureRecorded(pattern)
                }
            }
        }
    }

    private fun cancelRecording() {
        setParentWindowOpacity(true)
        isRecording = false
        isRevertMode = false
        revertPattern = null
        revertGestureId = null
        recordingGestureId = null
        recordingRowIndex = -1
        recordButton.text = "⏺ Record Gesture"
        orchestratorService.stopGestureRecording()
        loadGestureIntoEditPanel()
    }

    /** Vráti gesto na vzor pred nahrávaním. */
    private fun revertRecording() {
        val gesture = workingGestures.firstOrNull { it.id == revertGestureId } ?: return
        gesture.pattern = revertPattern ?: emptyList()
        isRevertMode = false
        revertPattern = null
        revertGestureId = null
        recordButton.text = "⏺ Record Gesture"
        refreshTable()
        loadGestureIntoEditPanel()
        duplicateWarningLabel.text = " "
    }

    private fun onGestureRecorded(pattern: List<GestureDirection>) {
        isRecording = false
        val id = recordingGestureId
        recordingGestureId = null
        recordingRowIndex = -1
        val gesture = if (id != null) workingGestures.firstOrNull { it.id == id }
                      else selectedGesture()
        if (gesture == null || pattern.isEmpty()) {
            isRevertMode = false; revertPattern = null; revertGestureId = null
            recordButton.text = "⏺ Record Gesture"
            loadGestureIntoEditPanel(); return
        }
        val duplicate = workingGestures.firstOrNull { it.id != gesture.id && it.matchesPattern(pattern) }
        gesture.pattern = pattern
        isRevertMode = true
        recordButton.text = "↩ Revert"
        refreshTable()
        loadGestureIntoEditPanel()
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
            isRevertMode = false; revertPattern = null; revertGestureId = null
            if (!isRecording) recordButton.text = "⏺ Record Gesture"
        }
    }

    private fun setParentWindowOpacity(visible: Boolean) {
        val window = SwingUtilities.getWindowAncestor(recordButton) ?: return
        if (!visible) {
            savedWindowBounds = window.bounds
            window.isVisible = false
        } else {
            window.isVisible = true
            savedWindowBounds?.let { window.bounds = it }
            savedWindowBounds = null
            window.revalidate()
            window.repaint()
        }
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

    inner class ColorPickerField(initialHex: String) : JButton() {
        var hex: String = initialHex
            set(value) { field = value; repaint() }

        init {
            val sz = 22
            preferredSize = Dimension(sz, sz)
            minimumSize   = Dimension(sz, sz)
            maximumSize   = Dimension(sz, sz)
            isFocusPainted    = false
            isBorderPainted   = false
            isContentAreaFilled = false
            isOpaque          = false
            toolTipText = "Click to choose color"
            addActionListener { pickColor() }
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val color = runCatching { java.awt.Color.decode(hex) }.getOrDefault(java.awt.Color.GRAY)
            g2.color = color
            g2.fillRoundRect(2, 2, width - 5, height - 5, 5, 5)
            g2.color = color.darker()
            g2.stroke = BasicStroke(1.2f)
            g2.drawRoundRect(2, 2, width - 5, height - 5, 5, 5)
            if (model.isRollover) {
                g2.color = java.awt.Color(255, 255, 255, 50)
                g2.fillRoundRect(2, 2, width - 5, height - 5, 5, 5)
            }
        }

        private fun pickColor() {
            val current = runCatching { java.awt.Color.decode(hex) }.getOrDefault(java.awt.Color.WHITE)
            val chooser = JColorChooser(current)
            val swatches = chooser.chooserPanels.firstOrNull {
                it.displayName.lowercase().let { n -> n.contains("swatch") || n.contains("palet") }
            }
            if (swatches != null) chooser.chooserPanels = arrayOf(swatches)
            chooser.previewPanel = JPanel()
            JColorChooser.createDialog(
                this, "Choose Color", true, chooser,
                { hex = "#%02X%02X%02X".format(chooser.color.red, chooser.color.green, chooser.color.blue) },
                null
            ).isVisible = true
        }
    }
}
