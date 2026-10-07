# Vapez

## Export Gemini chats to Markdown

`gemini_to_markdown.py` converts a Google Takeout export of your Gemini history into one Markdown file. It uses only the Python standard library.

1. Go to [takeout.google.com](https://takeout.google.com), click **Deselect all**, and select **My Activity**.
2. Under **My Activity**, click **All activity data included**, deselect all, and tick only **Gemini Apps**. Format can be JSON or HTML.
3. Create the export and download the `.zip` when Google emails you.
4. Run:

```bash
python3 gemini_to_markdown.py takeout-XXXX.zip -o gemini_chats.md
```

The source can be the `.zip`, the extracted folder, or the `MyActivity.json` / `MyActivity.html` file directly. Prompts are sorted by time and grouped by day.
