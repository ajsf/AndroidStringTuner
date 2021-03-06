package tech.ajsf.instrutune.features.customtuning

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.Observer
import kotlinx.android.synthetic.main.activity_custom_tuning.*
import kotlinx.android.synthetic.main.custom_tuning_builder.*
import kotlinx.android.synthetic.main.custom_tuning_starter.*
import org.kodein.di.Kodein
import tech.ajsf.instrutune.R
import tech.ajsf.instrutune.common.model.InstrumentCategory
import tech.ajsf.instrutune.common.view.InjectedActivity
import tech.ajsf.instrutune.common.viewmodel.buildViewModel
import tech.ajsf.instrutune.features.customtuning.di.customTuningActivityModule
import tech.ajsf.instrutune.features.customtuning.view.ConfirmDeleteDialog
import tech.ajsf.instrutune.features.customtuning.view.CustomOnboarding
import tech.ajsf.instrutune.features.customtuning.view.SelectNoteDialog
import tech.ajsf.instrutune.features.customtuning.view.TextUpdateWatcher

const val CUSTOM_TUNING_EXTRA = "CUSTOM_TUNING_NAME"

class CustomTuningActivity : InjectedActivity() {

    override fun activityModule() = Kodein.Module("custom") {
        import(customTuningActivityModule())
    }

    private val viewModel: CustomTuningViewModel by buildViewModel()

    var onboarding: CustomOnboarding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_tuning)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        initUi()
    }

    override fun onDestroy() {
        super.onDestroy()
        onboarding?.clear()
        viewModel.onActivityDestroyed()
    }

    private fun initUi() {
        initViewModel()
        initStarter()
        initStringsView()
        val textWatcher = TextUpdateWatcher { viewModel.updateTitle(it) }
        tuning_name_edit_text.addTextChangedListener(textWatcher)
        add_string_fab.setOnClickListener { showSelectNoteDialog() }
        btn_save.setOnClickListener { saveAndFinish() }
        btn_delete.setOnClickListener { showConfirmDeleteTuningDialog() }
    }

    private fun initViewModel() {
        viewModel.viewStateLiveData.observe(this, Observer { viewState ->
            strings_view.setStrings(viewState.notes)
            btn_delete.isEnabled = viewState.id != null
            btn_save.isEnabled = viewState.tuningName.isNotBlank() && viewState.notes.isNotEmpty()
            if (viewState.notes.isNotEmpty()) {
                onboarding?.advance()
            }
            if (viewState.requestOnboarding) requestOnboarding()
        })

        viewModel.buildingLiveData.observe(this, Observer {
            if (it.building) {
                tuning_builder.visibility = View.VISIBLE
                tuning_starter.visibility = View.GONE
                tuning_name_edit_text.setText(it.tuningName)
            } else {
                tuning_builder.visibility = View.GONE
                tuning_starter.visibility = View.VISIBLE
            }
        })

        if (viewModel.enableEdit().not()) btn_edit.isEnabled = false
    }

    private fun requestOnboarding() {
        onboarding = CustomOnboarding(this)
        onboarding?.requestOnboarding()
        viewModel.onboardingChecked()
    }

    private fun initStarter() {
        btn_blank.setOnClickListener { viewModel.startBuilder() }
        btn_template.setOnClickListener { viewModel.showSelectCategory() }
        btn_edit.setOnClickListener { viewModel.showSelectTuning(InstrumentCategory.Custom.toString()) }
    }

    private fun initStringsView() = with(strings_view) {
        stringClickListener = { showConfirmDeleteNoteDialog(it) }
        noteClickListener = { index, name -> showSelectNoteDialog(index, name) }
        moveStringCallback = { oldIndex, newIndex -> viewModel.moveNote(oldIndex, newIndex) }
    }

    private fun saveAndFinish() {
        viewModel.saveTuningAndExecuteAction {
            val data = Intent()
            data.putExtra(CUSTOM_TUNING_EXTRA, it)
            setResult(Activity.RESULT_OK, data)
            finish()
        }
    }

    private fun deleteAndFinish() {
        viewModel.deleteTuning()
        val data = Intent()
        data.putExtra(CUSTOM_TUNING_EXTRA, -1)
        setResult(Activity.RESULT_OK, data)
        finish()
    }

    private fun showConfirmDeleteTuningDialog() {
        ConfirmDeleteDialog(this) { deleteAndFinish() }.show("Do you want to delete this tuning?")
    }

    private fun showConfirmDeleteNoteDialog(noteIndex: Int): Boolean =
        ConfirmDeleteDialog(this) { viewModel.removeNote(noteIndex) }
            .show("Do you want to delete this string?")
            .run { true }

    private fun showSelectNoteDialog(noteIndex: Int = -1, numberedName: String = "C4") {
        val notePicker =
            SelectNoteDialog(this) { noteName -> onNoteSelected(noteIndex, noteName) }
        notePicker.show(numberedName, viewModel.getNoteRange())
    }

    private fun onNoteSelected(noteIndex: Int, noteName: String) {
        if (noteIndex < 0) createNewString(noteName)
        else updateString(noteIndex, noteName)
    }

    private fun createNewString(noteName: String) {
        viewModel.addNote(noteName)
    }

    private fun updateString(noteIndex: Int, noteName: String) {
        viewModel.updateNote(noteIndex, noteName)
    }
}