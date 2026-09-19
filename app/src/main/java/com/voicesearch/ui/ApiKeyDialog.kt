package com.voicesearch.ui

import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.voicesearch.R
import com.voicesearch.provider.TmdbException
import com.voicesearch.provider.TmdbSearchProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

object ApiKeyDialog {

    /**
     * Показывает ввод ключа. [onSaved] зовётся только после того, как TMDB
     * подтвердил ключ. Отменить диалог нельзя: без ключа экран поиска
     * бесполезен, Back закрывает Activity.
     */
    fun show(activity: AppCompatActivity, onSaved: (String) -> Unit) {
        val view = activity.layoutInflater.inflate(R.layout.dialog_api_key, null)
        val field = view.findViewById<EditText>(R.id.apiKeyField)
        val counter = view.findViewById<TextView>(R.id.apiKeyCounter)
        val status = view.findViewById<TextView>(R.id.apiKeyStatus)
        val grid = view.findViewById<GridLayout>(R.id.apiKeyGrid)
        val backspace = view.findViewById<Button>(R.id.apiKeyBackspace)
        val save = view.findViewById<Button>(R.id.apiKeySave)

        field.filters = arrayOf(HexKeyFilter.asInputFilter())
        // Глушит системную клавиатуру, поле остаётся EditText для adb input text.
        // XML-атрибута у этого свойства нет, только сеттер.
        field.showSoftInputOnFocus = false

        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.api_key_title)
            .setView(view)
            .setCancelable(false)
            .create()

        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                dialog.dismiss()
                activity.finish()
                true
            } else {
                false
            }
        }

        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val n = s?.length ?: 0
                counter.text = activity.getString(R.string.api_key_counter, n)
                save.isEnabled = n == HexKeyFilter.MAX_LENGTH
                status.visibility = View.GONE
            }
        })

        val margin = (4 * activity.resources.displayMetrics.density).toInt()
        "0123456789abcdef".forEach { c ->
            grid.addView(Button(activity).apply {
                text = c.toString()
                isAllCaps = false // тема по умолчанию пишет A–F, а в поле уходят a–f
                setOnClickListener { field.append(c.toString()) }
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(margin, margin, margin, margin)
                }
            })
        }

        backspace.setOnClickListener {
            val text = field.text
            if (text.isNotEmpty()) text.delete(text.length - 1, text.length)
        }

        save.setOnClickListener {
            val key = field.text.toString()
            save.isEnabled = false
            status.setText(R.string.api_key_checking)
            status.visibility = View.VISIBLE
            activity.lifecycleScope.launch {
                try {
                    TmdbSearchProvider(key).validate()
                    dialog.dismiss()
                    onSaved(key)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: TmdbException.ApiKeyInvalid) {
                    status.setText(R.string.api_key_error_rejected)
                    save.isEnabled = true
                } catch (e: Exception) {
                    status.setText(R.string.api_key_error_network)
                    save.isEnabled = true
                }
            }
        }

        // На большом экране система сама поднимает клавиатуру под EditText в окне
        // без явного режима — showSoftInputOnFocus этот путь не перекрывает.
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        dialog.show()
        field.requestFocus()
    }
}
