package com.photostudio.pro.analytics

/**
 * Every analytics event name in ONE file, so nothing is misspelled and the
 * weekly abuse check can rely on a stable name.
 *
 * ⚠️ PRIVACY (SPEC.md section 11): NO event may carry image data, a file path
 * outside the app sandbox, EXIF metadata, or anything that identifies a person.
 * These are counts, timings and enum-like labels only. Keeping this discipline
 * is what lets the Data Safety form say "no personal data collected" apart from
 * the AdMob advertising identifier and RevenueCat's anonymous purchase records.
 */
object Events {

    private val log: (String, Map<String, Any?>) -> Unit = { name, params ->
        // Wired to Firebase Analytics in PhotoStudioApp. Kept as an indirection
        // so unit tests never need a Firebase dependency.
        sink?.invoke(name, params)
    }

    /** Set once at app start. Null in unit tests -> events are no-ops. */
    var sink: ((String, Map<String, Any?>) -> Unit)? = null

    // ---- Ad decisions (the debug trail for revenue problems) ----
    fun logAdDecision(allowed: Boolean, reason: String) =
        log("ad_decision", mapOf("allowed" to allowed, "reason" to reason))

    fun logAdBlocked(reason: String) = log("ad_blocked", mapOf("reason" to reason))

    fun logAdShown(format: String, placement: String) =
        log("ad_shown", mapOf("format" to format, "placement" to placement))

    fun logAdFailed(format: String, code: Int) =
        log("ad_failed", mapOf("format" to format, "code" to code))

    // ---- ⭐ THE ABUSE DETECTOR ----
    /**
     * Logged on every reward GRANT. Weekly, compare this event's count against
     * AdMob dashboard "Rewarded impressions":
     *     ratio ~= 1.0  healthy
     *     ratio >  1.5  investigate
     *     ratio >  3.0  build Phase 2 SSV (SPEC.md section 14)
     * Exclude Play Console licence testers, and remember test-mode impressions
     * never appear in the AdMob dashboard, so this is production-only.
     */
    fun logRewardGranted(kind: String) = log("reward_granted", mapOf("kind" to kind))

    fun logRewardDenied(reason: String) = log("reward_denied", mapOf("reason" to reason))

    // ---- Product ----
    fun logToolOpened(toolId: String) = log("tool_opened", mapOf("tool" to toolId))

    fun logExportCompleted(toolId: String, widthPx: Int, heightPx: Int, bytesOut: Int, isPro: Boolean) =
        log("export_completed", mapOf(
            "tool" to toolId, "w" to widthPx, "h" to heightPx,
            "bytes" to bytesOut, "pro" to isPro,
        ))

    fun logPaywallShown(trigger: String, variant: String) =
        log("paywall_shown", mapOf("trigger" to trigger, "variant" to variant))

    fun logPaywallDismissed(trigger: String) = log("paywall_dismissed", mapOf("trigger" to trigger))

    fun logTrialStarted(productId: String) = log("trial_started", mapOf("product" to productId))

    fun logPurchaseIntentDetected() = log("purchase_intent", emptyMap())

    // ---- Health of the no-backend decision ----
    /**
     * Fires when the Subject Segmentation model is not yet on the device.
     * Watch this closely in week 1: a high rate means the install-time
     * auto-download declaration is not working and the main hook is broken.
     */
    fun logSegmentationModelNotReady(state: String) =
        log("seg_model_not_ready", mapOf("state" to state))

    fun logMlKitError(api: String, code: String) =
        log("mlkit_error", mapOf("api" to api, "code" to code))

    fun logOutOfMemory(toolId: String) = log("oom", mapOf("tool" to toolId))

    /**
     * OCR result. `chars` is the length of the recognised text, never the text
     * itself — a document's contents must not end up in an analytics backend.
     * That would contradict the Data Safety form and the privacy policy, both of
     * which state that no image content is collected.
     */
    fun logOcrCompleted(script: String, chars: Int) =
        log("ocr_completed", mapOf("script" to script, "chars" to chars))

    /** Collage template chosen — tells you which layouts to build more of. */
    fun logTemplateUsed(templateId: String, isProTemplate: Boolean) =
        log("template_used", mapOf("template" to templateId, "pro" to isProTemplate))

    /** PDF direction, because images->pdf and pdf->images are different products. */
    fun logPdfConverted(direction: String, pages: Int, bytesOut: Int) =
        log("pdf_converted", mapOf("direction" to direction, "pages" to pages, "bytes" to bytesOut))
}
