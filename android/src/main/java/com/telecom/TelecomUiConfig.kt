package com.telecom

/**
 * Appearance / behavior knobs for the incoming full-screen host Activity.
 * Apps can change these at runtime via [TelecomModule.setup] / JS `setup({ ui: ... })`.
 * Layout resource `R.layout.rn_telecom_incoming_call` can also be overridden by the host app.
 */
data class TelecomUiConfig(
  val incomingTitle: String = "Incoming call",
  val answerLabel: String = "Answer",
  val declineLabel: String = "Decline",
  val messageLabel: String = "Message",
  /** Light Material You cream — matches stock Android incoming UI. */
  val backgroundColor: Int = 0xFFF7F4EF.toInt(),
  val titleColor: Int = 0xFF79747E.toInt(),
  val nameColor: Int = 0xFF1C1B1F.toInt(),
  val handleColor: Int = 0xFF49454F.toInt(),
  val answerColor: Int = 0xFF1B5E20.toInt(),
  val declineColor: Int = 0xFF1C1B1F.toInt(),
  /** When false, only the native CallStyle notification is shown (no Activity). */
  val launchFullScreenOnIncoming: Boolean = true,
) {
  companion object {
    @Volatile
    var current: TelecomUiConfig = TelecomUiConfig()
  }
}
