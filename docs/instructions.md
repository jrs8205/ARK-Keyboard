# ARK-Keyboard – instructions

[← Back to the front page](../README.md) · [Suomeksi](ohjeet.md)

## Installation

1. Download `ark-nappaimisto-vX.Y.Z.apk` from the [Releases page](https://github.com/jrs8205/ARK-nappaimisto/releases/latest)
   on your phone and install it.
2. Open the ARK-Keyboard app. The first launch guides you through the setup.

Updates install over the old version, and learned words are kept. To move your data to another
device, use a backup (Settings → Backup). API keys are not included in backups.

The app follows your phone's language: Finnish if the phone is in Finnish, otherwise English.

## What to expect in English

- The keyboard uses a Finnish QWERTY layout with å, ä and ö.
- The vocabulary is mainly Finnish, with about 57,000 English words added. English words appear
  after Finnish ones until you have used them; the keyboard then learns them.
- Auto-correct is Finnish, so it is off by default unless the phone is in Finnish. It never turns
  a Finnish typo into an English word that you have not used yourself.
- Device dictation uses your phone's language.

## Tips

- **Hide the number row:** numbers and symbols are then available with a long press on the letters.
- **Long press a suggestion:** pin, remove or block the word.
- **Wrong correction:** backspace right after the correction undoes it.
- **Cursor:** slide your finger on the space bar.
- **Delete a word at a time:** hold backspace or slide left from it; slide back if you change your
  mind.
- **Full stop:** two quick spaces. Backspace right after restores the spaces.
- **Lowercase at the start of a sentence:** tap shift once. A quick double tap locks capitals.
- **Switch keyboard:** hold the space bar.
- **Recent copy:** copied text stays on the suggestion row for a minute, and one tap pastes it.
- **Emoji skin tone:** hold an emoji; your choice is remembered.
- **Translation:** Insert puts the text in the field and closes the translator, so you
  can send the message right away. If you reopen the translator before sending, you can continue
  the same text, and a new insert replaces the translation in the field. After sending, the
  translator opens empty. The Back button closes the translator first.
- **Undo button:** you can add it to the toolbar in the settings.
- **One-handed mode:** use the toolbar button. The side panel moves the keyboard to the other side
  or back to full width.

On the lock screen right after the phone starts, the keyboard works without settings or learning,
because they become available only after the first unlock.

## AI translation and dictation

The keyboard works fully without accounts or keys. Two optional features use an outside service
with your own ChatGPT subscription or API key.

| Feature | Requires | Price |
|---|---|---|
| Live translation | – | free |
| ✨ AI translation | ChatGPT Plus/Pro subscription OR a Claude/OpenAI API key | from the subscription's quota or a fraction of a cent |
| Dictation, device recognition | – | free |
| Dictation, OpenAI | OpenAI API key | priced per minute |

### An API key is not the same as a subscription

Claude Pro/Max and ChatGPT Plus/Pro subscriptions do not include API use. A key is created
separately, and its use is billed by usage:

- Claude: [platform.claude.com](https://platform.claude.com/)
- OpenAI: [platform.openai.com](https://platform.openai.com/)

Enter the key in ARK's settings under AI.

### Signing in with a ChatGPT subscription

Settings → AI → **Sign in with a ChatGPT subscription** opens OpenAI's
[Sign in with ChatGPT](https://developers.openai.com/siwc) in the browser. Approve the requested
permissions and return to ARK. After that:

- ✨ AI translation uses your ChatGPT Plus/Pro quota, and no OpenAI API key is needed for it. When
  ChatGPT is the selected AI service, the sign-in takes priority over a key.
- The model is chosen from the same row as before; the list comes from OpenAI's models for your
  subscription, and the default is the first on the list.
- The sign-in is specific to the device and is not included in backups. Signing out from the same
  row removes the sign-in from the device and also revokes the permission at OpenAI when there is a
  connection; without one, you can remove the permission in ChatGPT's settings.
- Dictation is not covered by the sign-in: OpenAI dictation still requires an API key.

If the quota runs out, ✨ tells you; the quota status is shown in ChatGPT's settings. **You cannot
sign in with a Claude subscription:** Anthropic does not allow it for other apps
([terms](https://code.claude.com/docs/en/legal-and-compliance), as of 10/2026).

AI and manually corrected translations are remembered, so you do not pay twice for the same text.

## Privacy

- Learning and suggestions are local. The app has no analytics and no ads.
- The text you type leaves the device only when you press ✨ yourself.
- In OpenAI dictation, speech is streamed to OpenAI only while dictation is running (five minutes
  at most at a time). Your most used learned words are also sent as recognition hints, so that your
  own names and terms are recognised; the vocabulary itself stays on the device.
- The device's own speech recognition works primarily on the device. If there is no on-device model
  for your language, the phone's recognition service may use the network.
- The network is also used for the one-time download of translation models (~30 MB per language) and
  for fetching the AI model list.
- API keys are stored on the device, encrypted with Android Keystore.
- Nothing is learned in password and incognito fields, not even emoji history.

## Building from source

```
./gradlew :app:assembleDebug
```

Requirements: JDK 17+ and the Android SDK (compileSdk 36).
