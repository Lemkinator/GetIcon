package de.lemke.geticon.ui

import android.R.anim.fade_in
import android.R.anim.fade_out
import android.content.Intent
import android.graphics.Color.TRANSPARENT
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout.LayoutParams
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.data.commonUtilsSettings
import de.lemke.geticon.R
import de.lemke.geticon.databinding.ActivityOobeBinding
import de.lemke.geticon.domain.UpdateUserSettingsUseCase
import dev.oneuiproject.oneui.widget.OnboardingTipsItemView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import de.lemke.commonutils.R as commonutilsR
import dev.oneuiproject.oneui.R as oneuiR

@AndroidEntryPoint
class OOBEActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOobeBinding

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (SDK_INT >= 34) overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, fade_in, fade_out)
        binding = ActivityOobeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initTipsItems()
        initToSView()
        initFooterButton()
    }

    private fun initTipsItems() {
        val tipsData = listOf(
            Triple(R.string.oobe_onboard_msg1_title, R.string.oobe_onboard_msg1_summary, oneuiR.drawable.ic_oui_palette),
            Triple(R.string.oobe_onboard_msg2_title, R.string.oobe_onboard_msg2_summary, oneuiR.drawable.ic_oui_credit_card_outline),
            Triple(R.string.oobe_onboard_msg3_title, R.string.oobe_onboard_msg3_summary, oneuiR.drawable.ic_oui_decline)
        )
        tipsData.forEach { (titleRes, summaryRes, iconRes) ->
            OnboardingTipsItemView(this).apply {
                setIcon(iconRes)
                title = getString(titleRes)
                summary = getString(summaryRes)
                binding.oobeIntroTipsContainer.addView(this, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            }
        }
    }

    private fun initToSView() {
        val tos = getString(commonutilsR.string.commonutils_tos)
        val tosText = getString(commonutilsR.string.commonutils_oobe_tos_text, tos)
        val tosIndex = tosText.indexOf(tos)
        binding.oobeIntroFooterTosText.text = SpannableString(tosText).apply {
            setSpan(
                object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        AlertDialog.Builder(this@OOBEActivity)
                            .setTitle(getString(commonutilsR.string.commonutils_tos))
                            .setMessage(getString(commonutilsR.string.commonutils_tos_content))
                            .setPositiveButton(commonutilsR.string.commonutils_ok, null)
                            .show()
                    }
                },
                tosIndex, tosIndex + tos.length,
                SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        binding.oobeIntroFooterTosText.movementMethod = LinkMovementMethod.getInstance()
        binding.oobeIntroFooterTosText.highlightColor = TRANSPARENT
    }

    private fun initFooterButton() {
        if (resources.configuration.screenWidthDp < 360) {
            binding.oobeIntroFooterButton.layoutParams.width = MATCH_PARENT
        }
        binding.oobeIntroFooterButton.setOnClickListener {
            binding.oobeIntroFooterTosText.isEnabled = false
            binding.oobeIntroFooterButton.isVisible = false
            binding.oobeIntroFooterButtonProgress.isVisible = true
            lifecycleScope.launch {
                commonUtilsSettings.tosAccepted = true
                delay(500)
                openNextActivity()
            }
        }
    }

    private fun openNextActivity() {
        startActivity(Intent(this@OOBEActivity, MainActivity::class.java))
        @Suppress("DEPRECATION") if (SDK_INT < 34) overridePendingTransition(fade_in, fade_out)
        finishAfterTransition()
    }
}